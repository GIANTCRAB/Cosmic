/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.coordinator.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-function unit tests for {@link MonsterAggroOps}. No mocking required:
 * every helper is side-effect free and deterministic.
 */
class MonsterAggroOpsTest {

    private static final long MELEE_SQ = 400000L;
    private static final long MAX_SQ = 722500L;
    private static final double HI = 2.0;
    private static final double LO = 0.1;
    private static final long INTERVAL = 5000L;

    // ----- distanceMultiplier -----

    @Test
    void distanceMultiplier_atOrInsideMelee_returnsHigh() {
        assertEquals(HI, MonsterAggroOps.distanceMultiplier(0, MELEE_SQ, MAX_SQ, HI, LO));
        assertEquals(HI, MonsterAggroOps.distanceMultiplier(MELEE_SQ, MELEE_SQ, MAX_SQ, HI, LO),
                "Exactly at melee range should still earn the full multiplier");
    }

    @Test
    void distanceMultiplier_atOrBeyondMax_returnsLow() {
        assertEquals(LO, MonsterAggroOps.distanceMultiplier(MAX_SQ, MELEE_SQ, MAX_SQ, HI, LO));
        assertEquals(LO, MonsterAggroOps.distanceMultiplier(MAX_SQ * 4, MELEE_SQ, MAX_SQ, HI, LO),
                "Beyond max range is clamped to the minimum multiplier");
    }

    @Test
    void distanceMultiplier_interpolatesLinearlyAtMidpoint() {
        long mid = (MELEE_SQ + MAX_SQ) / 2;
        double expected = HI + (LO - HI) * 0.5; // 1.05
        assertEquals(expected, MonsterAggroOps.distanceMultiplier(mid, MELEE_SQ, MAX_SQ, HI, LO), 1e-9);
    }

    @Test
    void distanceMultiplier_isMonotonicDecreasing() {
        double prev = Double.POSITIVE_INFINITY;
        for (long d = MELEE_SQ; d <= MAX_SQ; d += 20000) {
            double m = MonsterAggroOps.distanceMultiplier(d, MELEE_SQ, MAX_SQ, HI, LO);
            assertTrue(m <= prev + 1e-9, "Multiplier should not increase with distance (d=" + d + ")");
            prev = m;
        }
    }

    @Test
    void distanceMultiplier_degenerateBandReturnsLow() {
        assertEquals(LO, MonsterAggroOps.distanceMultiplier(0, MAX_SQ, MELEE_SQ, HI, LO),
                "When max <= melee the band is degenerate; floor to low multiplier");
    }

    @Test
    void isWithinMelee_boundaryInclusive() {
        assertTrue(MonsterAggroOps.isWithinMelee(0, MELEE_SQ));
        assertTrue(MonsterAggroOps.isWithinMelee(MELEE_SQ, MELEE_SQ));
        assertFalse(MonsterAggroOps.isWithinMelee(MELEE_SQ + 1, MELEE_SQ));
    }

    // ----- recordDamage -----

    @Test
    void recordDamage_accumulatesAndAverages() {
        MonsterAggroOps.AggroEntry e = MonsterAggroOps.AggroEntry.create(1);

        e = MonsterAggroOps.recordDamage(e, 100, INTERVAL);
        assertEquals(100, e.accumulatedDamage());
        assertEquals(100, e.averageDamage());
        assertEquals(1, e.currentDamageInstances());

        e = MonsterAggroOps.recordDamage(e, 300, INTERVAL);
        assertEquals(400, e.accumulatedDamage());
        assertEquals(200, e.averageDamage());
        assertEquals(2, e.currentDamageInstances());
    }

    @Test
    void recordDamage_isImmutableAndResetsDecayStreaks() {
        MonsterAggroOps.AggroEntry original = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);

        MonsterAggroOps.AggroEntry updated = MonsterAggroOps.recordDamage(original, 50, INTERVAL);

        assertNotSame(original, updated);
        assertEquals(1, original.currentDamageInstances(), "Original entry must be untouched");
        assertEquals(2, updated.currentDamageInstances());
        assertEquals(0, updated.expireStreak());
        assertEquals(0, updated.updateStreak());
    }

    @Test
    void recordDamage_firstHitHasFullDecayWindow() {
        MonsterAggroOps.AggroEntry e = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);

        // 120000ms / 5000ms = 24 ticks, undivided on the first instance
        assertEquals(24, e.toNextUpdate());
    }

    // ----- tickExpiry -----

    @Test
    void tickExpiry_doesNotExpireBeforeWindowElapses() {
        MonsterAggroOps.AggroEntry e = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);

        for (int i = 0; i < 23; i++) {
            MonsterAggroOps.TickResult res = MonsterAggroOps.tickExpiry(e, 1, INTERVAL);
            assertFalse(res.expired());
            assertEquals(100, res.entry().accumulatedDamage(), "Aggro must be intact until the window elapses");
            e = res.entry();
        }
    }

    @Test
    void tickExpiry_expiresOnWindowElapseWithNoFurtherDamage() {
        MonsterAggroOps.AggroEntry e = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);

        for (int i = 0; i < 23; i++) {
            e = MonsterAggroOps.tickExpiry(e, 1, INTERVAL).entry();
        }

        MonsterAggroOps.TickResult res = MonsterAggroOps.tickExpiry(e, 1, INTERVAL);
        assertTrue(res.expired(), "The single damage instance should expire once its window elapses");
    }

    @Test
    void tickExpiry_advancesUpdateStreakEachTick() {
        MonsterAggroOps.AggroEntry e = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);

        e = MonsterAggroOps.tickExpiry(e, 1, INTERVAL).entry();
        assertEquals(1, e.updateStreak());

        e = MonsterAggroOps.tickExpiry(e, 1, INTERVAL).entry();
        assertEquals(2, e.updateStreak());
    }

    // ----- list helpers -----

    @Test
    void replaceOrAppend_replacesInPlaceOrAppends() {
        List<MonsterAggroOps.AggroEntry> list = new ArrayList<>();
        MonsterAggroOps.AggroEntry a = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL);
        MonsterAggroOps.AggroEntry b = MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(2), 50, INTERVAL);
        MonsterAggroOps.replaceOrAppend(list, a);
        MonsterAggroOps.replaceOrAppend(list, b);
        assertEquals(2, list.size());

        MonsterAggroOps.AggroEntry aUpdated = MonsterAggroOps.recordDamage(a, 500, INTERVAL);
        MonsterAggroOps.replaceOrAppend(list, aUpdated);

        assertEquals(2, list.size(), "Updating an existing cid must not grow the list");
        assertSame(aUpdated, list.get(0));
        assertEquals(600, list.get(0).accumulatedDamage());
    }

    @Test
    void removeByCid_removesMatchingEntry() {
        List<MonsterAggroOps.AggroEntry> list = new ArrayList<>();
        MonsterAggroOps.replaceOrAppend(list, MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 100, INTERVAL));
        MonsterAggroOps.replaceOrAppend(list, MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(2), 50, INTERVAL));

        assertTrue(MonsterAggroOps.removeByCid(list, 1));
        assertEquals(1, list.size());
        assertEquals(2, list.get(0).cid());
        assertFalse(MonsterAggroOps.removeByCid(list, 999));
    }

    @Test
    void sortByAccumulatedDamageDesc_ordersByDamageThenPreservesInsertionOrder() {
        List<MonsterAggroOps.AggroEntry> list = new ArrayList<>();
        MonsterAggroOps.replaceOrAppend(list, MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(1), 50, INTERVAL));  // lowest
        MonsterAggroOps.replaceOrAppend(list, MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(2), 300, INTERVAL)); // highest
        MonsterAggroOps.replaceOrAppend(list, MonsterAggroOps.recordDamage(MonsterAggroOps.AggroEntry.create(3), 300, INTERVAL)); // tied with 2, inserted later

        MonsterAggroOps.sortByAccumulatedDamageDesc(list);

        assertEquals(2, list.get(0).cid());
        assertEquals(3, list.get(1).cid(), "Tied entries should retain insertion order (stable sort)");
        assertEquals(1, list.get(2).cid());
    }
}
