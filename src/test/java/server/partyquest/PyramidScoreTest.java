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
        assertEquals(0, Pyramid.computeRank(5, 3000), "S at 3000");
        assertEquals(0, Pyramid.computeRank(5, 5000), "S above 3000");
        assertEquals(1, Pyramid.computeRank(5, 2999), "A just below 3000");
        assertEquals(1, Pyramid.computeRank(5, 2000), "A at 2000");
        assertEquals(2, Pyramid.computeRank(5, 1999), "B just below 2000");
        assertEquals(2, Pyramid.computeRank(5, 1500), "B at 1500");
        assertEquals(3, Pyramid.computeRank(5, 1499), "C just below 1500");
        assertEquals(3, Pyramid.computeRank(5, 500), "C at 500");
        assertEquals(4, Pyramid.computeRank(5, 499), "D just below 500");
        assertEquals(4, Pyramid.computeRank(5, 0), "D at zero kills");
    }

    @Test
    void computeRank_earlyExitUsesCoarserTable() {
        // A failed/abandoned run (stage < 5) can only earn C or D regardless of kills.
        assertEquals(3, Pyramid.computeRank(3, 2000), "C at 2000 on early exit");
        assertEquals(3, Pyramid.computeRank(0, 5000), "C when >= 2000 regardless of count");
        assertEquals(4, Pyramid.computeRank(3, 1999), "D below 2000 on early exit");
        assertEquals(4, Pyramid.computeRank(0, 0), "D at zero kills");
    }

    @Test
    void computeExp_basePerRankAtEasyMode() {
        assertEquals(60500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0), "rank S base");
        assertEquals(55000, Pyramid.computeExp((byte) 1, Pyramid.PyramidMode.EASY, 0, 0), "rank A base");
        assertEquals(46750, Pyramid.computeExp((byte) 2, Pyramid.PyramidMode.EASY, 0, 0), "rank B base");
        assertEquals(22000, Pyramid.computeExp((byte) 3, Pyramid.PyramidMode.EASY, 0, 0), "rank C base");
        assertEquals(0, Pyramid.computeExp((byte) 4, Pyramid.PyramidMode.EASY, 0, 0), "rank D has no base EXP");
    }

    @Test
    void computeExp_scalesWithDifficultyMode() {
        // rank S base = 60500 + 5500 * mode
        assertEquals(60500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 0));
        assertEquals(66000, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.NORMAL, 0, 0));
        assertEquals(71500, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.HARD, 0, 0));
        assertEquals(77000, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.HELL, 0, 0));
    }

    @Test
    void computeExp_includesKillAndCoolBonuses() {
        int base = 60500; // rank S, EASY
        assertEquals(base + 3000 * 2, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 3000, 0), "kill bonus only");
        assertEquals(base + 50 * 10, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 0, 50), "cool bonus only");
        assertEquals(base + 100 * 2 + 10 * 10, Pyramid.computeExp((byte) 0, Pyramid.PyramidMode.EASY, 100, 10), "both bonuses");
    }
}
