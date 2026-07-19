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
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the maximum stack size (a.k.a. {@code slotMax}) for an item, optionally overriding the
 * value loaded from the WZ data with a server-configured global ceiling.
 * <p>
 * The override is opt-in via two server flags:
 * <ul>
 *     <li>{@code USE_FORCE_ITEM_STACK_OVERRIDE} &mdash; when true, ignore the WZ {@code slotMax}
 *     for stackable items and use {@code MAX_ITEM_STACK_OVERRIDE} instead;</li>
 *     <li>{@code MAX_ITEM_STACK_OVERRIDE} &mdash; the forced ceiling value, clamped to
 *     {@code [1, Short.MAX_VALUE]} to fit {@link Item}'s {@code short} quantity field and the
 *     v83 wire format.</li>
 * </ul>
 * <p>
 * Certain items <b>must never</b> be forced to stack regardless of the override, because doing so
 * breaks core game mechanics. These are identified by {@link #isNeverStackable(int)} and are
 * always passed through with their original WZ value:
 * <ul>
 *     <li><b>Equipment</b> ({@link ItemConstants#isEquipment(int)}) &mdash; each piece carries
 *     unique stats, slots, and scroll history;</li>
 *     <li><b>Pets</b> ({@link ItemConstants#isPet(int)}) &mdash; each carries a unique
 *     {@code petid} and a live {@link Pet} entity;</li>
 *     <li><b>Rechargeables</b> ({@link ItemConstants#isRechargeable(int)}: throwing stars and
 *     bullets) &mdash; per-slot recharge economy scaled by mastery skill level.</li>
 * </ul>
 * <p>
 * The resolver is a pure function of {@code (itemId, wzSlotMax, config)}; only the thin
 * {@link #configFromYaml()} adapter touches {@link YamlConfig}, mirroring the
 * {@link EquipmentLevelModel} pattern so the override logic is unit-testable in isolation.
 */
public final class ItemStackModel {
    private ItemStackModel() {
    }

    private static final Logger log = LoggerFactory.getLogger(ItemStackModel.class);

    /**
     * Immutable snapshot of the two server-config flags driving the override.
     *
     * @param forceOverride when true, stackable items use {@link #maxStack()} instead of WZ
     * @param maxStack      the forced ceiling value (clamped to {@code [1, Short.MAX_VALUE]}
     *                      by {@link #clampMaxStack(int)} before use)
     */
    public record ItemStackOverrideConfig(boolean forceOverride, int maxStack) {
    }

    /**
     * Returns {@code true} for items whose stack size must never be overridden, because forcing
     * them to stack breaks the game. The set is intentionally closed: equipment, pets, and
     * rechargeables (throwing stars + bullets).
     */
    public static boolean isNeverStackable(int itemId) {
        return ItemConstants.isEquipment(itemId)
                || ItemConstants.isPet(itemId)
                || ItemConstants.isRechargeable(itemId);
    }

    /**
     * Clamps a candidate max-stack value to the valid range for {@link Item#getQuantity()}
     * (a {@code short}) and the v83 wire format: {@code [1, Short.MAX_VALUE]}.
     */
    public static int clampMaxStack(int requested) {
        return Math.clamp(requested, 1, Short.MAX_VALUE);
    }

    /**
     * Pure resolver. Returns the max stack size to use for the given item.
     * <ul>
     *     <li>If {@code config.forceOverride()} is true AND the item is not
     *     {@link #isNeverStackable(int) never-stackable}, returns
     *     {@link #clampMaxStack(int)} applied to {@code config.maxStack()}.</li>
     *     <li>Otherwise returns {@code wzSlotMax} unchanged.</li>
     * </ul>
     *
     * @param itemId    the item id (used only to classify never-stackable items)
     * @param wzSlotMax the value loaded from WZ (or the implicit {@code 1}/{@code 100} default)
     * @param config    the active override configuration
     * @return the effective max stack size
     */
    public static int resolveMaxStack(int itemId, int wzSlotMax, ItemStackOverrideConfig config) {
        if (config.forceOverride() && !isNeverStackable(itemId)) {
            return clampMaxStack(config.maxStack());
        }
        return wzSlotMax;
    }

    /**
     * Reads the current override configuration from {@link YamlConfig}. When force is enabled
     * but the configured max is non-positive, the value is clamped to {@code 1} and a warning
     * is logged &mdash; the server still boots.
     */
    public static ItemStackOverrideConfig configFromYaml() {
        boolean force = YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE;
        int max = YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE;
        if (force && max <= 0) {
            log.warn("MAX_ITEM_STACK_OVERRIDE={} with USE_FORCE_ITEM_STACK_OVERRIDE=true; clamping to 1.", max);
            max = 1;
        }
        return new ItemStackOverrideConfig(force, max);
    }
}
