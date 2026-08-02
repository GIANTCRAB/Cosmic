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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // --- getMaxedStats: stats at/above MAX_EQUIPMNT_STAT must be flagged for exclusion ---

    private static final int MAX_STAT = 32767;

    @Test
    void getMaxedStatsReturnsEmptyWhenAllBelowMax() {
        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 100, (short) 100, (short) 100, (short) 100,
                (short) 100, (short) 100, (short) 100, (short) 100,
                (short) 100, (short) 100, (short) 100, (short) 100,
                (short) 100, (short) 100, MAX_STAT);

        assertTrue(maxed.isEmpty());
    }

    @Test
    void getMaxedStatsReturnsExactlyTheMaxedStats() {
        // watk and luk are maxed; everything else is well below.
        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 0, (short) 5, (short) 0, (short) MAX_STAT,
                (short) 0, (short) 0, (short) MAX_STAT, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, MAX_STAT);

        assertEquals(EnumSet.of(Equip.StatUpgrade.incLUK, Equip.StatUpgrade.incPAD), maxed);
    }

    @Test
    void getMaxedStatsBoundaryAtExactlyMaxIsMaxed() {
        // A stat sitting exactly at the cap counts as maxed (>=), so it is excluded.
        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) MAX_STAT, MAX_STAT);

        assertEquals(EnumSet.of(Equip.StatUpgrade.incJump), maxed);
    }

    @Test
    void getMaxedStatsBoundaryOneBelowMaxIsNotMaxed() {
        // maxStat - 1 is still upgradeable, so it must NOT be flagged.
        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) (MAX_STAT - 1), MAX_STAT);

        assertTrue(maxed.isEmpty());
    }

    @Test
    void getMaxedStatsFlagsEveryStatIndependently() {
        // Param order of getMaxedStats: str, dex, _int, luk, hp, mp, watk, matk,
        // wdef, mdef, acc, avoid, speed, jump. Map each stat to that positional index.
        Map<Equip.StatUpgrade, Integer> paramIndex = new HashMap<>();
        paramIndex.put(Equip.StatUpgrade.incSTR, 0);
        paramIndex.put(Equip.StatUpgrade.incDEX, 1);
        paramIndex.put(Equip.StatUpgrade.incINT, 2);
        paramIndex.put(Equip.StatUpgrade.incLUK, 3);
        paramIndex.put(Equip.StatUpgrade.incMHP, 4);
        paramIndex.put(Equip.StatUpgrade.incMMP, 5);
        paramIndex.put(Equip.StatUpgrade.incPAD, 6);
        paramIndex.put(Equip.StatUpgrade.incMAD, 7);
        paramIndex.put(Equip.StatUpgrade.incPDD, 8);
        paramIndex.put(Equip.StatUpgrade.incMDD, 9);
        paramIndex.put(Equip.StatUpgrade.incACC, 10);
        paramIndex.put(Equip.StatUpgrade.incEVA, 11);
        paramIndex.put(Equip.StatUpgrade.incSpeed, 12);
        paramIndex.put(Equip.StatUpgrade.incJump, 13);

        for (Equip.StatUpgrade s : paramIndex.keySet()) {
            short[] v = new short[14];
            v[paramIndex.get(s)] = (short) MAX_STAT;
            Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7],
                    v[8], v[9], v[10], v[11], v[12], v[13], MAX_STAT);
            assertEquals(EnumSet.of(s), maxed, "Stat " + s + " not flagged when maxed");
        }
    }

    @Test
    void maxedStatIsExcludedFromUpgradeCandidates() {
        // End-to-end: a stat that is both > 0 and >= maxStat is present in raw candidates
        // but must be removed once maxed stats are filtered out (mirrors getUpgradeCandidates).
        short watk = MAX_STAT;   // > 0 AND maxed
        List<Equip.StatUpgrade> raw = Equip.buildUpgradeCandidates(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, watk, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0);
        assertEquals(List.of(Equip.StatUpgrade.incPAD), raw);

        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, watk, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, MAX_STAT);

        List<Equip.StatUpgrade> filtered = new ArrayList<>(raw);
        filtered.removeAll(maxed);

        assertTrue(filtered.isEmpty(), "Maxed stat was not excluded from candidates");
    }

    @Test
    void selectStatsToUpgradeNeverPicksAMaxedStat() {
        // After filtering, the maxed stat must never be selected in either mode.
        short watk = MAX_STAT;
        List<Equip.StatUpgrade> raw = Equip.buildUpgradeCandidates(
                (short) 1, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, watk, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0);
        Set<Equip.StatUpgrade> maxed = Equip.getMaxedStats(
                (short) 1, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, watk, (short) 0,
                (short) 0, (short) 0, (short) 0, (short) 0,
                (short) 0, (short) 0, MAX_STAT);

        List<Equip.StatUpgrade> filtered = new ArrayList<>(raw);
        filtered.removeAll(maxed);

        // Only STR survives; WATK is maxed and gone.
        assertEquals(List.of(Equip.StatUpgrade.incSTR), filtered);
        assertEquals(List.of(Equip.StatUpgrade.incSTR), Equip.selectStatsToUpgrade(filtered, true));
        for (int i = 0; i < 5_000; i++) {
            assertFalse(Equip.selectStatsToUpgrade(filtered, false).contains(Equip.StatUpgrade.incPAD),
                    "Maxed WATK was picked in single-stat mode");
        }
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

    // --- decideSelectAllStats: special-boss rescue while USE_EQUIPMNT_LVLUP_POWER is off -----

    private static final int BAIN_SWORD = 1402062;
    private static final int ZAKUM_HELMET = 1002357;
    private static final int REGULAR_WEAPON = 1302000;
    private static final List<Integer> SPECIAL_BOSS_IDS = List.of(
            1382068, 1402062, 1442078, 1452071, 1472086, 1492037,
            1002357, 1002390, 1002430, 1122000, 1002971,
            1003023, 1003024, 1003025, 1003026);

    @Test
    void powerModeShortCircuitsToAllStats() {
        // When POWER is on, every item selects all stats regardless of the boss rescue flag.
        assertTrue(Equip.decideSelectAllStats(REGULAR_WEAPON, true, false, SPECIAL_BOSS_IDS));
        assertTrue(Equip.decideSelectAllStats(BAIN_SWORD, true, false, SPECIAL_BOSS_IDS));
        assertTrue(Equip.decideSelectAllStats(REGULAR_WEAPON, true, true, null));
    }

    @Test
    void bossRescueUpgradesAllStatsForBossItemWhenPowerOff() {
        assertTrue(Equip.decideSelectAllStats(BAIN_SWORD, false, true, SPECIAL_BOSS_IDS));
        assertTrue(Equip.decideSelectAllStats(ZAKUM_HELMET, false, true, SPECIAL_BOSS_IDS));
    }

    @Test
    void bossRescueDoesNotApplyToNonBossItemWhenPowerOff() {
        assertFalse(Equip.decideSelectAllStats(REGULAR_WEAPON, false, true, SPECIAL_BOSS_IDS));
    }

    @Test
    void bossRescueRequiresFlagEvenForBossItemWhenPowerOff() {
        // The rescue flag must be explicitly enabled; a boss drop alone is not enough.
        assertFalse(Equip.decideSelectAllStats(BAIN_SWORD, false, false, SPECIAL_BOSS_IDS));
    }

    @Test
    void bossRescueIsNullSafeWhenBossListMissing() {
        assertFalse(Equip.decideSelectAllStats(BAIN_SWORD, false, true, null));
    }

    @Test
    void bossRescueWorksWithEverySpecialBossDropId() {
        // Sanity: every id enumerated in config.yaml must be recognised as a boss drop.
        for (int itemId : SPECIAL_BOSS_IDS) {
            assertTrue(Equip.decideSelectAllStats(itemId, false, true, SPECIAL_BOSS_IDS),
                    "Item " + itemId + " should be a special boss drop");
        }
    }
}
