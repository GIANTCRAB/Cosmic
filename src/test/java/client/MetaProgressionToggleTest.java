/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Isolate the static registry from other test classes that may touch it when run concurrently.
@Execution(ExecutionMode.SAME_THREAD)
class MetaProgressionToggleTest {

    private static final int CHARACTER_ID = 424242;

    @BeforeEach
    void resetRegistry() {
        MetaProgressionToggle.clearAll();
    }

    @Test
    void defaultsToEnabled() {
        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID));
    }

    @Test
    void forCharacterReturnsSameReferenceAcrossCalls() {
        AtomicBoolean first = MetaProgressionToggle.forCharacter(CHARACTER_ID);
        AtomicBoolean second = MetaProgressionToggle.forCharacter(CHARACTER_ID);

        assertSame(first, second, "forCharacter must be idempotent for a given character id");
    }

    @Test
    void toggleFlipsStateAndReturnsNewValue() {
        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID));

        boolean afterFirstToggle = MetaProgressionToggle.toggle(CHARACTER_ID);
        assertFalse(afterFirstToggle);
        assertFalse(MetaProgressionToggle.isEnabled(CHARACTER_ID));

        boolean afterSecondToggle = MetaProgressionToggle.toggle(CHARACTER_ID);
        assertTrue(afterSecondToggle);
        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID));
    }

    @Test
    void differentCharactersAreIndependent() {
        int other = CHARACTER_ID + 1;

        MetaProgressionToggle.toggle(CHARACTER_ID); // disable only the first

        assertFalse(MetaProgressionToggle.isEnabled(CHARACTER_ID));
        assertTrue(MetaProgressionToggle.isEnabled(other));
        assertNotSame(MetaProgressionToggle.forCharacter(CHARACTER_ID),
                MetaProgressionToggle.forCharacter(other));
    }

    /**
     * The core requirement: a channel change destroys the Character object and rebuilds it,
     * re-fetching its toggle from the registry. A toggle set on the "old" instance must still
     * be in effect for the "new" instance because they share the same registry entry.
     */
    @Test
    void stateSurvivesSimulatedCharacterReload() {
        // "old" character instance disables the buff
        AtomicBoolean oldRef = MetaProgressionToggle.forCharacter(CHARACTER_ID);
        oldRef.set(false);

        // channel change: a brand-new character instance re-binds from the registry
        AtomicBoolean newRef = MetaProgressionToggle.forCharacter(CHARACTER_ID);

        assertSame(oldRef, newRef, "reload must re-bind to the same shared AtomicBoolean");
        assertFalse(MetaProgressionToggle.isEnabled(CHARACTER_ID),
                "toggle must persist across the simulated reload");
    }

    @Test
    void clearResetsCharacterToDefault() {
        MetaProgressionToggle.toggle(CHARACTER_ID);
        assertFalse(MetaProgressionToggle.isEnabled(CHARACTER_ID));

        MetaProgressionToggle.clear(CHARACTER_ID);

        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID),
                "after clear the toggle must return to its enabled default");
    }

    /**
     * AtomicBoolean must not lose updates under contention. Starting from {@code true} and
     * applying {@code threads * flips} toggles deterministically ends at {@code true} when the
     * total count is even, proving no toggles were dropped.
     */
    @Test
    void concurrentTogglesDoNotLoseUpdates() throws InterruptedException {
        final int threads = 16;
        final int flipsPerThread = 1000;
        int totalFlips = threads * flipsPerThread;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> failures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < flipsPerThread; j++) {
                        MetaProgressionToggle.toggle(CHARACTER_ID);
                    }
                } catch (Throwable t) {
                    synchronized (failures) {
                        failures.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertTrue(finished, "worker threads did not finish in time");

        assertTrue(failures.isEmpty(), () -> "workers threw: " + failures);

        boolean expected = totalFlips % 2 == 0; // starts true; even number of flips -> true
        assertEquals(expected, MetaProgressionToggle.isEnabled(CHARACTER_ID),
                "an even number of atomic toggles must return the toggle to its initial state");
    }
}
