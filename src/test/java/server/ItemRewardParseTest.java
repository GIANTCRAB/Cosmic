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

import org.junit.jupiter.api.Test;
import provider.Data;
import server.ItemInformationProvider.RewardItem;
import tools.Pair;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-parse tests for {@link RewardTableParser}, the helper behind reward-chest use-items (e.g. the
 * Pharaoh's Treasure Chest dropped by the tomb's Jr. Yeti). The parser is a standalone class so it
 * can be exercised without bootstrapping {@link ItemInformationProvider}'s DB-backed static
 * initializer.
 *
 * <p>Regression guard for the historical {@code (byte)} cast on {@code prob}: the WZ prob values for
 * the Pharaoh chests reach into the thousands (4500, 5500, ...) and 15 for the Pharaoh Belt. A byte
 * cast truncated them (4500 -> -108, 5500 -> 124, ...), drove the total probability negative, and
 * made {@code Randomizer.nextInt(totalprob)} throw -- the exception was silently swallowed in
 * {@code Client.channelRead}, so the chest did nothing and was never consumed. The Pharaoh Belt was
 * therefore unreachable even though the chest dropped. These tests build a synthetic WZ reward tree
 * (no WZ/DB bootstrap needed) and assert the probs are read as full ints.
 */
class ItemRewardParseTest {

    @Test
    void probIsReadAsFullIntNotByteTruncated() {
        // 4500 (byte) -> -108; the fix reads it verbatim. This is the crux of the regression.
        Data root = rewardTable(entry(2000009, 4500, 10));

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        assertEquals(4500, out.getLeft(), "totalprob must be 4500, not the byte-truncated -108");
        assertEquals(4500, out.getRight().get(0).prob);
    }

    @Test
    void multipleProbsSumWithoutOverflow() {
        // Mirrors the top of the real Pharaoh chest table: two big potion probs that overflow a byte.
        Data root = rewardTable(
                entry(2000009, 4500, 10),
                entry(2000010, 5500, 10));

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        assertEquals(10_000, out.getLeft(), "4500 + 5500 = 10000, not the byte-corrupted -108+124=16");
    }

    @Test
    void pharaohChestTableParsesToExpectedTotalWithBeltIntact() {
        // Faithfully reconstructs the actual 2022613 WZ reward table (prob values from 0202.img.xml):
        // 6 potion slots + 16 weapon scrolls (prob 6 each) + 3 rare scrolls + the Pharaoh Belt. With
        // the old (byte) cast, totalprob was -30 and the belt's prob 15 survived only by luck (it fits
        // in a byte) -- but the negative total made nextInt throw before any entry could be rolled.
        Data root = rewardTable(
                entry(2000009, 4500, 10),
                entry(2000010, 5500, 10),
                entry(2000006, 1500, 7),
                entry(2022003, 1500, 7),
                entry(2000004, 1200, 5),
                entry(2000005, 500, 3),
                entry(2043001, 6, 1),
                entry(2043101, 6, 1),
                entry(2043201, 6, 1),
                entry(2043301, 6, 1),
                entry(2043701, 6, 1),
                entry(2043801, 6, 1),
                entry(2044001, 6, 1),
                entry(2044101, 6, 1),
                entry(2044201, 6, 1),
                entry(2044301, 6, 1),
                entry(2044401, 6, 1),
                entry(2044501, 6, 1),
                entry(2044601, 6, 1),
                entry(2044701, 6, 1),
                entry(2044801, 6, 1),
                entry(2044901, 6, 1),
                entry(2040804, 5, 1),
                entry(2049100, 1, 1),
                entry(2049000, 1, 1),
                entry(1132012, 15, 1));   // the Pharaoh Belt

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        assertEquals(14_818, out.getLeft(), "the real 2022613 table sums to 14818 (was -30 under the byte cast)");

        RewardItem belt = findByItem(out.getRight(), 1132012);
        assertNotNull(belt, "the Pharaoh Belt entry must be present");
        assertEquals(15, belt.prob, "belt prob must be 15, not byte-truncated");
        assertEquals(1, belt.quantity);
    }

    @Test
    void hellChestTableCanContainImmortalBelt() {
        // The HELL chest (2022618) is the SOLE source of the Immortal Pharaoh Belt (1132013). This
        // locks that the parse preserves an entry beyond the standard belt slot.
        Data root = rewardTable(
                entry(1132012, 8, 1),     // normal Pharaoh Belt -- also present in the HELL chest
                entry(1132013, 3, 1));    // Immortal Pharaoh Belt -- HELL chest only

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        assertEquals(11, out.getLeft());
        assertNotNull(findByItem(out.getRight(), 1132013), "Immortal Pharaoh Belt must be parseable from the HELL chest table");
        assertNotNull(findByItem(out.getRight(), 1132012));
    }

    @Test
    void emptyRewardTableYieldsZeroTotal() {
        Data root = mock(Data.class);
        when(root.getChildren()).thenReturn(new ArrayList<>());

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        assertEquals(0, out.getLeft());
        assertEquals(0, out.getRight().size());
    }

    @Test
    void quantityAndPeriodDefaultsApplyWhenAbsent() {
        // A reward entry with only item/prob set must fall back to count=0 (-> short 0) and period=-1.
        // Build the int sub-nodes as locals first to avoid Mockito's nested-stubbing detector.
        Data itemNode = intNode(1132012);
        Data probNode = intNode(15);
        Data onlyItemProb = mock(Data.class);
        when(onlyItemProb.getChildByPath("item")).thenReturn(itemNode);
        when(onlyItemProb.getChildByPath("prob")).thenReturn(probNode);
        // count / period / Effect / worldMsg left to default null -> count 0, period -1, effect "", msg null
        Data root = mock(Data.class);
        when(root.getChildren()).thenReturn(List.of(onlyItemProb));

        Pair<Integer, List<RewardItem>> out = RewardTableParser.parse(root);

        RewardItem r = out.getRight().get(0);
        assertEquals((short) 0, r.quantity);
        assertEquals(-1, r.period);
    }

    // ----- synthetic WZ builders -------------------------------------------------

    private static Data rewardTable(Data... entries) {
        Data root = mock(Data.class);
        when(root.getChildren()).thenReturn(List.of(entries));
        return root;
    }

    private static Data entry(int item, int prob, int count) {
        // Build the int sub-nodes first so their stubbing completes before the parent's when() chain
        // starts -- nesting when().thenReturn(intNode()) inside another stub trips Mockito's
        // unfinished-stubbing detector.
        Data itemNode = intNode(item);
        Data probNode = intNode(prob);
        Data countNode = intNode(count);
        Data e = mock(Data.class);
        when(e.getChildByPath("item")).thenReturn(itemNode);
        when(e.getChildByPath("prob")).thenReturn(probNode);
        when(e.getChildByPath("count")).thenReturn(countNode);
        return e;
    }

    private static Data intNode(int value) {
        // DataTool.getInt(Data, def): non-null getData() that is an Integer, and getType() != STRING
        // -> returns the int. Mockito's default getType() is null (!= STRING), so no need to stub it.
        Data n = mock(Data.class);
        when(n.getData()).thenReturn(value);
        return n;
    }

    private static RewardItem findByItem(List<RewardItem> rewards, int itemId) {
        return rewards.stream().filter(r -> r.itemid == itemId).findFirst().orElse(null);
    }
}
