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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic tests for Nett's Pyramid reward computation (rank tiers + EXP formula), extracted
 * from {@link Pyramid#sendScore(client.Character)} into testable static helpers.
 */
class PyramidScoreTest {

    @Test
    void computeRank_fullClearUsesFullTierTable() {
        assertEquals(0, Pyramid.computeRank(5, 3000, Pyramid.PyramidMode.EASY), "S at 3000");
        assertEquals(0, Pyramid.computeRank(5, 5000, Pyramid.PyramidMode.EASY), "S above 3000");
        assertEquals(1, Pyramid.computeRank(5, 2999, Pyramid.PyramidMode.EASY), "A just below 3000");
        assertEquals(1, Pyramid.computeRank(5, 2000, Pyramid.PyramidMode.EASY), "A at 2000");
        assertEquals(2, Pyramid.computeRank(5, 1999, Pyramid.PyramidMode.EASY), "B just below 2000");
        assertEquals(2, Pyramid.computeRank(5, 1500, Pyramid.PyramidMode.EASY), "B at 1500");
        assertEquals(3, Pyramid.computeRank(5, 1499, Pyramid.PyramidMode.EASY), "C just below 1500");
        assertEquals(3, Pyramid.computeRank(5, 500, Pyramid.PyramidMode.EASY), "C at 500");
        assertEquals(4, Pyramid.computeRank(5, 499, Pyramid.PyramidMode.EASY), "D just below 500");
        assertEquals(4, Pyramid.computeRank(5, 0, Pyramid.PyramidMode.EASY), "D at zero kills");
    }

    @Test
    void computeRank_earlyExitUsesCoarserTable() {
        // A failed/abandoned run (stage < 5) can only earn C or D regardless of kills.
        assertEquals(3, Pyramid.computeRank(3, 2000, Pyramid.PyramidMode.EASY), "C at 2000 on early exit");
        assertEquals(3, Pyramid.computeRank(0, 5000, Pyramid.PyramidMode.EASY), "C when >= 2000 regardless of count");
        assertEquals(4, Pyramid.computeRank(3, 1999, Pyramid.PyramidMode.EASY), "D below 2000 on early exit");
        assertEquals(4, Pyramid.computeRank(0, 0, Pyramid.PyramidMode.EASY), "D at zero kills");
    }

    @Test
    void computeRank_requirementsScaleWithMode() {
        // HARD = m 2 -> scale = 3 (full-clear S/A/B/C thresholds: 9000/6000/4500/1500)
        assertEquals(0, Pyramid.computeRank(5, 9000, Pyramid.PyramidMode.HARD), "HARD S at 9000");
        assertEquals(1, Pyramid.computeRank(5, 8999, Pyramid.PyramidMode.HARD), "HARD A just below 9000");
        assertEquals(1, Pyramid.computeRank(5, 6000, Pyramid.PyramidMode.HARD), "HARD A at 6000");
        assertEquals(2, Pyramid.computeRank(5, 5999, Pyramid.PyramidMode.HARD), "HARD B just below 6000");
        assertEquals(2, Pyramid.computeRank(5, 4500, Pyramid.PyramidMode.HARD), "HARD B at 4500");
        assertEquals(3, Pyramid.computeRank(5, 4499, Pyramid.PyramidMode.HARD), "HARD C just below 4500");
        assertEquals(3, Pyramid.computeRank(5, 1500, Pyramid.PyramidMode.HARD), "HARD C at 1500");
        assertEquals(4, Pyramid.computeRank(5, 1499, Pyramid.PyramidMode.HARD), "HARD D just below 1500");

        // HELL = m 3 -> scale = 4 (full-clear S/A/B/C thresholds: 12000/8000/6000/2000)
        assertEquals(0, Pyramid.computeRank(5, 12000, Pyramid.PyramidMode.HELL), "HELL S at 12000");
        assertEquals(1, Pyramid.computeRank(5, 11999, Pyramid.PyramidMode.HELL), "HELL A just below 12000");
        assertEquals(1, Pyramid.computeRank(5, 8000, Pyramid.PyramidMode.HELL), "HELL A at 8000");
        assertEquals(2, Pyramid.computeRank(5, 7999, Pyramid.PyramidMode.HELL), "HELL B just below 8000");
        assertEquals(2, Pyramid.computeRank(5, 6000, Pyramid.PyramidMode.HELL), "HELL B at 6000");
        assertEquals(3, Pyramid.computeRank(5, 5999, Pyramid.PyramidMode.HELL), "HELL C just below 6000");
        assertEquals(3, Pyramid.computeRank(5, 2000, Pyramid.PyramidMode.HELL), "HELL C at 2000");
        assertEquals(4, Pyramid.computeRank(5, 1999, Pyramid.PyramidMode.HELL), "HELL D just below 2000");
    }

    @Test
    void computeRank_earlyExitRequirementsScaleWithMode() {
        // NORMAL = m 1 -> scale = 2, early-exit C threshold = 2000 * 2 = 4000
        assertEquals(3, Pyramid.computeRank(3, 4000, Pyramid.PyramidMode.NORMAL), "NORMAL C at 4000 on early exit");
        assertEquals(4, Pyramid.computeRank(3, 3999, Pyramid.PyramidMode.NORMAL), "NORMAL D below 4000 on early exit");

        // HELL = m 3 -> scale = 4, early-exit C threshold = 2000 * 4 = 8000
        assertEquals(3, Pyramid.computeRank(0, 8000, Pyramid.PyramidMode.HELL), "HELL C at 8000 on early exit");
        assertEquals(4, Pyramid.computeRank(0, 7999, Pyramid.PyramidMode.HELL), "HELL D below 8000 on early exit");
    }

    @Test
    void computeExp_basePerRankAtEasyMode() {
        assertEquals(60500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0, 1), "rank S base");
        assertEquals(55000, Pyramid.computeExp((byte) 1, Pyramid.PyramidMode.EASY, 0, 0, 1), "rank A base");
        assertEquals(46750, Pyramid.computeExp((byte) 2, Pyramid.PyramidMode.EASY, 0, 0, 1), "rank B base");
        assertEquals(22000, Pyramid.computeExp((byte) 3, Pyramid.PyramidMode.EASY, 0, 0, 1), "rank C base");
        assertEquals(0, Pyramid.computeExp((byte) 4, Pyramid.PyramidMode.EASY, 0, 0, 1), "rank D has no base EXP");
    }

    @Test
    void computeExp_scalesWithDifficultyMode() {
        // rank S base = 60500 + 55000 * mode
        assertEquals(60500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0, 1));
        assertEquals(115500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.NORMAL, 0, 0, 1));
        assertEquals(170500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.HARD, 0, 0, 1));
        assertEquals(225500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.HELL, 0, 0, 1));
    }

    @Test
    void computeExp_includesKillAndCoolBonuses() {
        int base = 60500; // rank S, EASY
        assertEquals(base + 3000 * 2, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 3000, 0, 1), "kill bonus only");
        assertEquals(base + 50 * 10, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 50, 1), "cool bonus only");
        assertEquals(base + 100 * 2 + 10 * 10, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 100, 10, 1), "both bonuses");
    }

    @Test
    void computeExp_scalesByWorldExpRate() {
        // rank S, EASY, no kills/cools: base 60500 scaled by expRate
        assertEquals(121000, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0, 2));
        assertEquals(181500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0, 3));
    }

    @Test
    void computeExp_killAndCoolBonusScalesWithMode() {
        // rank S, 3000 kills, expRate 1: bonus = kills * (m+1) * 2
        assertEquals(127500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.NORMAL, 3000, 0, 1));
        assertEquals(249500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.HELL, 3000, 0, 1));
    }
}
