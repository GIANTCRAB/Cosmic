/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server;

import provider.Data;
import provider.DataTool;
import server.ItemInformationProvider.RewardItem;
import tools.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure parser for a WZ {@code reward} table (the random-reward table on use-items such as the
 * Pharaoh's Treasure Chest). Walks each entry child of the supplied {@code reward} node, reads its
 * {@code item}/{@code prob}/{@code count}/... fields into a {@link RewardItem}, and sums the
 * {@code prob} values into a total returned alongside the list.
 *
 * <p>Extracted from {@link ItemInformationProvider#getItemReward(int)} as a standalone class so the
 * parsing is unit-testable in isolation: {@link ItemInformationProvider}'s static initializer eagerly
 * loads card-id data from the drop-data DB, so any static method called directly on it cannot run in
 * a unit test without standing up the database. This class has no such initializer and is DB-free.
 *
 * <p>The {@code prob} field is read as a full int. WZ reward probs routinely exceed a byte (e.g.
 * 4500/5500 for the Pharaoh chests' potion slots, 15 for the Pharaoh Belt); a historical
 * {@code (byte)} cast in the old in-line parser truncated them, drove the total probability negative,
 * and made {@code Randomizer.nextInt(totalprob)} throw -- the exception was swallowed in the packet
 * dispatcher, so reward chests silently did nothing and were never consumed (the Pharaoh Belt was
 * therefore unreachable even when a chest dropped).
 */
public final class RewardTableParser {

    private RewardTableParser() {
    }

    public static Pair<Integer, List<RewardItem>> parse(Data rewardRoot) {
        int totalprob = 0;
        List<RewardItem> rewards = new ArrayList<>();
        for (Data child : rewardRoot.getChildren()) {
            RewardItem reward = new RewardItem();
            reward.itemid = DataTool.getInt("item", child, 0);
            reward.prob = DataTool.getInt("prob", child, 0);
            reward.quantity = (short) DataTool.getInt("count", child, 0);
            reward.effect = DataTool.getString("Effect", child, "");
            reward.worldmsg = DataTool.getString("worldMsg", child, null);
            reward.period = DataTool.getInt("period", child, -1);

            totalprob += reward.prob;

            rewards.add(reward);
        }
        return new Pair<>(totalprob, rewards);
    }
}
