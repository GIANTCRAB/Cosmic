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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pure-logic tests for the hit classification that drives Nett's Pyramid kill/cool/miss tracking.
 * The same {@link Pyramid#classifyHit} is invoked by {@link server.life.Monster#damage}.
 */
class PyramidHitClassificationTest {

    @Test
    void nonConnectingHitIsAlwaysMiss() {
        assertEquals(Pyramid.HitType.MISS, Pyramid.classifyHit(0, 0, 0, 0));
        assertEquals(Pyramid.HitType.MISS, Pyramid.classifyHit(0, 1000, 100, 0));
        assertEquals(Pyramid.HitType.MISS, Pyramid.classifyHit(-5, 1000, 100, 0));
    }

    @Test
    void hitWithoutCoolDamageIsKill() {
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(500, 0, 0, 0));
        // a cool-prob is irrelevant when the mob carries no coolDamage threshold
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(500, 0, 100, 0));
    }

    @Test
    void hitBelowCoolDamageThresholdIsKill() {
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(500, 1000, 100, 0));
    }

    @Test
    void hardHitRollingUnderProbIsCool() {
        assertEquals(Pyramid.HitType.COOL, Pyramid.classifyHit(1000, 1000, 50, 49));
        assertEquals(Pyramid.HitType.COOL, Pyramid.classifyHit(2000, 1000, 100, 99));
        assertEquals(Pyramid.HitType.COOL, Pyramid.classifyHit(1000, 1000, 50, 0));
    }

    @Test
    void hardHitRollingAtOrOverProbIsKill() {
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(1000, 1000, 50, 50));
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(1000, 1000, 50, 99));
    }

    @Test
    void zeroCoolProbNeverProducesCool() {
        assertEquals(Pyramid.HitType.KILL, Pyramid.classifyHit(5000, 1000, 0, 0));
        assertNotEquals(Pyramid.HitType.COOL, Pyramid.classifyHit(5000, 1000, 0, 0));
    }
}
