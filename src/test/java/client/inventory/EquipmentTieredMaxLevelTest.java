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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the tiered-equipment cap subsystem. These build an {@link
 * EquipmentLevelModel.EquipmentTierConfig} in-memory and exercise the pure resolver, the slot
 * classifier, and the formula evaluator without touching {@link config.YamlConfig}.
 */
class EquipmentTieredMaxLevelTest {

    /** Builds the exact spec: tier1 all-30, tier2 chest/pants/shield 40 + overalls/weapon 50, tier3 +5/10lvls. */
    private static EquipmentLevelModel.EquipmentTierConfig specConfig() {
        EquipmentLevelModel.Tier tier1 = new EquipmentLevelModel.Tier(69, Map.of(
                "chest", new EquipmentLevelModel.LiteralCap(30),
                "pants", new EquipmentLevelModel.LiteralCap(30),
                "overall", new EquipmentLevelModel.LiteralCap(30),
                "weapon", new EquipmentLevelModel.LiteralCap(30),
                "shield", new EquipmentLevelModel.LiteralCap(30),
                "default", new EquipmentLevelModel.LiteralCap(30)));
        EquipmentLevelModel.Tier tier2 = new EquipmentLevelModel.Tier(99, Map.of(
                "chest", new EquipmentLevelModel.LiteralCap(40),
                "pants", new EquipmentLevelModel.LiteralCap(40),
                "overall", new EquipmentLevelModel.LiteralCap(50),
                "weapon", new EquipmentLevelModel.LiteralCap(50),
                "shield", new EquipmentLevelModel.LiteralCap(40),
                "default", new EquipmentLevelModel.LiteralCap(30)));
        EquipmentLevelModel.Tier tier3 = new EquipmentLevelModel.Tier(-1, Map.of(
                "chest", new EquipmentLevelModel.FormulaCap("((characterLevel - 100) / 10 + 1) * 5 + 40"),
                "pants", new EquipmentLevelModel.FormulaCap("((characterLevel - 100) / 10 + 1) * 5 + 40"),
                "overall", new EquipmentLevelModel.FormulaCap("((characterLevel - 100) / 10 + 1) * 5 + 50"),
                "weapon", new EquipmentLevelModel.FormulaCap("((characterLevel - 100) / 10 + 1) * 5 + 50"),
                "shield", new EquipmentLevelModel.FormulaCap("((characterLevel - 100) / 10 + 1) * 5 + 40"),
                "default", new EquipmentLevelModel.LiteralCap(30)));
        return new EquipmentLevelModel.EquipmentTierConfig(true, List.of(tier1, tier2, tier3));
    }

    private static int tiered(EquipmentLevelModel.SlotKey slot, int charLevel) {
        return EquipmentLevelModel.tieredMaxLevel(slot, charLevel, specConfig());
    }

    // --- Tier 1 (character levels 1..69): everything capped at 30 ---------------------------

    @Test
    void tier1CapsAllSlotsAtThirty() {
        for (EquipmentLevelModel.SlotKey slot : EquipmentLevelModel.SlotKey.values()) {
            assertEquals(30, tiered(slot, 1), "level 1 " + slot);
            assertEquals(30, tiered(slot, 30), "level 30 " + slot);
            assertEquals(30, tiered(slot, 69), "level 69 " + slot);
        }
    }

    // --- Tier 2 (character levels 70..99) ---------------------------------------------------

    @Test
    void tier2AtLowerBoundary() {
        assertEquals(40, tiered(EquipmentLevelModel.SlotKey.CHEST, 70));
        assertEquals(40, tiered(EquipmentLevelModel.SlotKey.PANTS, 70));
        assertEquals(40, tiered(EquipmentLevelModel.SlotKey.SHIELD, 70));
        assertEquals(50, tiered(EquipmentLevelModel.SlotKey.OVERALL, 70));
        assertEquals(50, tiered(EquipmentLevelModel.SlotKey.WEAPON, 70));
        assertEquals(30, tiered(EquipmentLevelModel.SlotKey.DEFAULT, 70));
        assertEquals(30, tiered(EquipmentLevelModel.SlotKey.GLOVES, 70));   // non-scaling -> default 30
    }

    @Test
    void tier2AtUpperBoundaryUnchanged() {
        assertEquals(50, tiered(EquipmentLevelModel.SlotKey.WEAPON, 99));
        assertEquals(40, tiered(EquipmentLevelModel.SlotKey.PANTS, 99));
    }

    // --- Tier 3 (character level 100+) ------------------------------------------------------

    @Test
    void tier3FirstStepAtLevel100() {
        // bonus = ((100-100)/10 + 1) * 5 = 5
        assertEquals(55, tiered(EquipmentLevelModel.SlotKey.WEAPON, 100));
        assertEquals(55, tiered(EquipmentLevelModel.SlotKey.OVERALL, 100));
        assertEquals(45, tiered(EquipmentLevelModel.SlotKey.CHEST, 100));
        assertEquals(45, tiered(EquipmentLevelModel.SlotKey.PANTS, 100));
        assertEquals(45, tiered(EquipmentLevelModel.SlotKey.SHIELD, 100));
        assertEquals(30, tiered(EquipmentLevelModel.SlotKey.DEFAULT, 100));
    }

    @Test
    void tier3StepDoesNotAdvanceUntilNextTen() {
        assertEquals(55, tiered(EquipmentLevelModel.SlotKey.WEAPON, 109));   // still one step
        assertEquals(60, tiered(EquipmentLevelModel.SlotKey.WEAPON, 110));   // two steps: ((10/10)+1)*5=10
    }

    @Test
    void tier3AtLevel200MatchesUserSpec() {
        // bonus = ((200-100)/10 + 1) * 5 = 55
        assertEquals(105, tiered(EquipmentLevelModel.SlotKey.WEAPON, 200));
        assertEquals(95, tiered(EquipmentLevelModel.SlotKey.PANTS, 200));
        assertEquals(95, tiered(EquipmentLevelModel.SlotKey.CHEST, 200));
        assertEquals(95, tiered(EquipmentLevelModel.SlotKey.SHIELD, 200));
        assertEquals(105, tiered(EquipmentLevelModel.SlotKey.OVERALL, 200));
        assertEquals(30, tiered(EquipmentLevelModel.SlotKey.DEFAULT, 200));
    }

    @Test
    void tier3ContinuesScalingFarPast200() {
        // bonus at 1000 = ((900)/10 + 1) * 5 = 455 -> weapon 505
        assertEquals(505, tiered(EquipmentLevelModel.SlotKey.WEAPON, 1000));
    }

    @Test
    void weaponMaxIsMonotonicWithJumpsAtTierBoundaries() {
        int previous = tiered(EquipmentLevelModel.SlotKey.WEAPON, 1);
        for (int level = 2; level <= 250; level++) {
            int current = tiered(EquipmentLevelModel.SlotKey.WEAPON, level);
            assertTrue(current >= previous, "weapon regressed at level " + level + ": " + previous + " -> " + current);
            previous = current;
        }
        // Explicit boundary jumps from the spec.
        assertEquals(30, tiered(EquipmentLevelModel.SlotKey.WEAPON, 69));
        assertEquals(50, tiered(EquipmentLevelModel.SlotKey.WEAPON, 70));
        assertEquals(50, tiered(EquipmentLevelModel.SlotKey.WEAPON, 99));
        assertEquals(55, tiered(EquipmentLevelModel.SlotKey.WEAPON, 100));
    }

    // --- Slot classifier --------------------------------------------------------------------

    @Test
    void slotKeyOfClassifiesByItemIdPrefix() {
        assertEquals(EquipmentLevelModel.SlotKey.CAP, EquipmentLevelModel.SlotKey.of(1000000));
        assertEquals(EquipmentLevelModel.SlotKey.CHEST, EquipmentLevelModel.SlotKey.of(1040000));
        assertEquals(EquipmentLevelModel.SlotKey.OVERALL, EquipmentLevelModel.SlotKey.of(1050000));
        assertEquals(EquipmentLevelModel.SlotKey.PANTS, EquipmentLevelModel.SlotKey.of(1060000));
        assertEquals(EquipmentLevelModel.SlotKey.SHOES, EquipmentLevelModel.SlotKey.of(1070000));
        assertEquals(EquipmentLevelModel.SlotKey.GLOVES, EquipmentLevelModel.SlotKey.of(1080000));
        assertEquals(EquipmentLevelModel.SlotKey.SHIELD, EquipmentLevelModel.SlotKey.of(1090000));
        assertEquals(EquipmentLevelModel.SlotKey.CAPE, EquipmentLevelModel.SlotKey.of(1100000));
        assertEquals(EquipmentLevelModel.SlotKey.RING, EquipmentLevelModel.SlotKey.of(1110000));
        assertEquals(EquipmentLevelModel.SlotKey.ACCESSORY, EquipmentLevelModel.SlotKey.of(1010000));
        assertEquals(EquipmentLevelModel.SlotKey.ACCESSORY, EquipmentLevelModel.SlotKey.of(1120000));
        assertEquals(EquipmentLevelModel.SlotKey.TAMING, EquipmentLevelModel.SlotKey.of(1800000));
        assertEquals(EquipmentLevelModel.SlotKey.TAMING, EquipmentLevelModel.SlotKey.of(1902000));
        assertEquals(EquipmentLevelModel.SlotKey.DEFAULT, EquipmentLevelModel.SlotKey.of(2000000));
    }

    @Test
    void slotKeyOfClassifiesAllWeaponRangesAsWeapon() {
        assertEquals(EquipmentLevelModel.SlotKey.WEAPON, EquipmentLevelModel.SlotKey.of(1302000));  // sword
        assertEquals(EquipmentLevelModel.SlotKey.WEAPON, EquipmentLevelModel.SlotKey.of(1372000));  // wand
        assertEquals(EquipmentLevelModel.SlotKey.WEAPON, EquipmentLevelModel.SlotKey.of(1452000));  // bow
        assertEquals(EquipmentLevelModel.SlotKey.WEAPON, EquipmentLevelModel.SlotKey.of(1492000));  // pistol
    }

    // --- Fallback chain & degenerate config -------------------------------------------------

    @Test
    void missingSlotFallsBackToDefaultThenNetworkCap() {
        // Tier has only "default" -> chest resolves via default.
        EquipmentLevelModel.Tier onlyDefault = new EquipmentLevelModel.Tier(99,
                Map.of("default", new EquipmentLevelModel.LiteralCap(33)));
        EquipmentLevelModel.EquipmentTierConfig cfg = new EquipmentLevelModel.EquipmentTierConfig(true, List.of(onlyDefault));
        assertEquals(33, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.CHEST, 50, cfg));

        // Tier has neither slot nor default -> NETWORK_MAX_LEVEL (30).
        EquipmentLevelModel.Tier emptySlots = new EquipmentLevelModel.Tier(99, Map.of());
        cfg = new EquipmentLevelModel.EquipmentTierConfig(true, List.of(emptySlots));
        assertEquals(30, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.CHEST, 50, cfg));
    }

    @Test
    void emptyOrNullConfigReturnsNetworkCap() {
        assertEquals(30, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.WEAPON, 200, null));
        assertEquals(30, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.WEAPON, 200,
                new EquipmentLevelModel.EquipmentTierConfig(true, List.of())));
    }

    @Test
    void singleTierIsOpenEnded() {
        EquipmentLevelModel.Tier tier = new EquipmentLevelModel.Tier(10,
                Map.of("weapon", new EquipmentLevelModel.LiteralCap(77)));
        EquipmentLevelModel.EquipmentTierConfig cfg = new EquipmentLevelModel.EquipmentTierConfig(true, List.of(tier));
        assertEquals(77, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.WEAPON, 5, cfg));
        assertEquals(77, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.WEAPON, 9999, cfg));
    }

    @Test
    void customFormulaIsHonouredProvingDataDriven() {
        EquipmentLevelModel.Tier tier = new EquipmentLevelModel.Tier(-1,
                Map.of("weapon", new EquipmentLevelModel.FormulaCap("characterLevel + 1000")));
        EquipmentLevelModel.EquipmentTierConfig cfg = new EquipmentLevelModel.EquipmentTierConfig(true, List.of(tier));
        assertEquals(1050, EquipmentLevelModel.tieredMaxLevel(EquipmentLevelModel.SlotKey.WEAPON, 50, cfg));
    }

    // --- parseSlotCap -----------------------------------------------------------------------

    @Test
    void parseSlotCapDetectsLiteralVersusFormula() {
        assertEquals(new EquipmentLevelModel.LiteralCap(30), EquipmentLevelModel.parseSlotCap(30));
        assertEquals(new EquipmentLevelModel.LiteralCap(30), EquipmentLevelModel.parseSlotCap("30"));
        assertEquals(new EquipmentLevelModel.LiteralCap(-3), EquipmentLevelModel.parseSlotCap(-3));
        EquipmentLevelModel.SlotCap parsed = EquipmentLevelModel.parseSlotCap("characterLevel * 2");
        assertTrue(parsed instanceof EquipmentLevelModel.FormulaCap);
        assertEquals("characterLevel * 2", ((EquipmentLevelModel.FormulaCap) parsed).expression());
    }

    @Test
    void parseSlotCapRejectsInvalidSyntax() {
        assertThrows(IllegalArgumentException.class, () -> EquipmentLevelModel.parseSlotCap(""));
        assertThrows(IllegalArgumentException.class, () -> EquipmentLevelModel.parseSlotCap("1 +"));
    }

    // --- resolveCap -------------------------------------------------------------------------

    @Test
    void resolveCapClampsLiteralAndFormulaResultsToOne() {
        assertEquals(40, EquipmentLevelModel.resolveCap(new EquipmentLevelModel.LiteralCap(40), 200));
        assertEquals(1, EquipmentLevelModel.resolveCap(new EquipmentLevelModel.LiteralCap(0), 200));
        assertEquals(1, EquipmentLevelModel.resolveCap(new EquipmentLevelModel.LiteralCap(-5), 200));
        // Formula yielding a negative value is clamped to 1.
        assertEquals(1, EquipmentLevelModel.resolveCap(
                new EquipmentLevelModel.FormulaCap("characterLevel - 1000"), 1));
    }

    // --- CapFormula evaluator ---------------------------------------------------------------

    @Test
    void formulaEvaluatesLiteralsAndVariables() {
        assertEquals(30L, EquipmentLevelModel.CapFormula.evaluate("30", 200));
        assertEquals(200L, EquipmentLevelModel.CapFormula.evaluate("characterLevel", 200));
        assertEquals(200L, EquipmentLevelModel.CapFormula.evaluate("charLevel", 200));
        assertEquals(200L, EquipmentLevelModel.CapFormula.evaluate("cl", 200));
    }

    @Test
    void formulaHonoursPrecedenceAndParentheses() {
        assertEquals(14L, EquipmentLevelModel.CapFormula.evaluate("2 + 3 * 4", 1));
        assertEquals(20L, EquipmentLevelModel.CapFormula.evaluate("(2 + 3) * 4", 1));
        assertEquals(70L, EquipmentLevelModel.CapFormula.evaluate("-characterLevel + 100", 30));
    }

    @Test
    void formulaUsesIntegerTruncationDivision() {
        assertEquals(10L, EquipmentLevelModel.CapFormula.evaluate("characterLevel / 10", 105));
        assertEquals(105L, EquipmentLevelModel.CapFormula.evaluate("((characterLevel - 100) / 10 + 1) * 5 + 50", 200));
    }

    @Test
    void formulaIgnoresSurroundingAndInnerWhitespace() {
        assertEquals(20L, EquipmentLevelModel.CapFormula.evaluate("  ( 2 + 3 ) * 4 ", 1));
    }

    @Test
    void formulaRejectsUnknownVariableAndTrailingGarbage() {
        assertThrows(IllegalArgumentException.class, () -> EquipmentLevelModel.CapFormula.evaluate("foo + 1", 1));
        assertThrows(IllegalArgumentException.class, () -> EquipmentLevelModel.CapFormula.evaluate("1 2", 1));
        assertThrows(IllegalArgumentException.class, () -> EquipmentLevelModel.CapFormula.evaluate("(1 + 2", 1));
    }

    @Test
    void formulaRejectsDivisionByZero() {
        assertThrows(ArithmeticException.class,
                () -> EquipmentLevelModel.CapFormula.evaluate("1 / (characterLevel - 200)", 200));
    }
}
