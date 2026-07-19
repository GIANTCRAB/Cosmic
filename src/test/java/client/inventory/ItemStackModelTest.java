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
import constants.id.ItemId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for {@link ItemStackModel}. The resolver is exercised in isolation with
 * in-memory {@link ItemStackModel.ItemStackOverrideConfig} records; only the thin
 * {@link ItemStackModel#configFromYaml()} adapter touches {@link YamlConfig}, and it is
 * covered separately with snapshot/restore on the static fields.
 */
class ItemStackModelTest {

    private static final ItemStackModel.ItemStackOverrideConfig FORCE_OFF =
            new ItemStackModel.ItemStackOverrideConfig(false, 100);
    private static final ItemStackModel.ItemStackOverrideConfig FORCE_ON_500 =
            new ItemStackModel.ItemStackOverrideConfig(true, 500);

    // --- isNeverStackable ------------------------------------------------------

    @Nested
    class NeverStackable {
        @Test
        void equipmentIsNeverStackable() {
            assertTrue(ItemStackModel.isNeverStackable(ItemId.GLADIUS));
            assertTrue(ItemStackModel.isNeverStackable(ItemId.MITHRIL_WAND));
            assertTrue(ItemStackModel.isNeverStackable(ItemId.BLUE_WIZARD_ROBE));
        }

        @Test
        void petIsNeverStackable() {
            assertTrue(ItemStackModel.isNeverStackable(ItemId.PET_SNAIL));
        }

        @Test
        void throwingStarsAreNeverStackable() {
            assertTrue(ItemStackModel.isNeverStackable(ItemId.SUBI_THROWING_STARS));
            assertTrue(ItemStackModel.isNeverStackable(ItemId.HWABI_THROWING_STARS));
            assertTrue(ItemStackModel.isNeverStackable(ItemId.CRYSTAL_ILBI_THROWING_STARS));
        }

        @Test
        void bulletsAreNeverStackable() {
            assertTrue(ItemStackModel.isNeverStackable(ItemId.BULLET));
            assertTrue(ItemStackModel.isNeverStackable(ItemId.BLAZE_CAPSULE));
        }

        @Test
        void stackableItemsAreNotNeverStackable() {
            assertFalse(ItemStackModel.isNeverStackable(ItemId.WHITE_POTION));
            assertFalse(ItemStackModel.isNeverStackable(ItemId.MANA_ELIXIR));
            assertFalse(ItemStackModel.isNeverStackable(ItemId.SNAIL_SHELL));
            assertFalse(ItemStackModel.isNeverStackable(ItemId.BLUE_SNAIL_SHELL));
        }
    }

    // --- clampMaxStack ---------------------------------------------------------

    @Nested
    class ClampMaxStack {
        @ParameterizedTest
        @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
        void nonPositiveClampsToOne(int value) {
            assertEquals(1, ItemStackModel.clampMaxStack(value));
        }

        @Test
        void oneIsTheLowerBoundary() {
            assertEquals(1, ItemStackModel.clampMaxStack(1));
        }

        @Test
        void shortMaxIsTheUpperBoundary() {
            assertEquals(Short.MAX_VALUE, ItemStackModel.clampMaxStack(Short.MAX_VALUE));
        }

        @Test
        void aboveShortMaxClampsDown() {
            assertEquals(Short.MAX_VALUE, ItemStackModel.clampMaxStack(Short.MAX_VALUE + 1));
            assertEquals(Short.MAX_VALUE, ItemStackModel.clampMaxStack(Integer.MAX_VALUE));
        }

        @Test
        void midRangeValuePassesThrough() {
            assertEquals(500, ItemStackModel.clampMaxStack(500));
            assertEquals(32766, ItemStackModel.clampMaxStack(32766));
        }
    }

    // --- resolveMaxStack: override disabled ------------------------------------

    @Nested
    class ForceOverrideFalse {
        @Test
        void wzValueReturnedUnchangedForStackableItem() {
            assertEquals(100, ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, 100, FORCE_OFF));
        }

        @Test
        void wzValueReturnedUnchangedForNeverStackableItem() {
            assertEquals(1, ItemStackModel.resolveMaxStack(ItemId.GLADIUS, 1, FORCE_OFF));
        }

        @Test
        void weirdMaxStackIgnoredWhenForceFalse() {
            ItemStackModel.ItemStackOverrideConfig forceOffWithBadMax =
                    new ItemStackModel.ItemStackOverrideConfig(false, -999);
            assertEquals(100, ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, 100, forceOffWithBadMax));
        }
    }

    // --- resolveMaxStack: override enabled -------------------------------------

    @Nested
    class ForceOverrideTrue {
        @Test
        void stackableItemGetsOverrideInsteadOfWzValue() {
            assertEquals(500, ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, 100, FORCE_ON_500));
        }

        @Test
        void overrideReplacesImplicitNonEquipDefault() {
            // Items lacking a WZ slotMax node get the implicit 100 fallback; override replaces it.
            assertEquals(500, ItemStackModel.resolveMaxStack(ItemId.SNAIL_SHELL, 100, FORCE_ON_500));
        }

        @ParameterizedTest
        @CsvSource({
                "2000002, 100, 2000002",   // WHITE_POTION
                "4000019, 100, 4000019",   // SNAIL_SHELL
                "2050004, 100, 2050004"    // ALL_CURE_POTION
        })
        void overrideAppliedToMultipleStackableItemIds(int itemId, int wz, int ignored) {
            assertEquals(500, ItemStackModel.resolveMaxStack(itemId, wz, FORCE_ON_500));
        }

        @Test
        void equipmentNotOverriddenEvenWhenForceTrue() {
            assertEquals(1, ItemStackModel.resolveMaxStack(ItemId.GLADIUS, 1, FORCE_ON_500));
            assertEquals(1, ItemStackModel.resolveMaxStack(ItemId.MITHRIL_WAND, 1, FORCE_ON_500));
        }

        @Test
        void petNotOverriddenEvenWhenForceTrue() {
            assertEquals(1, ItemStackModel.resolveMaxStack(ItemId.PET_SNAIL, 1, FORCE_ON_500));
        }

        @Test
        void throwingStarNotOverriddenEvenWhenForceTrue() {
            assertEquals(800, ItemStackModel.resolveMaxStack(ItemId.SUBI_THROWING_STARS, 800, FORCE_ON_500));
            assertEquals(1000, ItemStackModel.resolveMaxStack(ItemId.CRYSTAL_ILBI_THROWING_STARS, 1000, FORCE_ON_500));
        }

        @Test
        void bulletNotOverriddenEvenWhenForceTrue() {
            assertEquals(3200, ItemStackModel.resolveMaxStack(ItemId.BULLET, 3200, FORCE_ON_500));
        }

        @Test
        void overrideClampedWhenAboveShortMax() {
            ItemStackModel.ItemStackOverrideConfig huge =
                    new ItemStackModel.ItemStackOverrideConfig(true, Integer.MAX_VALUE);
            assertEquals(Short.MAX_VALUE,
                    ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, 100, huge));
        }

        @Test
        void overrideClampedWhenNonPositive() {
            ItemStackModel.ItemStackOverrideConfig badMax =
                    new ItemStackModel.ItemStackOverrideConfig(true, 0);
            assertEquals(1, ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, 100, badMax));
        }
    }

    // --- configFromYaml: the only tests that touch YamlConfig ------------------

    @Nested
    class ConfigFromYaml {
        private boolean savedForce;
        private int savedMax;

        @BeforeEach
        void snapshotConfig() {
            savedForce = YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE;
            savedMax = YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE;
        }

        @AfterEach
        void restoreConfig() {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = savedForce;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = savedMax;
        }

        @Test
        void reflectsEnabledFlagAndValue() {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = true;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = 750;

            ItemStackModel.ItemStackOverrideConfig cfg = ItemStackModel.configFromYaml();
            assertTrue(cfg.forceOverride());
            assertEquals(750, cfg.maxStack());
        }

        @Test
        void reflectsDisabledFlag() {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = false;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = 750;

            ItemStackModel.ItemStackOverrideConfig cfg = ItemStackModel.configFromYaml();
            assertFalse(cfg.forceOverride());
            assertEquals(750, cfg.maxStack());
        }

        @Test
        void clampsToZeroWhenForceTrueAndMaxNonPositive() {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = true;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = 0;

            ItemStackModel.ItemStackOverrideConfig cfg = ItemStackModel.configFromYaml();
            assertTrue(cfg.forceOverride());
            assertEquals(1, cfg.maxStack());
        }

        @Test
        void clampsToZeroWhenForceTrueAndMaxNegative() {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = true;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = -50;

            ItemStackModel.ItemStackOverrideConfig cfg = ItemStackModel.configFromYaml();
            assertEquals(1, cfg.maxStack());
        }

        @Test
        void negativeMaxPassedThroughWhenForceFalse() {
            // When force is off, the max value is informational only; no clamping is applied.
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = false;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = -50;

            ItemStackModel.ItemStackOverrideConfig cfg = ItemStackModel.configFromYaml();
            assertFalse(cfg.forceOverride());
            assertEquals(-50, cfg.maxStack());
        }
    }

    // --- end-to-end style: resolveMaxStack composed with configFromYaml --------

    @Test
    void resolveMaxStackComposesWithConfigFromYamlWhenDisabled() {
        // With the default repo config (force=false), any stackable item should pass through.
        boolean savedForce = YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE;
        int savedMax = YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE;
        YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = false;
        try {
            int wz = 100;
            int resolved = ItemStackModel.resolveMaxStack(ItemId.WHITE_POTION, wz,
                    ItemStackModel.configFromYaml());
            assertEquals(wz, resolved);
        } finally {
            YamlConfig.config.server.USE_FORCE_ITEM_STACK_OVERRIDE = savedForce;
            YamlConfig.config.server.MAX_ITEM_STACK_OVERRIDE = savedMax;
        }
    }
}
