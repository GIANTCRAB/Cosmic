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

import java.util.Objects;
import java.util.Set;

/**
 * Pure decision helper consulted by {@code MapFactory} at SpawnPoint creation
 * time. When enabled and a monster id is in the overridable set, the
 * SpawnPoint is marked {@code denySpawn=true} immediately after its initial
 * spawn fires. This stops the normal {@code RespawnTask} cycle from
 * re-spawning that boss on its WZ-defined {@code mobTime}; instead
 * {@code AreaBossTask} becomes the sole respawner, driven by
 * {@code AREA_BOSS_RESPAWN_INTERVAL}.
 *
 * <p>The overridable id set is supplied by {@link AreaBossRegistry#overridableWzMobIds}
 * so there is a single source of truth for which bosses are under task control.
 *
 * <p>Stateless and side-effect free so the decision matrix can be exercised
 * directly in unit tests without touching WZ data, channels, or the server.
 */
public record WzBossSpawnOverride(boolean enabled, Set<Integer> overridableMobIds) {

    public WzBossSpawnOverride(boolean enabled, Set<Integer> overridableMobIds) {
        this.enabled = enabled;
        this.overridableMobIds = Set.copyOf(Objects.requireNonNull(overridableMobIds, "overridableMobIds"));
    }

    /**
     * @return {@code true} iff the override is enabled and the given monster id
     * is in the overridable set; in that case the caller should mark
     * the SpawnPoint as denied.
     */
    public boolean shouldDenySpawn(int mobId) {
        return enabled && overridableMobIds.contains(mobId);
    }
}
