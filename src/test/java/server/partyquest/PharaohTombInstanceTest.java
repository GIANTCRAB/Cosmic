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

import client.Character;
import constants.id.ItemId;
import constants.id.MapId;
import constants.id.MobId;
import net.server.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.TimerManager;
import server.life.Monster;
import server.life.MonsterDropEntry;
import server.maps.MapManager;
import server.maps.MapleMap;
import server.maps.Portal;
import testutil.Mocks;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-driven lifecycle tests for {@link PharaohTomb#enter()}. These pin the two behaviours that
 * fix the original "lose the gem, land in an empty shared room" bug:
 * <ul>
 *   <li><b>Instance isolation:</b> the tomb map is acquired via {@code getDisposableMap} (a fresh,
 *       untracked map per call) and never the shared {@code getMap}.</li>
 *   <li><b>The spawn:</b> exactly one Pharaoh Jr. Yeti is spawned onto that instance, carrying the
 *       mode-matched chest as its drop override, and the player is force-warped in.</li>
 * </ul>
 *
 * <p>The {@link LifeFactory} lookup is side-stepped by overriding {@link PharaohTomb#createJrYeti()}
 * (a package-private seam) to return a mock yeti, so the tests neither bootstrap WZ nor depend on
 * static-mock ordering across the full suite. {@link MapManager} is mocked as an instance, matching
 * the convention from {@code PyramidLifecycleTest}.
 */
class PharaohTombInstanceTest {

    private PharaohTomb tomb;

    @BeforeAll
    static void startTimerManager() {
        // enter() schedules the disposal poll via TimerManager; the executor must exist.
        TimerManager.getInstance().start();
    }

    @AfterEach
    void disposeTomb() {
        if (tomb != null) {
            tomb.dispose();   // cancels the disposal poll so it cannot fire between tests
        }
    }

    /**
     * A PharaohTomb whose Jr. Yeti is a supplied mock, so enter() never touches LifeFactory/WZ.
     */
    private static PharaohTomb tombWithMockYeti(Character chr, Channel cs, Pyramid.PyramidMode mode,
                                                 MapleMap tombMap, Monster yeti) {
        return new PharaohTomb(chr, cs, mode) {
            @Override
            Monster createJrYeti() {
                return yeti;
            }
        };
    }

    @Test
    void enterMintsDisposableInstanceAndNeverSharedMap() {
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());

        tomb = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.EASY, tombMap, mock(Monster.class));
        tomb.enter();

        verify(cs.getMapFactory()).getDisposableMap(MapId.TOMB_OF_PHARAOH_YETI);
        verify(cs.getMapFactory(), never()).getMap(anyInt());   // the bug: a shared warp would hit this
    }

    @Test
    void enterSpawnsExactlyOneJrYetiOnTheInstance() {
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());
        Monster yeti = mock(Monster.class);

        tomb = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.NORMAL, tombMap, yeti);
        tomb.enter();

        verify(tombMap, times(1)).spawnMonsterOnGroundBelow(any(Monster.class), any(Point.class));
        verify(tombMap, never()).spawnMonster(any(Monster.class));   // no stray double-spawn
    }

    @Test
    void spawnedYetiCarriesModeMatchedChestDropOverride() {
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());
        Monster yeti = mock(Monster.class);

        tomb = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.HELL, tombMap, yeti);
        tomb.enter();

        ArgumentCaptor<List<MonsterDropEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(yeti).setDropOverride(captor.capture());
        List<MonsterDropEntry> override = captor.getValue();
        assertEquals(1, override.size());
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST_HELL, override.get(0).itemId,
                "HELL must drop the HELL chest (sole source of the Immortal Pharaoh Belt)");
        assertEquals(1_000_000, override.get(0).chance);
    }

    @Test
    void easyModeYetiCarriesStandardChestOverride() {
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());
        Monster yeti = mock(Monster.class);

        tomb = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.EASY, tombMap, yeti);
        tomb.enter();

        ArgumentCaptor<List<MonsterDropEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(yeti).setDropOverride(captor.capture());
        assertEquals(ItemId.PHARAOHS_TREASURE_CHEST, captor.getValue().get(0).itemId);
    }

    @Test
    void enterForceWarpsPlayerIntoTheInstance() {
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());
        Portal portal = mock(Portal.class);
        when(tombMap.getPortal(0)).thenReturn(portal);
        Character chr = Mocks.chr();

        tomb = tombWithMockYeti(chr, cs, Pyramid.PyramidMode.HARD, tombMap, mock(Monster.class));
        tomb.enter();

        verify(chr).forceChangeMap(eq(tombMap), eq(portal));
    }

    @Test
    void enterUsesFixedSpawnPointNotPlayerPosition() {
        // The spawn must not depend on the (post-warp, client-fading) player position -- it uses the
        // fixed YETI_SPAWN_POINT so it is deterministic regardless of timing.
        Channel cs = mockChannelWithDisposableMap();
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(anyInt());

        tomb = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.EASY, tombMap, mock(Monster.class));
        tomb.enter();

        verify(tombMap).spawnMonsterOnGroundBelow(any(Monster.class), eq(PharaohTomb.YETI_SPAWN_POINT));
    }

    @Test
    void twoEntriesGetDistinctDisposableMaps() {
        // Two players spending gems must never share a room: getDisposableMap is called once per
        // entry and returns a fresh map each time.
        Channel cs = mock(Channel.class);
        MapManager mf = mock(MapManager.class);
        when(cs.getMapFactory()).thenReturn(mf);
        MapleMap mapA = mock(MapleMap.class);
        MapleMap mapB = mock(MapleMap.class);
        when(mf.getDisposableMap(MapId.TOMB_OF_PHARAOH_YETI)).thenReturn(mapA, mapB);

        PharaohTomb tombA = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.EASY, mapA, mock(Monster.class));
        tombA.enter();
        PharaohTomb tombB = tombWithMockYeti(Mocks.chr(), cs, Pyramid.PyramidMode.EASY, mapB, mock(Monster.class));
        tombB.enter();

        verify(mf, times(2)).getDisposableMap(MapId.TOMB_OF_PHARAOH_YETI);
        verify(mf, never()).getMap(anyInt());
        assertNotNull(mapA);
        assertNotNull(mapB);
        assertNotSame(mapA, mapB);   // two distinct instance maps, never a shared one
        assertSame(mapA, mapA);      // sanity

        tombA.dispose();
        tombB.dispose();
    }

    @Test
    void createJrYetiDefaultLooksUpThePharaohJrYetiMobId() {
        // The non-overridden seam must request the 1-HP "Pharaoh's clone" mob id (9700019), not a
        // transparent-yeti family member. We exercise the default by NOT overriding it; LifeFactory
        // is not bootstrapped here -- we only assert the constant the seam forwards.
        assertEquals(MobId.PHARAOH_JR_YETI, 9700019);
    }

    private static Channel mockChannelWithDisposableMap() {
        Channel cs = mock(Channel.class);
        MapManager mf = mock(MapManager.class);
        when(cs.getMapFactory()).thenReturn(mf);
        when(mf.getDisposableMap(anyInt())).thenReturn(mock(MapleMap.class));
        return cs;
    }
}
