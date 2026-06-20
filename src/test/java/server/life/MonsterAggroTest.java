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
import client.Client;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import testutil.Mocks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the {@code firstAttack} flag behavior in monster aggro
 * assignment. These guard against the bug where {@code aggroUpdateController}
 * always passed {@code false} for immediate aggro, ignoring the
 * {@code firstAttack} property loaded from wz data.
 */
class MonsterAggroTest {

    @Test
    void aggroUpdateController_shouldSetImmediateAggro_forFirstAttackMonster() {
        MonsterStats stats = new MonsterStats();
        stats.setFirstAttack(true);

        Monster monster = createMonsterWithCandidateController(stats);
        monster.aggroUpdateController();

        assertTrue(monster.isControllerHasAggro(),
                "A firstAttack monster should enter aggro state immediately when a controller is assigned");
    }

    @Test
    void aggroUpdateController_shouldNotSetImmediateAggro_forNormalMonster() {
        MonsterStats stats = new MonsterStats();
        stats.setFirstAttack(false);

        Monster monster = createMonsterWithCandidateController(stats);
        monster.aggroUpdateController();

        assertFalse(monster.isControllerHasAggro(),
                "A non-firstAttack monster should not enter aggro state until provoked");
    }

    @Test
    void isFirstAttack_shouldReflectMonsterStats() {
        MonsterStats aggressive = new MonsterStats();
        aggressive.setFirstAttack(true);
        Monster aggressiveMob = new Monster(3210800, aggressive);

        MonsterStats passive = new MonsterStats();
        passive.setFirstAttack(false);
        Monster passiveMob = new Monster(3210800, passive);

        assertTrue(aggressiveMob.isFirstAttack(), "Monster should report firstAttack from its stats");
        assertFalse(passiveMob.isFirstAttack(), "Monster should report firstAttack from its stats");
    }

    /**
     * Creates a Monster spy with the given stats, a mocked map containing a
     * single candidate controller, and {@code aggroUpdatePuppetVisibility}
     * stubbed out to avoid channel-server dependencies.
     */
    private static Monster createMonsterWithCandidateController(MonsterStats stats) {
        MapleMap map = mock(MapleMap.class);
        Character chr = Mocks.chr("testPlayer");
        Client client = mock(Client.class);

        Monster monster = spy(new Monster(3210800, stats));
        monster.setMap(map);

        when(map.getAllPlayers()).thenReturn(List.of(chr));
        when(chr.isHidden()).thenReturn(false);
        when(chr.isAlive()).thenReturn(true);
        when(chr.getNumControlledMonsters()).thenReturn(0);
        when(chr.isLoggedinWorld()).thenReturn(true);
        when(chr.getMap()).thenReturn(map);
        when(chr.getClient()).thenReturn(client);

        doNothing().when(monster).aggroUpdatePuppetVisibility();

        return monster;
    }
}
