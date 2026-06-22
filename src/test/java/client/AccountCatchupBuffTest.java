/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountCatchupBuffTest {

    @Test
    void noBonusAtOrAboveCeiling() {
        // gap <= 0 means the character has caught up to (or exceeded) the account ceiling
        assertEquals(0, Character.calculateCatchupBonusPercent(0, 4, 100));
        assertEquals(0, Character.calculateCatchupBonusPercent(-1, 4, 100));
        assertEquals(0, Character.calculateCatchupBonusPercent(-20, 4, 100));
    }

    @Test
    void rampsLinearlyToMaxAtThreshold() {
        // default tuning: threshold 4, max 100
        assertEquals(25, Character.calculateCatchupBonusPercent(1, 4, 100));
        assertEquals(50, Character.calculateCatchupBonusPercent(2, 4, 100));
        assertEquals(75, Character.calculateCatchupBonusPercent(3, 4, 100));
        assertEquals(100, Character.calculateCatchupBonusPercent(4, 4, 100));
    }

    @Test
    void capsAtMaxBeyondThreshold() {
        assertEquals(100, Character.calculateCatchupBonusPercent(5, 4, 100));
        assertEquals(100, Character.calculateCatchupBonusPercent(10, 4, 100));
        assertEquals(100, Character.calculateCatchupBonusPercent(49, 4, 100));
    }

    @Test
    void respectsCustomMaxAndThreshold() {
        // e.g. a gentler max of 60 reached at gap 3
        assertEquals(0, Character.calculateCatchupBonusPercent(0, 3, 60));
        assertEquals(20, Character.calculateCatchupBonusPercent(1, 3, 60));
        assertEquals(40, Character.calculateCatchupBonusPercent(2, 3, 60));
        assertEquals(60, Character.calculateCatchupBonusPercent(3, 3, 60));
        assertEquals(60, Character.calculateCatchupBonusPercent(8, 3, 60));
    }

    @Test
    void guardsAgainstZeroOrNegativeThreshold() {
        // a non-positive threshold is treated as 1 so the bonus never divides by zero
        assertEquals(100, Character.calculateCatchupBonusPercent(1, 0, 100));
        assertEquals(100, Character.calculateCatchupBonusPercent(1, -3, 100));
        assertEquals(0, Character.calculateCatchupBonusPercent(0, 0, 100));
    }

    @Test
    void flatStatRampsWithGapAndCombinesWithPercent() {
        // base STR 12, maxFlat 50: total bonus = base*pct/100 + flat*pct/100
        assertEquals(0, Character.calculateCatchupStatBonus(12, 0, 50));    // gap 0 -> no buff
        assertEquals(15, Character.calculateCatchupStatBonus(12, 25, 50));  // gap 1: 3 + 12
        assertEquals(31, Character.calculateCatchupStatBonus(12, 50, 50));  // gap 2: 6 + 25
        assertEquals(46, Character.calculateCatchupStatBonus(12, 75, 50));  // gap 3: 9 + 37
        assertEquals(62, Character.calculateCatchupStatBonus(12, 100, 50)); // gap >=4: 12 + 50
    }

    @Test
    void flatStatRespectsConfiguredMax() {
        // a larger flat configured at full ramp
        assertEquals(12 + 200, Character.calculateCatchupStatBonus(12, 100, 200));
        // mid ramp: 12*50/100 + 200*50/100 = 6 + 100
        assertEquals(106, Character.calculateCatchupStatBonus(12, 50, 200));
    }

    @Test
    void flatMaxScalesWithCeilingAndRoundsHalfUp() {
        // tierSize 10, flatPerTier 50: flatMax = roundHalfUp(highestLevel / 10) * 50
        assertEquals(200, Character.calculateCatchupFlatMax(40, 10, 50));   // tier 4
        assertEquals(200, Character.calculateCatchupFlatMax(44, 10, 50));   // still tier 4 (rounds down)
        assertEquals(250, Character.calculateCatchupFlatMax(45, 10, 50));   // rounds up to tier 5 at the midpoint
        assertEquals(250, Character.calculateCatchupFlatMax(49, 10, 50));   // tier 5
        assertEquals(600, Character.calculateCatchupFlatMax(120, 10, 50));  // tier 12
        assertEquals(0, Character.calculateCatchupFlatMax(0, 10, 50));      // no ceiling yet
    }

    @Test
    void flatMaxHonorsCustomTierSizeAndPerTier() {
        // tierSize 4, flatPerTier 30: flatMax = roundHalfUp(highestLevel / 4) * 30
        // roundHalfUp(40/4)=10 -> 300; roundHalfUp(42/4)=roundHalfUp(10.5)=11 -> 330
        assertEquals(300, Character.calculateCatchupFlatMax(40, 4, 30));
        assertEquals(330, Character.calculateCatchupFlatMax(42, 4, 30));
    }

    @Test
    void fullFlatMaxFeedsIntoStatBonusAtFullRamp() {
        // ceiling 120 -> flatMax 600 at full ramp; base STR 12 -> 12 (percent) + 600 (flat)
        assertEquals(612, Character.calculateCatchupStatBonus(12, 100, Character.calculateCatchupFlatMax(120, 10, 50)));
    }
}
