/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.inventory;

import org.junit.jupiter.api.Test;
import tools.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipStatUpgradeSelectionTest {

    private static List<Equip.StatUpgrade> allUpgradableStats() {
        List<Equip.StatUpgrade> stats = new ArrayList<>();
        Collections.addAll(stats,
                Equip.StatUpgrade.incSTR,
                Equip.StatUpgrade.incDEX,
                Equip.StatUpgrade.incINT,
                Equip.StatUpgrade.incLUK,
                Equip.StatUpgrade.incMHP,
                Equip.StatUpgrade.incMMP,
                Equip.StatUpgrade.incPAD,
                Equip.StatUpgrade.incMAD,
                Equip.StatUpgrade.incPDD,
                Equip.StatUpgrade.incMDD,
                Equip.StatUpgrade.incEVA,
                Equip.StatUpgrade.incACC,
                Equip.StatUpgrade.incSpeed,
                Equip.StatUpgrade.incJump);
        return stats;
    }

    @Test
    void emptyCandidatesReturnsEmptyForPowerMode() {
        assertTrue(Equip.selectStatsToUpgrade(new ArrayList<>(), true).isEmpty());
    }

    @Test
    void emptyCandidatesReturnsEmptyForSingleStatMode() {
        assertTrue(Equip.selectStatsToUpgrade(new ArrayList<>(), false).isEmpty());
    }

    @Test
    void powerModeUpgradesAllCandidates() {
        List<Equip.StatUpgrade> candidates = allUpgradableStats();

        List<Equip.StatUpgrade> selected = Equip.selectStatsToUpgrade(candidates, true);

        assertEquals(candidates.size(), selected.size());
        assertEquals(candidates, selected);
    }

    @Test
    void powerModeReturnsCopyNotSameInstance() {
        List<Equip.StatUpgrade> candidates = allUpgradableStats();

        List<Equip.StatUpgrade> selected = Equip.selectStatsToUpgrade(candidates, true);

        assertNotEquals(System.identityHashCode(candidates), System.identityHashCode(selected));
    }

    @Test
    void singleStatModePicksExactlyOneCandidate() {
        List<Equip.StatUpgrade> candidates = allUpgradableStats();

        List<Equip.StatUpgrade> selected = Equip.selectStatsToUpgrade(candidates, false);

        assertEquals(1, selected.size());
        assertTrue(candidates.contains(selected.get(0)));
    }

    @Test
    void singleStatModeWithOneCandidatePicksIt() {
        Equip.StatUpgrade only = Equip.StatUpgrade.incPAD;
        List<Equip.StatUpgrade> candidates = new ArrayList<>(List.of(only));

        List<Equip.StatUpgrade> selected = Equip.selectStatsToUpgrade(candidates, false);

        assertEquals(1, selected.size());
        assertEquals(only, selected.get(0));
    }

    @Test
    void singleStatModeEventuallyPicksEveryCandidate() {
        // Uniform random selection: over many rolls every candidate must appear at least once.
        List<Equip.StatUpgrade> candidates = allUpgradableStats();
        Map<Equip.StatUpgrade, Integer> hits = new HashMap<>();
        for (Equip.StatUpgrade s : candidates) {
            hits.put(s, 0);
        }

        final int rounds = 20_000;
        for (int i = 0; i < rounds; i++) {
            Equip.StatUpgrade picked = Equip.selectStatsToUpgrade(candidates, false).get(0);
            hits.merge(picked, 1, Integer::sum);
        }

        for (Equip.StatUpgrade s : candidates) {
            assertTrue(hits.get(s) > 0, "Stat " + s + " was never selected");
        }
    }

    @Test
    void watkPositiveIncludesIncPAD() {
        List<Equip.StatUpgrade> candidates = Equip.buildUpgradeCandidates(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 5, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0);

        assertEquals(List.of(Equip.StatUpgrade.incPAD), candidates);
    }

    @Test
    void watkZeroExcludesIncPAD() {
        List<Equip.StatUpgrade> candidates = Equip.buildUpgradeCandidates(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0);

        assertTrue(candidates.isEmpty());
        assertFalse(candidates.contains(Equip.StatUpgrade.incPAD));
    }

    @Test
    void weaponLikeStatsIncludeIncPAD() {
        List<Equip.StatUpgrade> candidates = Equip.buildUpgradeCandidates(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 10, (short) 0,
                (short) 0, (short) 0, (short) 5, (short) 3,
                (short) 0, (short) 0);

        assertTrue(candidates.contains(Equip.StatUpgrade.incPAD));
        assertEquals(List.of(Equip.StatUpgrade.incPAD, Equip.StatUpgrade.incEVA, Equip.StatUpgrade.incACC), candidates);
    }

    @Test
    void singleStatModeDistributionIsRoughlyUniform() {
        // With uniform selection across 14 candidates, each should land near rounds/14.
        // Allow a generous +/-40% band around the expected mean to avoid flakiness.
        List<Equip.StatUpgrade> candidates = allUpgradableStats();
        Map<Equip.StatUpgrade, Integer> hits = new HashMap<>();
        for (Equip.StatUpgrade s : candidates) {
            hits.put(s, 0);
        }

        final int rounds = 56_000; // evenly divisible by 14
        for (int i = 0; i < rounds; i++) {
            Equip.StatUpgrade picked = Equip.selectStatsToUpgrade(candidates, false).get(0);
            hits.merge(picked, 1, Integer::sum);
        }

        int expected = rounds / candidates.size();
        int lowerBound = (int) (expected * 0.6);
        int upperBound = (int) (expected * 1.4);
        for (Equip.StatUpgrade s : candidates) {
            int count = hits.get(s);
            assertTrue(count >= lowerBound && count <= upperBound,
                    "Stat " + s + " count " + count + " outside expected band [" + lowerBound + ", " + upperBound + "]");
        }
    }

    @Test
    void attemptSlotUpgradesDisabledNeverAddsAnything() {
        // pickUpgradeSlot == false models a non-scrollable equip (e.g. a Medal, tuc == 0):
        // regardless of vicious, no upgrade-slot/vicious gain must ever be appended.
        for (int i = 0; i < 500; i++) {
            List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();
            Equip.attemptSlotUpgrades(stats, false, (short) 7);
            assertTrue(stats.isEmpty(), "Slot upgrade was added while pickUpgradeSlot is disabled");
        }
    }

    @Test
    void attemptSlotUpgradesDisabledIgnoresEvenIncSlotOnly() {
        // Explicitly assert incSlot/incVicious can never appear when disabled.
        for (int i = 0; i < 500; i++) {
            List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();
            Equip.attemptSlotUpgrades(stats, false, (short) 0);
            assertFalse(stats.contains(new Pair<>(Equip.StatUpgrade.incSlot, 1)));
            assertFalse(stats.contains(new Pair<>(Equip.StatUpgrade.incVicious, 1)));
        }
    }

    @Test
    void attemptSlotUpgradesEnabledCanStillAddSlots() {
        // Sanity: the enabled path (scrollable equip) must still be able to grant a slot,
        // so the fix does not silently disable slot gains for regular gear.
        boolean addedSlot = false;
        for (int i = 0; i < 5000; i++) {
            List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();
            Equip.attemptSlotUpgrades(stats, true, (short) 0);
            if (!stats.isEmpty()) {
                assertEquals(Equip.StatUpgrade.incSlot, stats.get(0).getLeft());
                addedSlot = true;
                break;
            }
        }
        assertTrue(addedSlot, "Enabled slot upgrade never fired over many attempts");
    }

    @Test
    void ensureStatGainedIsNoOpWhenStatAlreadyPresent() {
        // When the first roll already produced a stat, the supplier must NOT be called
        // (no retry needed).
        List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();
        stats.add(new Pair<>(Equip.StatUpgrade.incLUK, 1));

        int[] calls = {0};
        Equip.ensureStatGained(stats, s -> calls[0]++);

        assertEquals(0, calls[0], "Supplier was called even though a stat was already present");
        assertEquals(1, stats.size());
    }

    @Test
    void ensureStatGainedRetriesUntilStatProduced() {
        // Models a stat roll that yields 0 a few times before succeeding: the helper
        // must keep retrying until at least one stat is present.
        List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();

        int[] calls = {0};
        Equip.ensureStatGained(stats, s -> {
            calls[0]++;
            if (calls[0] >= 4) {
                s.add(new Pair<>(Equip.StatUpgrade.incLUK, 1));
            }
        });

        assertEquals(4, calls[0], "Helper stopped retrying before a stat was produced");
        assertEquals(1, stats.size());
        assertEquals(Equip.StatUpgrade.incLUK, stats.get(0).getLeft());
    }

    @Test
    void ensureStatGainedNeverYieldsEmptyForProductiveSupplier() {
        // Over many runs with a supplier that sometimes produces 0, the result must
        // always end up non-empty (the always-gain-a-stat guarantee).
        for (int i = 0; i < 1000; i++) {
            List<Pair<Equip.StatUpgrade, Integer>> stats = new LinkedList<>();
            int[] calls = {0};
            Equip.ensureStatGained(stats, s -> {
                calls[0]++;
                if (calls[0] % 3 == 0) {   // produces a stat only every 3rd call
                    s.add(new Pair<>(Equip.StatUpgrade.incSTR, 1));
                }
            });
            assertFalse(stats.isEmpty(), "ensureStatGained yielded an empty stat list");
        }
    }
}
