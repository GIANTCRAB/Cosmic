/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.inventory;

import config.YamlConfig;

/**
 * Resolves the split between an equipment's <b>true</b> level (the server-internal value, which may
 * rise past the legacy cap via {@code USE_EQUIPMNT_LVLUP}) and its <b>network</b> level (the value
 * serialized to the v83 client, whose WZ data only defines per-level info up to {@value #NETWORK_MAX_LEVEL}).
 * <p>
 * Sending a level above {@value #NETWORK_MAX_LEVEL} to the client is unsafe (its WZ tables end there),
 * and indexing the GMS EXP table past its length used to throw {@code ArrayIndexOutOfBoundsException}.
 * All such access is now routed through this class so that:
 * <ul>
 *     <li>the client only ever receives a network level (clamped to {@value #NETWORK_MAX_LEVEL}); and</li>
 *     <li>true EXP lookups for levels above the table fall back to an exponential curve instead of a
 *         fixed-length array, so the ceiling is not hardwired to a particular level.</li>
 * </ul>
 */
public final class EquipmentLevelModel {
    private EquipmentLevelModel() {
    }

    /**
     * Highest level the client can display. The v83 client's Character.wz only carries per-level
     * equipment info up to this value, so the network level must never exceed it.
     */
    public static final int NETWORK_MAX_LEVEL = 30;

    /**
     * GMS equipment EXP table for levels 1..{@value #NETWORK_MAX_LEVEL} (index == level).
     * Levels beyond this range are resolved by {@link #expNeededForTrueLevel(int)} via the curve.
     */
    private static final int[] BASE_EXP = {
            1, 15, 19, 23, 35, 43, 98, 188, 237, 280, 304, 331, 571, 656, 840,
            1060, 1193, 1467, 1784, 1976, 2357, 2791, 3052, 3560, 4128, 4469,
            5123, 5844, 6276, 7093, 10000
    };

    /** Per-level multiplier of the EXP curve once the GMS table runs out. Tunable. */
    private static final double CURVE_GROWTH = 1.15;
    /** EXP anchor the curve grows from; equals the last GMS table entry (level {@value #NETWORK_MAX_LEVEL}). */
    private static final double CURVE_BASE = BASE_EXP[NETWORK_MAX_LEVEL];

    /**
     * True ceiling for equipment leveling, driven by {@code USE_EQUIPMNT_LVLUP}. Unlike the previous
     * implementation this is no longer silently clamped to {@value #NETWORK_MAX_LEVEL}.
     */
    public static int trueMaxLevel() {
        return trueMaxLevel(YamlConfig.config.server.USE_EQUIPMNT_LVLUP);
    }

    static int trueMaxLevel(int configMax) {
        return Math.max(1, configMax);
    }

    /**
     * The "fake" level to send to the client: the true level clamped to the client's display range.
     */
    public static int networkLevelOf(int trueLevel) {
        return Math.min(Math.max(trueLevel, 1), NETWORK_MAX_LEVEL);
    }

    public static boolean isAtNetworkCap(int trueLevel) {
        return trueLevel >= NETWORK_MAX_LEVEL;
    }

    public static boolean isAtTrueMax(int trueLevel) {
        return trueLevel >= trueMaxLevel();
    }

    /**
     * EXP required to advance from {@code trueLevel} to the next level. Uses the GMS table for
     * levels below {@value #NETWORK_MAX_LEVEL} and an exponential curve from there on, so it is
     * defined for any level rather than bounded by the table length.
     */
    public static int expNeededForTrueLevel(int trueLevel) {
        if (trueLevel < NETWORK_MAX_LEVEL) {
            return BASE_EXP[Math.max(trueLevel, 0)];
        }
        long value = Math.round(CURVE_BASE * Math.pow(CURVE_GROWTH, trueLevel - NETWORK_MAX_LEVEL));
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    /**
     * EXP divisor used solely for the client EXP-bar display. Always a table lookup, guaranteed in range.
     */
    public static int expNeededForNetworkLevel(int networkLevel) {
        int idx = Math.min(Math.max(networkLevel, 1), NETWORK_MAX_LEVEL);
        return BASE_EXP[idx];
    }
}
