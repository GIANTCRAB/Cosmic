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

import tools.Randomizer;

import java.util.List;
import java.util.random.RandomGenerator;

public class VendingMachineRewards {
    public static final int NO_TIER = -1;

    public record RewardEntry(int itemId, int quantity, int weight) {}

    public record RewardTier(String label, List<RewardEntry> entries) {
        public int size() {
            return entries.size();
        }
    }

    private static final List<RewardTier> TIERS = List.of(
            new RewardTier("Tier 1", List.of(
                    e(1302021, 1, 1), e(1302024, 1, 1), e(1302033, 1, 1), e(1082150, 1, 1), e(1002419, 1, 1),
                    e(2022053, 20, 8), e(2022054, 20, 8), e(2020032, 20, 8), e(2022057, 20, 8), e(2022096, 20, 8),
                    e(2022097, 25, 8), e(2022192, 25, 8), e(2020030, 25, 8), e(2010005, 50, 8), e(2022041, 50, 8),
                    e(2030000, 12, 8), e(2040100, 1, 3), e(2040004, 1, 3), e(2040207, 1, 3), e(2048004, 1, 3),
                    e(4031203, 3, 8), e(4000021, 4, 8), e(4003005, 2, 8), e(4003000, 2, 8), e(4003001, 1, 8),
                    e(4010000, 2, 8), e(4010001, 2, 8), e(4010002, 2, 8), e(4010005, 2, 8), e(4020004, 2, 8))),
            new RewardTier("Tier 2", List.of(
                    e(1022073, 1, 1), e(1012098, 1, 1), e(1012101, 1, 1), e(1012102, 1, 1), e(1012103, 1, 1),
                    e(2022055, 40, 8), e(2022056, 40, 8), e(2022103, 40, 8), e(2020029, 40, 8), e(2020032, 60, 8),
                    e(2020031, 60, 8), e(2022191, 60, 8), e(2022016, 60, 8), e(2043300, 1, 3), e(2043110, 1, 3),
                    e(2043800, 1, 3), e(2041001, 1, 3), e(2040903, 1, 3), e(4031203, 4, 8), e(4000021, 6, 8),
                    e(4003005, 7, 8), e(4003000, 5, 8), e(4003001, 2, 8), e(4010000, 4, 8), e(4010001, 4, 8),
                    e(4010003, 3, 8), e(4010004, 3, 8), e(4020004, 4, 8), e(3010004, 1, 2), e(3010005, 1, 2))),
            new RewardTier("Tier 3", List.of(
                    e(1302058, 1, 1), e(1372008, 1, 1), e(1422030, 1, 1), e(1422031, 1, 1), e(1022082, 1, 1),
                    e(2022279, 65, 8), e(2022120, 40, 8), e(2001001, 40, 8), e(2001002, 40, 8), e(2022071, 25, 8),
                    e(2022189, 25, 8), e(2040914, 1, 3), e(2041001, 1, 3), e(2041041, 1, 3), e(2041308, 1, 3),
                    e(4031203, 10, 8), e(4000030, 7, 8), e(4003005, 10, 8), e(4003000, 8, 8), e(4010004, 5, 8),
                    e(4010006, 5, 8), e(4020000, 5, 8), e(4020006, 5, 8), e(3010002, 1, 2), e(3010003, 1, 2))),
            new RewardTier("Tier 4", List.of(
                    e(1332029, 1, 1), e(1472027, 1, 1), e(1462032, 1, 1), e(1492019, 1, 1), e(2022045, 45, 8),
                    e(2022048, 40, 8), e(2022094, 25, 8), e(2022123, 20, 8), e(2022058, 60, 8), e(2041304, 1, 3),
                    e(2041019, 1, 3), e(2040826, 1, 3), e(2040758, 1, 3), e(4000030, 10, 8), e(4003005, 10, 8),
                    e(4003000, 20, 8), e(4010007, 5, 8), e(4011003, 1, 8), e(4021003, 1, 8), e(3010016, 1, 2),
                    e(3010017, 1, 2))),
            new RewardTier("Tier 5", List.of(
                    e(1382015, 1, 1), e(1382016, 1, 1), e(1442044, 1, 1), e(1382035, 1, 1), e(2022310, 20, 8),
                    e(2022068, 40, 8), e(2022069, 40, 8), e(2022190, 30, 8), e(2022047, 30, 8), e(2040727, 1, 3),
                    e(2040924, 1, 3), e(2040501, 1, 3), e(4000030, 20, 8), e(4003005, 20, 8), e(4003000, 25, 8),
                    e(4011003, 3, 8), e(4011006, 2, 8), e(4021004, 3, 8), e(3010099, 1, 2))),
            new RewardTier("Tier 6", List.of(
                    e(1442046, 1, 1), e(1432018, 1, 1), e(1102146, 1, 1), e(1102145, 1, 1), e(2022094, 35, 8),
                    e(2022544, 15, 8), e(2022123, 20, 8), e(2022310, 20, 8), e(2040727, 1, 3), e(2041058, 1, 3),
                    e(2040817, 1, 3), e(4000030, 30, 8), e(4003005, 30, 8), e(4003000, 30, 8), e(4011007, 1, 8),
                    e(4021009, 1, 8), e(4011008, 3, 8), e(3010098, 1, 2))));

    public static int tierCount() {
        return TIERS.size();
    }

    public static String label(int tier) {
        return TIERS.get(tier).label();
    }

    public static List<RewardEntry> entries(int tier) {
        return TIERS.get(tier).entries();
    }

    public static int ticketMultiplier(int ticketIndex) {
        return (ticketIndex == 1 || ticketIndex == 3) ? 3 : 1;
    }

    public static int pointsFor(int[] tickets, int coins) {
        int points = 0;
        for (int i = 0; i < tickets.length; i++) {
            if (tickets[i] <= 0) {
                continue;
            }
            points += 6 + ((tickets[i] - 1) * ticketMultiplier(i));
        }
        points += (int) Math.ceil(0.46 * coins);
        return points;
    }

    public static int tierFor(int[] tickets, int coins) {
        int points = pointsFor(tickets, coins);
        if (points <= 6) {
            return points <= 0 ? NO_TIER : 0;
        }
        if (points >= 46) {
            return 5;
        }
        return (points - 6) / 8;
    }

    public static int pickIndex(int[] weights, int drawn) {
        for (int i = 0; i < weights.length; i++) {
            drawn -= weights[i];
            if (drawn < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    public static int rollIndex(int tier) {
        int[] w = weightsOf(tier);
        return pickIndex(w, Randomizer.nextInt(sum(w)));
    }

    public static int rollIndex(int tier, RandomGenerator rng) {
        int[] w = weightsOf(tier);
        return pickIndex(w, rng.nextInt(sum(w)));
    }

    public static RewardEntry roll(int tier) {
        return entries(tier).get(rollIndex(tier));
    }

    private static int[] weightsOf(int tier) {
        return TIERS.get(tier).entries().stream().mapToInt(RewardEntry::weight).toArray();
    }

    private static int sum(int[] arr) {
        int s = 0;
        for (int x : arr) {
            s += x;
        }
        return s;
    }

    private static RewardEntry e(int itemId, int quantity, int weight) {
        return new RewardEntry(itemId, quantity, weight);
    }
}
