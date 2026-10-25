/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.services;

import net.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.TimerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Drives the real monitor callback with a fake server clock. No timer executor is started.
 */
class BaseSchedulerTest {
    private MockedStatic<Server> serverScope;
    private MockedStatic<TimerManager> timerScope;
    private TestScheduler scheduler;
    private Runnable monitorRunnable;
    private long currentTime;
    private final List<Removal> removals = new ArrayList<>();

    @BeforeEach
    void setUp() {
        currentTime = 1_000L;
        serverScope = mockStatic(Server.class);
        timerScope = mockStatic(TimerManager.class);

        Server server = mock(Server.class);
        serverScope.when(Server::getInstance).thenReturn(server);
        when(server.getCurrentTime()).thenAnswer(invocation -> currentTime);

        TimerManager timerManager = mock(TimerManager.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        timerScope.when(TimerManager::getInstance).thenReturn(timerManager);
        doAnswer(invocation -> {
            monitorRunnable = invocation.getArgument(0);
            return scheduledFuture;
        }).when(timerManager).register(any(Runnable.class), anyLong(), anyLong());

        scheduler = new TestScheduler();
        // Record individual removals, without depending on empty-batch notifications or key order.
        scheduler.listen((entries, fromUpdate) -> entries.forEach(key -> removals.add(new Removal(key, fromUpdate))));
    }

    @AfterEach
    void tearDown() {
        try (MockedStatic<Server> server = serverScope;
             MockedStatic<TimerManager> timer = timerScope) {
            if (scheduler != null) {
                scheduler.dispose();
            }
        }
    }

    @Test
    void replacementRegisteredByExecutingActionSurvivesAndRunsOnceOnNextTick() {
        List<String> executions = new ArrayList<>();
        scheduler.register("entry", () -> {
            executions.add("original");
            scheduler.register("entry", () -> executions.add("replacement"), 0);
        }, 0);

        tickAt(1_001L);
        assertEquals(List.of("original"), executions, "A new registration must wait for another snapshot");
        List<Removal> firstTickRemovals = List.copyOf(removals);

        tickAt(1_002L);
        tickAt(1_003L);

        assertAll(
                () -> assertEquals(List.of("original", "replacement"), executions),
                () -> assertTrue(firstTickRemovals.isEmpty(), "The replacement's key is still registered"),
                () -> assertEquals(List.of(new Removal("entry", true)), removals)
        );
    }

    @Test
    void replacingAnotherDueEntryPreservesBothNewestRegistrationsRegardlessOfIterationOrder() {
        List<String> executions = new ArrayList<>();
        // Each original replaces the other key. Both replacements must survive whichever action runs first.
        scheduler.register("first", () -> {
            executions.add("first-original");
            scheduler.register("second", () -> executions.add("second-replacement"), 0);
        }, 0);
        scheduler.register("second", () -> {
            executions.add("second-original");
            scheduler.register("first", () -> executions.add("first-replacement"), 0);
        }, 0);

        tickAt(1_001L);
        assertEquals(2, executions.size());
        assertEquals(Set.of("first-original", "second-original"), Set.copyOf(executions));
        List<Removal> firstTickRemovals = List.copyOf(removals);

        tickAt(1_002L);
        tickAt(1_003L);

        assertAll(
                () -> assertEquals(4, executions.size(), "Both replacement actions must run exactly once"),
                () -> assertEquals(Set.of("first-original", "second-original", "first-replacement", "second-replacement"),
                        Set.copyOf(executions)),
                () -> assertTrue(firstTickRemovals.isEmpty(), "Neither replacement was removed by the first tick"),
                () -> assertEquals(2, removals.size()),
                () -> assertEquals(Set.of(new Removal("first", true), new Removal("second", true)), Set.copyOf(removals))
        );
    }

    @Test
    void replacementWithIdenticalRunnableAndDeadlineIsStillANewRegistration() {
        List<String> executions = new ArrayList<>();
        Runnable action = new Runnable() {
            @Override
            public void run() {
                executions.add("run");
                if (executions.size() == 1) {
                    scheduler.register("entry", this, -1);
                }
            }
        };
        // Keep the clock fixed and the deadline already due. Both Pairs contain this action and time 999,
        // so an equals-based conditional remove would incorrectly delete the new registration too.
        scheduler.register("entry", action, -1);

        tickAt(1_000L);
        assertEquals(List.of("run"), executions);
        List<Removal> firstTickRemovals = List.copyOf(removals);

        tickAt(1_000L);
        tickAt(1_000L);

        assertAll(
                () -> assertEquals(List.of("run", "run"), executions),
                () -> assertTrue(firstTickRemovals.isEmpty(), "Equal values do not identify the processed registration"),
                () -> assertEquals(List.of(new Removal("entry", true)), removals)
        );
    }

    @Test
    void ordinaryDueEntriesRunOnceAndNotifyRemovalOnce() {
        List<String> executions = new ArrayList<>();
        scheduler.register("first", () -> executions.add("first"), 0);
        scheduler.register("second", () -> executions.add("second"), 0);

        tickAt(1_001L);
        tickAt(1_002L);

        assertAll(
                () -> assertEquals(2, executions.size()),
                () -> assertEquals(Set.of("first", "second"), Set.copyOf(executions)),
                () -> assertEquals(2, removals.size()),
                () -> assertEquals(Set.of(new Removal("first", true), new Removal("second", true)), Set.copyOf(removals))
        );
    }

    @Test
    void futureEntryWaitsUntilClockIsStrictlyPastDeadline() {
        List<String> executions = new ArrayList<>();
        scheduler.register("future", () -> executions.add("run"), 10);

        tickAt(1_000L);
        tickAt(1_009L);
        tickAt(1_010L);
        assertTrue(executions.isEmpty(), "The deadline comparison must be strict '<', not '<='");
        assertTrue(removals.isEmpty(), "Waiting entries have not been removed");

        tickAt(1_011L);
        tickAt(1_012L);

        assertEquals(List.of("run"), executions);
        assertEquals(List.of(new Removal("future", true)), removals);
    }

    @Test
    void listenersOnlyReceiveKeysActuallyRemovedWhenAnotherKeyIsReplaced() {
        List<String> executions = new ArrayList<>();
        scheduler.register("replaced", () -> {
            executions.add("original");
            scheduler.register("replaced", () -> executions.add("replacement"), 10);
        }, 0);
        scheduler.register("finished", () -> executions.add("finished"), 0);

        tickAt(1_001L);

        assertEquals(2, executions.size());
        assertEquals(Set.of("original", "finished"), Set.copyOf(executions));
        assertEquals(List.of(new Removal("finished", true)), removals,
                "Cleanup must not announce a removal for a key whose replacement remains registered");
    }

    @Test
    void failingActionDoesNotDropItsReplacement() {
        List<String> executions = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("scheduled action failed after registering its replacement");
        scheduler.register("entry", () -> {
            executions.add("original");
            scheduler.register("entry", () -> executions.add("replacement"), 0);
            throw failure;
        }, 0);

        assertSame(failure, assertThrows(IllegalStateException.class, () -> tickAt(1_001L)));
        assertTrue(removals.isEmpty(), "A failed action has not removed its replacement");

        tickAt(1_002L);
        tickAt(1_003L);

        assertEquals(List.of("original", "replacement"), executions);
        assertEquals(List.of(new Removal("entry", true)), removals);
    }

    private void tickAt(long time) {
        currentTime = time;
        assertNotNull(monitorRunnable, "Registering an entry should install the monitor callback");
        monitorRunnable.run();
    }

    private record Removal(Object key, boolean fromUpdate) {
    }

    private static final class TestScheduler extends BaseScheduler {
        void register(Object key, Runnable action, long duration) {
            registerEntry(key, action, duration);
        }

        void listen(SchedulerListener listener) {
            addListener(listener);
        }
    }
}
