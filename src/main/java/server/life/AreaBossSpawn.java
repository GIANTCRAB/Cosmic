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

import java.util.OptionalInt;

/**
 * Immutable description of a single area-boss spawn: the map and mob it targets,
 * the fixed ground position to spawn at, and the broadcast notice sent on spawn.
 *
 * <p>Historically each area boss had its own {@code scripts/event/AreaBoss*.js}
 * event script with these values inlined; this record captures them as data so
 * the spawn loop can live in Java and be unit-tested.
 *
 * <p>The optional {@link #reactorId()} links a weaken-family boss spawn (e.g.
 * Snow Witch) to the reactor whose completion removes that boss's
 * invulnerability. When {@code AreaBossTask} re-spawns the boss it also revives
 * any linked reactor that is currently dead, so players can re-trigger the
 * weaken mechanic without waiting out the reactor's full {@code reactorTime}
 * delay. Absent for spawns with no reactor mechanic.
 */
public record AreaBossSpawn(int mapId, int mobId, int x, int y, String message, OptionalInt reactorId) {

    public AreaBossSpawn {
        if (mapId <= 0) {
            throw new IllegalArgumentException("mapId must be positive: " + mapId);
        }
        if (mobId <= 0) {
            throw new IllegalArgumentException("mobId must be positive: " + mobId);
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be null or blank");
        }
        if (reactorId == null) {
            throw new IllegalArgumentException("reactorId must not be null (use OptionalInt.empty())");
        }
        if (reactorId.isPresent() && reactorId.getAsInt() <= 0) {
            throw new IllegalArgumentException("reactorId must be positive when present: " + reactorId);
        }
    }

    /**
     * Convenience constructor for spawns with no associated reactor (the common
     * case). Equivalent to passing {@link OptionalInt#empty()} for
     * {@code reactorId}.
     */
    public AreaBossSpawn(int mapId, int mobId, int x, int y, String message) {
        this(mapId, mobId, x, y, message, OptionalInt.empty());
    }

    /**
     * Factory for a weaken-family spawn linked to the reactor whose completion
     * removes the boss's invulnerability. Equivalent to passing
     * {@link OptionalInt#of(int) OptionalInt.of(reactorId)} to the canonical
     * constructor.
     */
    public static AreaBossSpawn of(int mapId, int mobId, int x, int y, String message, int reactorId) {
        return new AreaBossSpawn(mapId, mobId, x, y, message, OptionalInt.of(reactorId));
    }
}
