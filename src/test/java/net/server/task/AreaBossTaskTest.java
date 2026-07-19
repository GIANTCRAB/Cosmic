/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.task;

import net.server.Server;
import net.server.channel.Channel;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.life.AreaBossRegistry;
import server.life.AreaBossSpawn;
import server.life.LifeFactory;
import server.life.Monster;
import server.maps.MapManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AreaBossTask}. The decision logic ({@code shouldSpawn})
 * is exercised directly, and the full {@link AreaBossTask#run()} loop is
 * verified with {@code Server.getInstance()} and {@code LifeFactory.getMonster}
 * stubbed via {@link MockedStatic}.
 */
class AreaBossTaskTest {

    private static final AreaBossSpawn TEST_SPAWN =
            new AreaBossSpawn(104000400, 2220000, 279, -496, "A cool breeze was felt when Mano appeared.");

    @Test
    void shouldSpawnReturnsTrueWhenBossAbsentFromMap() {
        MapleMap map = mock(MapleMap.class);
        when(map.getMonsterById(TEST_SPAWN.mobId())).thenReturn(null);

        assertTrue(AreaBossTask.shouldSpawn(map, TEST_SPAWN));
    }

    @Test
    void shouldSpawnReturnsFalseWhenBossAlreadyOnMap() {
        MapleMap map = mock(MapleMap.class);
        when(map.getMonsterById(TEST_SPAWN.mobId())).thenReturn(mock(Monster.class));

        assertFalse(AreaBossTask.shouldSpawn(map, TEST_SPAWN));
    }

    @Test
    void shouldSpawnReturnsFalseWhenMapIsNull() {
        assertFalse(AreaBossTask.shouldSpawn(null, TEST_SPAWN));
    }

    @Test
    void runSpawnsAbsentBossAndBroadcastsNotice() {
        MapleMap map = mock(MapleMap.class);
        when(map.getMonsterById(TEST_SPAWN.mobId())).thenReturn(null);
        Channel ch = channelFor(TEST_SPAWN.mapId(), map);

        AreaBossRegistry registry = singleEntryRegistry(TEST_SPAWN);

        try (MockedStatic<Server> server = mockStatic(Server.class);
             MockedStatic<LifeFactory> lifeFactory = mockStatic(LifeFactory.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            when(Server.getInstance().getAllChannels()).thenReturn(List.of(ch));
            Monster mob = mock(Monster.class);
            lifeFactory.when(() -> LifeFactory.getMonster(TEST_SPAWN.mobId())).thenReturn(mob);

            new AreaBossTask(registry).run();
        }

        verify(map).spawnMonsterOnGroundBelow(any(Monster.class), any(Point.class));
        verify(map).broadcastMessage(any());
    }

    @Test
    void runSkipsSpawningWhenBossAlreadyPresent() {
        MapleMap map = mock(MapleMap.class);
        when(map.getMonsterById(TEST_SPAWN.mobId())).thenReturn(mock(Monster.class));
        Channel ch = channelFor(TEST_SPAWN.mapId(), map);

        AreaBossRegistry registry = singleEntryRegistry(TEST_SPAWN);

        try (MockedStatic<Server> server = mockStatic(Server.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            when(Server.getInstance().getAllChannels()).thenReturn(List.of(ch));

            new AreaBossTask(registry).run();
        }

        verify(map, never()).spawnMonsterOnGroundBelow(any(), any());
        verify(map, never()).broadcastMessage(any());
    }

    @Test
    void runIteratesEverySpawnOnEveryChannel() {
        AreaBossSpawn a = new AreaBossSpawn(104000400, 2220000, 279, -496, "Mano");
        AreaBossSpawn b = new AreaBossSpawn(110040000, 5220001, -400, 140, "King Clang");

        MapleMap mapA = mock(MapleMap.class);
        MapleMap mapB = mock(MapleMap.class);
        when(mapA.getMonsterById(a.mobId())).thenReturn(null);
        when(mapB.getMonsterById(b.mobId())).thenReturn(null);

        Channel ch1 = channelWithMaps(a.mapId(), mapA, b.mapId(), mapB);
        Channel ch2 = channelWithMaps(a.mapId(), mapA, b.mapId(), mapB);

        AreaBossRegistry registry = new AreaBossRegistry(List.of(a, b));

        try (MockedStatic<Server> server = mockStatic(Server.class);
             MockedStatic<LifeFactory> lifeFactory = mockStatic(LifeFactory.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            when(Server.getInstance().getAllChannels()).thenReturn(List.of(ch1, ch2));
            lifeFactory.when(() -> LifeFactory.getMonster(anyInt())).thenReturn(mock(Monster.class));

            new AreaBossTask(registry).run();
        }

        // 2 spawns x 2 channels = 4 spawn attempts, all absent -> all spawn.
        verify(mapA, times(2)).spawnMonsterOnGroundBelow(any(), any());
        verify(mapB, times(2)).spawnMonsterOnGroundBelow(any(), any());
    }

    @Test
    void runSkipsChannelWithNullMapFactoryWithoutThrowing() {
        Channel broken = mock(Channel.class);
        when(broken.getMapFactory()).thenReturn(null);

        try (MockedStatic<Server> server = mockStatic(Server.class)) {
            server.when(Server::getInstance).thenReturn(mock(Server.class));
            when(Server.getInstance().getAllChannels()).thenReturn(List.of(broken));

            new AreaBossTask(singleEntryRegistry(TEST_SPAWN)).run();
            // No exception thrown is the assertion; null map factory must be tolerated.
        }
    }

    private static Channel channelFor(int mapId, MapleMap map) {
        Channel ch = mock(Channel.class);
        MapManager mf = mock(MapManager.class);
        when(ch.getMapFactory()).thenReturn(mf);
        when(mf.getMap(mapId)).thenReturn(map);
        return ch;
    }

    private static Channel channelWithMaps(int mapIdA, MapleMap mapA, int mapIdB, MapleMap mapB) {
        Channel ch = mock(Channel.class);
        MapManager mf = mock(MapManager.class);
        when(ch.getMapFactory()).thenReturn(mf);
        when(mf.getMap(mapIdA)).thenReturn(mapA);
        when(mf.getMap(mapIdB)).thenReturn(mapB);
        return ch;
    }

    private static AreaBossRegistry singleEntryRegistry(AreaBossSpawn spawn) {
        return new AreaBossRegistry(List.of(spawn));
    }
}
