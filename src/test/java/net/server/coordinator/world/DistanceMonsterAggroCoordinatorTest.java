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

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DistanceMonsterAggroCoordinator}.
 * <p>
 * Exercises the distance-specific behavior: damage scaling by proximity
 * (melee vs max range vs mid range), proximity-aware decay (frozen while in
 * melee reach, decaying while far), and the shared lifecycle/parity paths
 * (leadership election, removal, disposal, puppet bookkeeping). All tuning
 * values are injected via the package-private constructor so tests stay
 * independent of {@code config.yaml}.
 */
class DistanceMonsterAggroCoordinatorTest {

    private static final long MELEE_SQ = 400000L;
    private static final long MAX_SQ = 722500L;
    private static final double HI = 2.0;
    private static final double LO = 0.1;
    private static final long INTERVAL = 5000L;
    private static final int DECAY_TICKS_TO_EXPIRE = 30;   // single instance expires after 24 ticks

    @Test
    void singleAttackerBecomesLeader() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chrAt(1, map, new Point(100, 0));   // melee
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1),
                "A single attacker should be elected as the aggro leader");
    }

    @Test
    void meleeBeatsRangedOnEqualRawDamage() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character melee = chrAt(1, map, new Point(100, 0));    // distSq 10000 -> 2.0x
        Character ranged = chrAt(2, map, new Point(850, 0));   // distSq 722500 -> 0.1x
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 2, 1000);   // added first, but only ~100 effective aggro
        coordinator.addAggroDamage(mob, 1, 1000);   // ~2000 effective aggro
        coordinator.runSortLeadingCharactersAggro();

        assertTrue(coordinator.isLeadingCharacterAggro(mob, melee),
                "Equal raw damage at melee range should outweigh max-range damage");
        assertFalse(coordinator.isLeadingCharacterAggro(mob, ranged),
                "Max-range damage should not hold leadership against a melee attacker");
    }

    @Test
    void meleeMidAndMaxRankByEffectiveAggro() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character melee = chrAt(1, map, new Point(100, 0));     // 2.0x
        Character mid = chrAt(2, map, new Point(749, 0));       // ~midpoint -> ~1.05x
        Character max = chrAt(3, map, new Point(850, 0));       // 0.1x
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 3, 1000);
        coordinator.addAggroDamage(mob, 2, 1000);
        coordinator.addAggroDamage(mob, 1, 1000);
        coordinator.runSortLeadingCharactersAggro();

        assertTrue(coordinator.isLeadingCharacterAggro(mob, melee),
                "Melee-range attacker should rank highest on equal raw damage");
        assertFalse(coordinator.isLeadingCharacterAggro(mob, mid));
        assertFalse(coordinator.isLeadingCharacterAggro(mob, max));
    }

    @Test
    void leadershipFlipsWhenMeleeDamageOvertaken() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chrAt(1, map, new Point(100, 0));   // both melee: uniform 2.0x scaling
        Character p2 = chrAt(2, map, new Point(120, 0));
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 1, 100);
        coordinator.runSortLeadingCharactersAggro();
        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1));

        coordinator.addAggroDamage(mob, 2, 200);   // out-damages p1
        coordinator.runSortLeadingCharactersAggro();

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p2),
                "Leadership should flip to the higher effective-damage attacker");
        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1));
    }

    @Test
    void closePlayerHoldsAggroAcrossDecayTicksWithoutAttacking() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chrAt(1, map, new Point(100, 0));   // within melee reach
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 1, 1000);
        coordinator.runSortLeadingCharactersAggro();
        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1));

        for (int i = 0; i < DECAY_TICKS_TO_EXPIRE; i++) {
            coordinator.runAggroUpdate(1);   // well past the single-instance expiry window
        }

        assertTrue(coordinator.isLeadingCharacterAggro(mob, p1),
                "A player holding melee proximity should not bleed aggro while idle");
    }

    @Test
    void farPlayerAggroDecaysAwayWhenIdle() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character ranged = chrAt(2, map, new Point(850, 0));   // outside melee reach
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 2, 1000);
        coordinator.runSortLeadingCharactersAggro();
        assertTrue(coordinator.isLeadingCharacterAggro(mob, ranged));

        for (int i = 0; i < DECAY_TICKS_TO_EXPIRE; i++) {
            coordinator.runAggroUpdate(1);
        }

        assertFalse(coordinator.isLeadingCharacterAggro(mob, ranged),
                "A far player who stops attacking should lose aggro over time");
    }

    @Test
    void removeAggroEntries_clearsLeadership() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chrAt(1, map, new Point(100, 0));
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 1, 1000);
        coordinator.removeAggroEntries(mob);

        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1),
                "No player should lead a mob whose aggro entries were removed");
    }

    @Test
    void dispose_clearsAllEntries() {
        Monster mob = mobMock();
        MapleMap map = wireMap(mob);
        Character p1 = chrAt(1, map, new Point(100, 0));
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        coordinator.addAggroDamage(mob, 1, 1000);
        coordinator.dispose();

        assertFalse(coordinator.isLeadingCharacterAggro(mob, p1),
                "No player should lead a disposed coordinator's mob");
    }

    @Test
    void puppetAggro_addRemoveListRoundTrip() {
        DistanceMonsterAggroCoordinator coordinator = newCoordinator();

        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(7);

        coordinator.addPuppetAggro(owner);
        assertTrue(coordinator.getPuppetAggroList().contains(7),
                "Puppet owner id should be present after add");

        coordinator.removePuppetAggro(7);
        assertFalse(coordinator.getPuppetAggroList().contains(7),
                "Puppet owner id should be absent after remove");
    }

    private static DistanceMonsterAggroCoordinator newCoordinator() {
        return new DistanceMonsterAggroCoordinator(MELEE_SQ, MAX_SQ, HI, LO, INTERVAL);
    }

    /**
     * A {@link Monster} mock stubbed alive at the origin with no puppet in
     * vicinity. Reused across a test since it serves as a {@code HashMap} key.
     */
    private static Monster mobMock() {
        Monster mob = mock(Monster.class);
        when(mob.isAlive()).thenReturn(true);
        when(mob.isBoss()).thenReturn(false);
        when(mob.getPosition()).thenReturn(new Point(0, 0));
        return mob;
    }

    private static MapleMap wireMap(Monster mob) {
        MapleMap map = mock(MapleMap.class);
        when(mob.getMap()).thenReturn(map);
        return map;
    }

    private static Character chrAt(int cid, MapleMap map, Point pos) {
        Character chr = mock(Character.class);
        when(chr.getId()).thenReturn(cid);
        when(chr.isAlive()).thenReturn(true);
        when(chr.getPosition()).thenReturn(pos);
        when(map.getCharacterById(cid)).thenReturn(chr);
        return chr;
    }
}
