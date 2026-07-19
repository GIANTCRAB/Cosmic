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

/**
 * Immutable description of a single area-boss spawn: the map and mob it targets,
 * the fixed ground position to spawn at, and the broadcast notice sent on spawn.
 *
 * <p>Historically each area boss had its own {@code scripts/event/AreaBoss*.js}
 * event script with these values inlined; this record captures them as data so
 * the spawn loop can live in Java and be unit-tested.
 */
public record AreaBossSpawn(int mapId, int mobId, int x, int y, String message) {

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
    }
}
