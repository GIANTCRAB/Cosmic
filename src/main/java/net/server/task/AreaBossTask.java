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
import server.life.AreaBossRegistry;
import server.life.AreaBossSpawn;
import server.life.LifeFactory;
import server.life.Monster;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.Point;
import java.util.List;

/**
 * Periodic task that ensures every registered area boss is present on its map.
 * On each tick, for every channel, for every {@link AreaBossSpawn}, if the boss
 * is not already on the map it is spawned at the configured position and a
 * server notice is broadcast. Otherwise the spawn is skipped.
 *
 * <p>The tick rate is controlled by {@code AREA_BOSS_RESPAWN_INTERVAL}, mirroring
 * how {@link RespawnTask} is driven by {@code RESPAWN_INTERVAL}. Replaces the
 * former per-boss {@code scripts/event/AreaBoss*.js} event scripts.
 */
public class AreaBossTask implements Runnable {

    private final AreaBossRegistry registry;

    public AreaBossTask() {
        this(new AreaBossRegistry());
    }

    public AreaBossTask(AreaBossRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run() {
        List<AreaBossSpawn> spawns = registry.spawns();
        for (Channel ch : Server.getInstance().getAllChannels()) {
            if (ch == null || ch.getMapFactory() == null) {
                continue;
            }
            for (AreaBossSpawn spawn : spawns) {
                MapleMap map = ch.getMapFactory().getMap(spawn.mapId());
                if (map == null) {
                    continue;
                }
                if (shouldSpawn(map, spawn)) {
                    spawnBoss(map, spawn);
                }
            }
        }
    }

    /**
     * Pure decision: spawn iff no monster with this spawn's mob id is currently
     * on the map. Extracted so it can be exercised by unit tests without any
     * {@link Server} / {@link Channel} coupling.
     */
    static boolean shouldSpawn(MapleMap map, AreaBossSpawn spawn) {
        return map != null && map.getMonsterById(spawn.mobId()) == null;
    }

    private static void spawnBoss(MapleMap map, AreaBossSpawn spawn) {
        Monster mob = LifeFactory.getMonster(spawn.mobId());
        if (mob == null) {
            return;
        }
        map.spawnMonsterOnGroundBelow(mob, new Point(spawn.x(), spawn.y()));
        map.broadcastMessage(PacketCreator.serverNotice(6, spawn.message()));
        // For weaken-family bosses, also revive any linked reactor that is
        // currently dead so players can re-trigger the weaken mechanic. Skips
        // itself when no reactor is linked or all linked reactors are alive.
        spawn.reactorId().ifPresent(map::resetDeadReactorsById);
    }
}
