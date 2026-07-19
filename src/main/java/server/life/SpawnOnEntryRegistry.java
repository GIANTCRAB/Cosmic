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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static catalogue of bosses that should spawn <em>once on player map entry</em>
 * rather than on a periodic respawn timer. Consulted by {@code MapleMap.addPlayer}
 * immediately after the WZ-driven {@code onUserEnter} map script (if any) runs.
 *
 * <p><b>Contrast with {@link AreaBossRegistry}</b>: that registry drives the
 * periodic {@code AreaBossTask} for bosses whose design is "always present,
 * reappears on a timer after being killed" (Mano, Stumpy, Manon, etc.). This
 * registry is for bosses whose design is "appears when a player walks in,
 * does not respawn on its own" — typically quest bosses. The six Door bosses
 * of the Astaroth questline (Marbas, Amdusias, Andras, Crocell, Valefor,
 * Astaroth) are the canonical example: a player on the quest walks in, the
 * boss spawns, the player kills it for quest credit. If the boss is already
 * on the map (another player is fighting it, or a previous spawn persists),
 * the entry is skipped.
 *
 * <p>Replaces the original {@code scripts/map/onUserEnter/67700000X.js}
 * scripts, which had the same spawn-if-absent semantics but emitted a private
 * {@code player.message}. This Java implementation emits a map broadcast via
 * {@code PacketCreator.serverNotice(6, ...)} for consistency with
 * {@code AreaBossTask}'s spawn notices.
 *
 * <p>The data shape is identical to {@link AreaBossSpawn} — only the
 * <em>timing model</em> differs — so this registry reuses that record type
 * rather than duplicating the definition.
 */
public record SpawnOnEntryRegistry(List<AreaBossSpawn> spawns) {

    private static final List<AreaBossSpawn> DEFAULT_SPAWNS = List.of(
            new AreaBossSpawn(677000001, 9400612, 461, 61, "Marbas has appeared!"),
            new AreaBossSpawn(677000003, 9400610, 467, 0, "Amdusias has appeared!"),
            new AreaBossSpawn(677000005, 9400609, 201, 80, "Andras has appeared!"),
            new AreaBossSpawn(677000007, 9400611, 171, 50, "Crocell has appeared!"),
            new AreaBossSpawn(677000009, 9400613, 251, -841, "Valefor has appeared!"),
            new AreaBossSpawn(677000012, 9400633, 842, 0, "Astaroth has appeared!")
    );

    private static final SpawnOnEntryRegistry DEFAULT = new SpawnOnEntryRegistry(DEFAULT_SPAWNS);

    public SpawnOnEntryRegistry {
        spawns = List.copyOf(spawns);
    }

    public static SpawnOnEntryRegistry getDefault() {
        return DEFAULT;
    }

    /**
     * @param mapId the map a player just entered
     * @return the spawn-on-entry boss for that map, or {@code null} if no
     *         spawn-on-entry boss is registered for it. The contract is one
     *         boss per map; if multiple entries ever share a map id, the
     *         first match wins.
     */
    public AreaBossSpawn forMap(int mapId) {
        for (AreaBossSpawn s : spawns) {
            if (s.mapId() == mapId) {
                return s;
            }
        }
        return null;
    }
}
