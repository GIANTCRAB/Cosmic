/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.coordinator.world;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultMonsterAggroCoordinator}.
 * <p>
 * Covers the pure aggro-resolution logic: damage accumulation, leader
 * election (which depends on {@link DefaultMonsterAggroCoordinator#runSortLeadingCharactersAggro()}),
 * entry removal, puppet bookkeeping, and disposal. The lifecycle methods that
 * drive the background {@code TimerManager} task ({@code start/stopAggroCoordinator})
 * are intentionally not exercised here.
 * <p>
 * Note: {@code addAggroDamage} only <em>appends</em> entries to an internal
 * list; sorting into damage order happens exclusively in
 * {@code runSortLeadingCharactersAggro}. The "quasi-sorted" leader query
 * therefore requires an explicit sort call before asserting damage-based
 * leadership.
 */
class DefaultMonsterAggroCoordinatorTest {

    @Test
    void addAggroDamage_singleAttackerBecomesLeader() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);
        Character p1 = chr(1, map);

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1),
                "A single attacker should be elected as the aggro leader");
    }

    @Test
    void higherDamageWinsLeadershipAfterSort() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chr(1, map);
        Character p2 = chr(2, map);
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        coordinator.addAggroDamage(mob, 2, 50);   // added first
        coordinator.addAggroDamage(mob, 1, 100);  // higher damage, added second
        coordinator.runSortLeadingCharactersAggro();

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1),
                "The higher-damage attacker should hold leadership after sorting");
        assertFalse(coordinator.isLeadingCharacterAggro(mob, p2),
                "The lower-damage attacker should not hold leadership");
    }

    @Test
    void leadershipFlipsWhenDamageOvertaken() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chr(1, map);
        Character p2 = chr(2, map);
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);
        coordinator.runSortLeadingCharactersAggro();
        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1));

        // second attacker now out-damages the first
        coordinator.addAggroDamage(mob, 2, 200);
        coordinator.runSortLeadingCharactersAggro();

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p2),
                "Leadership should flip to the attacker with higher accumulated damage");
        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1));
    }

    @Test
    void removeAggroEntries_clearsLeadership() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chr(1, map);
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);
        coordinator.removeAggroEntries(mob);

        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1),
                "No player should lead a mob whose aggro entries were removed");
    }

    @Test
    void puppetAggro_addRemoveListRoundTrip() {
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(7);

        coordinator.addPuppetAggro(owner);
        assertTrue(coordinator.getPuppetAggroList().contains(7),
                "Puppet owner id should be present after add");

        coordinator.removePuppetAggro(7);
        assertFalse(coordinator.getPuppetAggroList().contains(7),
                "Puppet owner id should be absent after remove");
    }

    @Test
    void dispose_clearsAllEntries() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chr(1, map);
        DefaultMonsterAggroCoordinator coordinator = new DefaultMonsterAggroCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);
        coordinator.dispose();

        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1),
                "No player should lead a disposed coordinator's mob");
    }

    /**
     * A {@link Monster} mock stubbed as alive with no puppet in vicinity.
     * The same instance must be reused across a test since it serves as a
     * {@code HashMap} key in the coordinator.
     */
    private static Monster mobMock() {
        Monster mob = mock(Monster.class);
        when(mob.isAlive()).thenReturn(true);
        return mob;
    }

    /**
     * Creates a single {@link MapleMap} mock wired as {@code mob}'s map, so
     * every {@link Character} resolved by id shares the same lookup. Using one
     * shared map is required because re-stubbing {@code mob.getMap()} per
     * character would shadow earlier lookups.
     */
    private static MapleMap wireMap(Monster mob) {
        MapleMap map = mock(MapleMap.class);
        when(mob.getMap()).thenReturn(map);
        return map;
    }

    /**
     * A {@link Character} mock whose {@code getId} resolves to {@code cid} and
     * whose lookup is registered on the shared {@code map}.
     */
    private static Character chr(int cid, MapleMap map) {
        Character chr = mock(Character.class);
        when(chr.getId()).thenReturn(cid);
        when(chr.isAlive()).thenReturn(true);
        when(map.getCharacterById(cid)).thenReturn(chr);
        return chr;
    }
}
