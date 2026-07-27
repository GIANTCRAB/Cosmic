/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.partyquest;

import constants.id.ItemId;
import constants.id.MobId;
import org.junit.jupiter.api.Test;
import server.life.MonsterDropEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for {@link PharaohTomb}'s decision helpers. These encode the reward-chain
 * invariants that make the Pharaoh Belt obtainable end-to-end:
 * <ul>
 *   <li>The chest the Jr. Yeti drops is mode-specific, and only HELL yields the upgraded chest
 *       (the sole source of the Immortal Pharaoh Belt).</li>
 *   <li>Exactly one Jr. Yeti spawns per gem entry (1 gem = 1 chest).</li>
 *   <li>The chest is a guaranteed, single, non-quest drop regardless of the player's drop rate.</li>
 * </ul>
 * No I/O -- these helpers are deliberately decoupled from {@link server.maps.MapleMap},
 * {@link server.life.LifeFactory}, and timers so they are exercised directly here.
 */
class PharaohTombTest {

    @Test
    void chestForModeGivesStandardChestForEasyNormalHard() {
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST, PharaohTomb.chestForMode(Pyramid.PyramidMode.EASY));
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST, PharaohTomb.chestForMode(Pyramid.PyramidMode.NORMAL));
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST, PharaohTomb.chestForMode(Pyramid.PyramidMode.HARD));
    }

    @Test
    void chestForModeGivesHellChestOnlyForHell() {
        // The HELL chest's WZ reward table is the sole source of the Immortal Pharaoh Belt (1132013).
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST_HELL, PharaohTomb.chestForMode(Pyramid.PyramidMode.HELL));
    }

    @Test
    void chestForModeCoversEveryMode() {
        // Guards the "immortal belt is HELL-only" invariant for the whole enum.
        for (Pyramid.PyramidMode mode : Pyramid.PyramidMode.values()) {
            int chest = PharaohTomb.chestForMode(mode);
            assertTrue(chest == ItemId.PHARAOHS_TREASURE_CHEST || chest == ItemId.PHARAOHS_TREASURE_CHEST_HELL,
                    "unexpected chest id " + chest + " for " + mode);
            assertEquals(mode == Pyramid.PyramidMode.HELL, chest == ItemId.PHARAOHS_TREASURE_CHEST_HELL,
                    "HELL-and-only-HELL must map to the HELL chest; " + mode + " broke that");
        }
    }

    @Test
    void yetisToSpawnInTombIsFixedOne() {
        // Duarte's dialog ("defeating the Pharaoh Jr. Yeti") and the 1-gem-1-chest economy.
        assertEquals(1, PharaohTomb.yetisToSpawnInTomb());
    }

    @Test
    void yetiSpawnPointIsDeterministicAndGroundLevel() {
        // The fixed spawn x is what makes the spawn independent of the (post-warp) player position.
        assertNotNull(PharaohTomb.YETI_SPAWN_POINT);
        assertEquals(150, PharaohTomb.YETI_SPAWN_POINT.x);
    }

    @Test
    void tombDropListProducesOneGuaranteedChestEntry() {
        List<MonsterDropEntry> drops = PharaohTomb.tombDropList(ItemId.PHARAOHS_TREASURE_CHEST);
        assertEquals(1, drops.size(), "the Jr. Yeti must drop exactly one chest");

        MonsterDropEntry entry = drops.get(0);
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST, entry.itemId);
        assertEquals(1_000_000, entry.chance, "chance must exceed the 999999 roll ceiling so the drop is guaranteed even at 1x drop rate");
        assertEquals(1, entry.Minimum);
        assertEquals(1, entry.Maximum, "min = max = 1 -> exactly one chest, never a stack");
        assertEquals(0, entry.questid, "non-quest drop so it routes through the normal drop path");
    }

    @Test
    void tombDropListReflectsTheModePassedIn() {
        // Same helper, HELL chest -> the drop that can yield the Immortal Belt.
        List<MonsterDropEntry> hellDrops = PharaohTomb.tombDropList(ItemId.PHARAOHS_TREASURE_CHEST_HELL);
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST_HELL, hellDrops.get(0).itemId);
    }

    @Test
    void tombDropListIsFreshPerCall() {
        // Each spawned Jr. Yeti gets its own override list; mutating one must not leak to another.
        List<MonsterDropEntry> a = PharaohTomb.tombDropList(ItemId.PHARAOHS_TREASURE_CHEST);
        List<MonsterDropEntry> b = PharaohTomb.tombDropList(ItemId.PHARAOHS_TREASURE_CHEST);
        assertTrue(a != b, "tombDropList must return a fresh list per call so per-mob overrides are independent");
    }

    @Test
    void chestForModeFeedsDropListEndToEnd() {
        // The full reward chain: mode -> chest -> drop entry, for each difficulty.
        for (Pyramid.PyramidMode mode : Pyramid.PyramidMode.values()) {
            int chest = PharaohTomb.chestForMode(mode);
            MonsterDropEntry entry = PharaohTomb.tombDropList(chest).get(0);
            assertEquals(chest, entry.itemId, "drop entry must carry the mode-matched chest for " + mode);
            assertEquals(1_000_000, entry.chance);
        }
    }

    @Test
    void chestIdsAreDistinctAndNonZero() {
        // Sanity: the two chests are genuinely different items (else the mode split would be a no-op).
        assertTrue(ItemId.PHARAOHS_TREASURE_CHEST != ItemId.PHARAOHS_TREASURE_CHEST_HELL);
        assertTrue(ItemId.PHARAOHS_TREASURE_CHEST != 0);
        assertTrue(ItemId.PHARAOHS_TREASURE_CHEST_HELL != 0);
    }

    @Test
    void jrYetiMobIdIsThePharaohsClone() {
        // Locks the mob id: 9700019 is the 1-HP "Pharaoh's clone" from the tomb, NOT the evasive
        // transparent-yeti family (9700021/22/23) used as stage-4/5 harassers in the battle PQ.
        assertEquals(9700019, MobId.PHARAOH_JR_YETI);
    }
}
