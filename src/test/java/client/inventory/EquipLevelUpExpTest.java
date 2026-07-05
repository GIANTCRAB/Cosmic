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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipLevelUpExpTest {

    private static final int EXP_NEEDED = 1000;
    private static final int MAX_LEVEL = 30;

    @Test
    void belowThresholdDoesNotLevel() {
        Equip.LevelUpOutcome outcome = Equip.resolveExpGainLevelUp(999.0f, EXP_NEEDED, 1, MAX_LEVEL);

        assertFalse(outcome.leveledUp());
        assertEquals(999.0f, outcome.resultingExp(), 0.0f);
    }

    @Test
    void atThresholdLevelsOnceAndZeroesExp() {
        Equip.LevelUpOutcome outcome = Equip.resolveExpGainLevelUp(EXP_NEEDED, EXP_NEEDED, 1, MAX_LEVEL);

        assertTrue(outcome.leveledUp());
        assertEquals(0.0f, outcome.resultingExp(), 0.0f);
    }

    @Test
    void farAboveThresholdStillOnlyOneLevel() {
        // Regression guard: a huge exp gain (enough for many levels under the old loop)
        // must grant exactly ONE level, discarding all overflow.
        Equip.LevelUpOutcome outcome = Equip.resolveExpGainLevelUp(EXP_NEEDED * 8L, EXP_NEEDED, 1, MAX_LEVEL);

        assertTrue(outcome.leveledUp());
        assertEquals(0.0f, outcome.resultingExp(), 0.0f);
    }

    @Test
    void atMaxLevelNeverLevels() {
        Equip.LevelUpOutcome outcome = Equip.resolveExpGainLevelUp(
                EXP_NEEDED * 100L, EXP_NEEDED, MAX_LEVEL, MAX_LEVEL);

        assertFalse(outcome.leveledUp());
    }

    @Test
    void oneBelowMaxWithHugeExpLevelsOnceOnly() {
        Equip.LevelUpOutcome outcome = Equip.resolveExpGainLevelUp(
                EXP_NEEDED * 100L, EXP_NEEDED, MAX_LEVEL - 1, MAX_LEVEL);

        assertTrue(outcome.leveledUp());
        assertEquals(0.0f, outcome.resultingExp(), 0.0f);
    }
}
