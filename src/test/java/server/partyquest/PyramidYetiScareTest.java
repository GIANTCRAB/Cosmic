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
 * Pure-logic tests for the "approaching yeti" scare-tier selection driven by the combined kill+cool
 * total. The roll is injected so the decision is fully deterministic.
 */
class PyramidYetiScareTest {

    @Test
    void neverScaresAtOrBelowZeroTotal() {
        assertEquals(-1, Pyramid.yetiScareTier(0, 0));
        assertEquals(-1, Pyramid.yetiScareTier(-1, 0));
    }

    @Test
    void neverScaresWhenRollMeetsOrExceedsThreshold() {
        // even at an exact boundary, roll >= 20 suppresses the scare
        assertEquals(-1, Pyramid.yetiScareTier(100, 20));
        assertEquals(-1, Pyramid.yetiScareTier(500, 99));
    }

    @Test
    void boundaryTotalsMapToHighestMatchingTier() {
        // each i*100 boundary (i = 1..5) maps to tier i-1
        assertEquals(0, Pyramid.yetiScareTier(100, 19), "100 -> tier 0");
        assertEquals(1, Pyramid.yetiScareTier(200, 19), "200 -> tier 1 (200%200==0 before 200%100)");
        assertEquals(2, Pyramid.yetiScareTier(300, 19), "300 -> tier 2");
        assertEquals(3, Pyramid.yetiScareTier(400, 19), "400 -> tier 3");
        assertEquals(4, Pyramid.yetiScareTier(500, 19), "500 -> tier 4");
    }

    @Test
    void rollsJustUnderThresholdStillFire() {
        assertEquals(0, Pyramid.yetiScareTier(100, 0));
        assertEquals(0, Pyramid.yetiScareTier(100, 19.99));
    }

    @Test
    void highestBoundaryWinsWhenMultipleDivide() {
        // 1000 is a multiple of 500 and 250(not checked) and 200 and 100 -> highest is i=5 (tier 4)
        assertEquals(4, Pyramid.yetiScareTier(1000, 5));
        // 600 is divisible by 300 and 200 and 100 -> highest i=3 (tier 2)
        assertEquals(2, Pyramid.yetiScareTier(600, 5));
        // 700 is only divisible by 100 (i=1) since 700 % 500/400/300/200 != 0 -> tier 0
        assertEquals(0, Pyramid.yetiScareTier(700, 5));
    }

    @Test
    void nonBoundaryTotalNeverScaresEvenWithLowRoll() {
        assertEquals(-1, Pyramid.yetiScareTier(99, 0));
        assertEquals(-1, Pyramid.yetiScareTier(150, 0), "150 is not a multiple of any i*100");
        assertEquals(-1, Pyramid.yetiScareTier(50, 0));
        assertEquals(-1, Pyramid.yetiScareTier(1337, 0));
    }
}
