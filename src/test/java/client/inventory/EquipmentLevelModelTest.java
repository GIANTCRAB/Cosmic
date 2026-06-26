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

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentLevelModelTest {
    private int previousMax;

    @BeforeEach
    void saveConfiguredCeiling() {
        previousMax = YamlConfig.config.server.USE_EQUIPMNT_LVLUP;
    }

    @AfterEach
    void restoreConfiguredCeiling() {
        YamlConfig.config.server.USE_EQUIPMNT_LVLUP = previousMax;
    }

    @Test
    void networkLevelClampsToClientCap() {
        assertEquals(1, EquipmentLevelModel.networkLevelOf(1));
        assertEquals(15, EquipmentLevelModel.networkLevelOf(15));
        assertEquals(30, EquipmentLevelModel.networkLevelOf(30));
        assertEquals(30, EquipmentLevelModel.networkLevelOf(31));
        assertEquals(30, EquipmentLevelModel.networkLevelOf(100));
    }

    @Test
    void networkLevelClampsBelowOne() {
        assertEquals(1, EquipmentLevelModel.networkLevelOf(0));
        assertEquals(1, EquipmentLevelModel.networkLevelOf(-5));
    }

    @Test
    void isAtNetworkCapTrueAtOrAboveThirty() {
        assertFalse(EquipmentLevelModel.isAtNetworkCap(29));
        assertTrue(EquipmentLevelModel.isAtNetworkCap(30));
        assertTrue(EquipmentLevelModel.isAtNetworkCap(100));
    }

    @Test
    void trueMaxLevelHonoursConfigAndFloorsAtOne() {
        assertEquals(1, EquipmentLevelModel.trueMaxLevel(1));
        assertEquals(100, EquipmentLevelModel.trueMaxLevel(100));
        assertEquals(1, EquipmentLevelModel.trueMaxLevel(0));
        assertEquals(1, EquipmentLevelModel.trueMaxLevel(-5));
    }

    @Test
    void isAtTrueMaxUsesConfiguredCeiling() {
        YamlConfig.config.server.USE_EQUIPMNT_LVLUP = 100;
        assertFalse(EquipmentLevelModel.isAtTrueMax(99));
        assertTrue(EquipmentLevelModel.isAtTrueMax(100));
        assertTrue(EquipmentLevelModel.isAtTrueMax(150));
    }

    @Test
    void expNeededForTrueLevelUsesGmsTableBelowThirty() {
        assertEquals(15, EquipmentLevelModel.expNeededForTrueLevel(1));
        assertEquals(7093, EquipmentLevelModel.expNeededForTrueLevel(29));
    }

    @Test
    void expNeededForTrueLevelMatchesTableAnchorAtThirty() {
        // Level 30 falls on the curve: 10000 * 1.15^0 == last GMS table entry.
        assertEquals(10000, EquipmentLevelModel.expNeededForTrueLevel(30));
    }

    @Test
    void expNeededForTrueLevelAppliesCurveBeyondThirty() {
        assertEquals(11500, EquipmentLevelModel.expNeededForTrueLevel(31));   // 10000 * 1.15^1
        assertEquals(20114, EquipmentLevelModel.expNeededForTrueLevel(35));   // 10000 * 1.15^5
    }

    @Test
    void expNeededForTrueLevelIsMonotonicIncreasingAcrossTableAndCurve() {
        int previous = EquipmentLevelModel.expNeededForTrueLevel(1);
        for (int level = 2; level <= 100; level++) {
            int current = EquipmentLevelModel.expNeededForTrueLevel(level);
            assertTrue(current > previous, "EXP not increasing at level " + level + ": " + previous + " -> " + current);
            previous = current;
        }
    }

    @Test
    void expNeededForTrueLevelClampsToIntegerMaxAtExtremeLevel() {
        assertEquals(Integer.MAX_VALUE, EquipmentLevelModel.expNeededForTrueLevel(1000));
    }

    @Test
    void expNeededForNetworkLevelIsAlwaysInTableRange() {
        assertEquals(15, EquipmentLevelModel.expNeededForNetworkLevel(1));
        assertEquals(1060, EquipmentLevelModel.expNeededForNetworkLevel(15));
        assertEquals(10000, EquipmentLevelModel.expNeededForNetworkLevel(30));
        assertEquals(10000, EquipmentLevelModel.expNeededForNetworkLevel(31));   // clamped
    }
}
