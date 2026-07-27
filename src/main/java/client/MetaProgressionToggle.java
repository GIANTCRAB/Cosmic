/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory, server-lifetime registry of per-character toggles that control whether the
 * account catchup ("meta progression") stat buffs are applied. Because the toggle is held
 * here (keyed by character id) rather than on the {@link Character} object itself, it
 * survives channel/map/instance transitions: a channel change reloads the Character from
 * the database, but the freshly loaded instance re-fetches the same shared AtomicBoolean
 * from this registry, so the player's choice is preserved. State is not persisted to the
 * database and is lost on a server restart.
 */
public final class MetaProgressionToggle {

    private static final ConcurrentHashMap<Integer, AtomicBoolean> TOGGLES = new ConcurrentHashMap<>();

    private MetaProgressionToggle() {
    }

    /**
     * Returns the shared toggle for the given character, defaulting to {@code true}
     * (buffs enabled) the first time the character is seen.
     */
    public static AtomicBoolean forCharacter(int characterId) {
        return TOGGLES.computeIfAbsent(characterId, id -> new AtomicBoolean(true));
    }

    /**
     * Returns whether the meta progression catchup buffs are currently enabled for the
     * given character.
     */
    public static boolean isEnabled(int characterId) {
        return forCharacter(characterId).get();
    }

    /**
     * Atomically flips the toggle and returns the new state ({@code true} = enabled).
     */
    public static boolean toggle(int characterId) {
        AtomicBoolean flag = forCharacter(characterId);
        boolean previous;
        do {
            previous = flag.get();
        } while (!flag.compareAndSet(previous, !previous));
        return !previous;
    }

    /**
     * Drops the toggle entry for a character, returning it to the default on next access.
     */
    public static void clear(int characterId) {
        TOGGLES.remove(characterId);
    }

    static void clearAll() {
        TOGGLES.clear();
    }
}
