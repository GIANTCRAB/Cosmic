/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.life;

import client.Character;
import org.junit.jupiter.api.Test;
import server.partyquest.Pyramid;
import testutil.Mocks;
import tools.Pair;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Nett's Pyramid hook wired into {@link Monster#damage}. They verify that
 * hits landed on a pyramid map are classified (miss/kill/cool) and dispatched to the player's
 * {@link Pyramid}, and that the hook is correctly guarded outside pyramid maps.
 */
class PyramidDamageHookTest {

    private static final int PYRAMID_MAP = 926010100;
    private static final int MUMMYDOG = 9700004;

    @Test
    void zeroDamageHitDispatchesMiss() {
        Monster mob = livingMob();
        Pyramid py = mock(Pyramid.class);
        Character attacker = pyramidAttacker(PYRAMID_MAP, py);

        mob.damage(attacker, 0, false);

        verify(py).miss();
        verify(py, never()).kill();
        verify(py, never()).cool();
    }

    @Test
    void normalHitDispatchesKill() {
        // A regular pyramid mob carries no coolDamage, so any connecting hit is a KILL.
        Monster mob = livingMob();
        Pyramid py = mock(Pyramid.class);
        Character attacker = pyramidAttacker(PYRAMID_MAP, py);

        mob.damage(attacker, 500, false);

        verify(py).kill();
        verify(py, never()).cool();
        verify(py, never()).miss();
    }

    @Test
    void heavyHitOnCoolMobDispatchesCool() {
        // coolDamage=100, coolProb=100% -> Math.random()*100 is always < 100, so a hard hit is COOL.
        Monster mob = livingMobWithCool(100, 100);
        Pyramid py = mock(Pyramid.class);
        Character attacker = pyramidAttacker(PYRAMID_MAP, py);

        mob.damage(attacker, 500, false);

        verify(py).cool();
        verify(py, never()).kill();
    }

    @Test
    void hitsOutsidePyramidDoNotTouchPartyQuest() {
        Monster mob = livingMob();
        Pyramid py = mock(Pyramid.class);
        Character attacker = pyramidAttacker(100000000, py); // Henesys, not a pyramid map

        mob.damage(attacker, 500, false);

        verifyNoInteractions(py);
    }

    @Test
    void pyramidMapWithoutActivePartyQuestIsSafe() {
        Monster mob = livingMob();
        Character attacker = pyramidAttacker(PYRAMID_MAP, null);

        mob.damage(attacker, 500, false); // must not throw
    }

    @Test
    void nullAttackerWithZeroDamageIsSafe() {
        Monster mob = livingMob();
        // damage == 0 skips applyDamage; the pyramid-branch null guard must keep this NPE-free.
        mob.damage(null, 0, false);
    }

    private static Monster livingMob() {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100_000);
        return new Monster(MUMMYDOG, stats);
    }

    private static Monster livingMobWithCool(int coolDamage, int coolProb) {
        MonsterStats stats = new MonsterStats();
        stats.setHp(100_000);
        stats.setCool(new Pair<>(coolDamage, coolProb));
        return new Monster(MUMMYDOG, stats);
    }

    private static Character pyramidAttacker(int mapId, Pyramid py) {
        Character attacker = Mocks.chr();
        when(attacker.getMapId()).thenReturn(mapId);
        when(attacker.getPartyQuest()).thenReturn(py);
        when(attacker.getParty()).thenReturn(null); // broadcastMobHpBar else-branch -> direct sendPacket
        return attacker;
    }
}
