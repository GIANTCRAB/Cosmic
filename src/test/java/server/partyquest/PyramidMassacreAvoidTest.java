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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic tests for the "Rage of Pharaoh" massacre skill's accuracy empowerment. The AVOID
 * debuff value is centralized in {@link Pyramid#yetiAvoidDebuffValue()} so that it can be verified
 * to exactly cancel the yeti's evasion, and so the sign is pinned (a regression here would silently
 * make the skill useless or counterproductive).
 */
class PyramidMassacreAvoidTest {

    @Test
    void debuffExactlyCancelsYetiEvasion() {
        assertEquals(Pyramid.YETI_EVASION + Pyramid.yetiAvoidDebuffValue(), 0,
                "debuff must zero out the yeti's evasion so the client stops reporting misses");
    }

    @Test
    void debuffIsNegativeSoItLowersAvoidability() {
        // a positive AVOID value raises a mob's avoidability (see MobSkill EVA handling), so the
        // empowerment must be negative to make the yeti hittable.
        assertTrue(Pyramid.yetiAvoidDebuffValue() < 0,
                "debuff must be negative, got " + Pyramid.yetiAvoidDebuffValue());
    }

    @Test
    void debuffMagnitudeMatchesYetiEvasion() {
        assertEquals(Pyramid.YETI_EVASION, -Pyramid.yetiAvoidDebuffValue());
    }
}
