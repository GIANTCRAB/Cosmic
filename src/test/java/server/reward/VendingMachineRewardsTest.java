/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.reward;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VendingMachineRewardsTest {
    private static final int[] NO_TICKETS = {0, 0, 0, 0, 0, 0};

    @Test
    void tierCount_andLabels_exposeTheSixTiers() {
        assertEquals(6, VendingMachineRewards.tierCount());
        assertEquals("Tier 1", VendingMachineRewards.label(0));
        assertEquals("Tier 6", VendingMachineRewards.label(5));
    }

    @Test
    void ticketMultiplier_rewardsRarerEraserTypes() {
        assertEquals(1, VendingMachineRewards.ticketMultiplier(0));
        assertEquals(3, VendingMachineRewards.ticketMultiplier(1));
        assertEquals(1, VendingMachineRewards.ticketMultiplier(2));
        assertEquals(3, VendingMachineRewards.ticketMultiplier(3));
        assertEquals(1, VendingMachineRewards.ticketMultiplier(4));
        assertEquals(1, VendingMachineRewards.ticketMultiplier(5));
    }

    @Test
    void pointsFor_appliesBasePlusQuantityBonusAndCoins() {
        assertEquals(0, VendingMachineRewards.pointsFor(NO_TICKETS, 0));
        assertEquals(6, VendingMachineRewards.pointsFor(new int[]{1, 0, 0, 0, 0, 0}, 0));
        assertEquals(7, VendingMachineRewards.pointsFor(new int[]{2, 0, 0, 0, 0, 0}, 0));
        assertEquals(9, VendingMachineRewards.pointsFor(new int[]{0, 2, 0, 0, 0, 0}, 0));
        assertEquals(36, VendingMachineRewards.pointsFor(new int[]{1, 1, 1, 1, 1, 1}, 0));
        assertEquals(46, VendingMachineRewards.pointsFor(NO_TICKETS, 100));
    }

    @Test
    void tierFor_mapsPointBandsToTiers() {
        assertEquals(-1, VendingMachineRewards.tierFor(NO_TICKETS, 0));
        assertEquals(0, VendingMachineRewards.tierFor(new int[]{1, 0, 0, 0, 0, 0}, 0));
        assertEquals(1, VendingMachineRewards.tierFor(new int[]{9, 0, 0, 0, 0, 0}, 0));
        assertEquals(2, VendingMachineRewards.tierFor(new int[]{20, 0, 0, 0, 0, 0}, 0));
        assertEquals(5, VendingMachineRewards.tierFor(NO_TICKETS, 100));
    }

    @Test
    void pickIndex_landsInBucketsSizedByWeight() {
        int[] weights = {1, 8, 3};
        int[] counts = new int[3];
        for (int drawn = 0; drawn < 12; drawn++) {
            counts[VendingMachineRewards.pickIndex(weights, drawn)]++;
        }
        assertEquals(1, counts[0]);
        assertEquals(8, counts[1]);
        assertEquals(3, counts[2]);
    }

    @Test
    void pickIndex_handlesBoundaries() {
        int[] weights = {1, 8, 3};
        assertEquals(0, VendingMachineRewards.pickIndex(weights, 0));
        assertEquals(1, VendingMachineRewards.pickIndex(weights, 1));
        assertEquals(1, VendingMachineRewards.pickIndex(weights, 8));
        assertEquals(2, VendingMachineRewards.pickIndex(weights, 9));
        assertEquals(2, VendingMachineRewards.pickIndex(weights, 11));
    }

    @Test
    void rollIndex_distributionMatchesWeights() {
        int[] weights = {1, 8, 3};
        int total = 12;
        RandomGenerator rng = new Random(2026);
        int[] counts = new int[3];
        for (int i = 0; i < 60_000; i++) {
            counts[VendingMachineRewards.pickIndex(weights, rng.nextInt(total))]++;
        }
        int s = counts[0] + counts[1] + counts[2];
        assertEquals(0.08, (double) counts[0] / s, 0.01);
        assertEquals(0.67, (double) counts[1] / s, 0.01);
        assertEquals(0.25, (double) counts[2] / s, 0.01);
    }

    @Test
    void rollIndex_alwaysReturnsValidIndexWithinTier() {
        RandomGenerator rng = new Random(42);
        for (int tier = 0; tier < VendingMachineRewards.tierCount(); tier++) {
            int n = VendingMachineRewards.entries(tier).size();
            for (int i = 0; i < 5000; i++) {
                int idx = VendingMachineRewards.rollIndex(tier, rng);
                assertTrue(idx >= 0 && idx < n);
            }
        }
    }

    @Test
    void roll_returnsAnEntryThatBelongsToTheTier() {
        for (int tier = 0; tier < VendingMachineRewards.tierCount(); tier++) {
            List<VendingMachineRewards.RewardEntry> entries = VendingMachineRewards.entries(tier);
            for (int i = 0; i < 100; i++) {
                assertTrue(entries.contains(VendingMachineRewards.roll(tier)));
            }
        }
    }

    @Test
    void entries_havePositiveWeightAndQuantityAndAreNonEmpty() {
        for (int tier = 0; tier < VendingMachineRewards.tierCount(); tier++) {
            List<VendingMachineRewards.RewardEntry> entries = VendingMachineRewards.entries(tier);
            assertTrue(entries.size() > 0, "empty tier " + tier);
            for (VendingMachineRewards.RewardEntry e : entries) {
                assertTrue(e.weight() > 0, "non-positive weight at tier " + tier);
                assertTrue(e.quantity() > 0, "non-positive quantity at tier " + tier);
            }
        }
    }

    @Test
    void weights_makeEquipsRarerThanCommonDrops() {
        List<VendingMachineRewards.RewardEntry> tier2 = VendingMachineRewards.entries(2);
        assertEquals(1, tier2.get(0).weight());    // lv3 index 0: equip
        assertEquals(8, tier2.get(5).weight());    // lv3 index 5: potion stack
        assertTrue(tier2.get(0).weight() < tier2.get(5).weight());
    }
}
