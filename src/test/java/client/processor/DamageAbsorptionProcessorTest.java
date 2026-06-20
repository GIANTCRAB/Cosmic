/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.processor;

import client.BuffStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import testutil.HandlerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DamageAbsorptionProcessorTest extends HandlerTest {

    // ---- splitDamage: Magic Guard ----

    @Test
    void magicGuard_absorbsIntoMpWhenEnoughMp() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, 80, 2000, null, 0, false, 0);

        assertSplit(r, 1000, 200, 800, 0, false, false);
    }

    @Test
    void magicGuard_overflowsToHpWhenMpInsufficient() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, 80, 400, null, 0, false, 0);

        // 80% of 1000 = 800 to MP, but only 400 MP available -> 400 overflow added to HP
        assertSplit(r, 1000, 600, 400, 0, false, false);
    }

    @Test
    void magicGuard_isSkippedWhenMpAttackNonZero() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, 80, 2000, null, 0, false, 100);

        // Magic Guard only engages when mpAttack == 0; full damage lands on HP
        assertSplit(r, 1000, 1000, 100, 0, false, false);
    }

    @Test
    void magicGuard_takesPriorityOverMesoGuard() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, 80, 2000, 50, 1000, false, 0);

        assertSplit(r, 1000, 200, 800, 0, false, false);
    }

    // ---- splitDamage: Meso Guard ----

    @Test
    void mesoGuard_halvesDamageAndConsumesMesoWhenEnoughMeso() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, null, 0, 50, 1000, false, 0);

        // halved to 500 HP, 50% of 500 = 250 meso
        assertSplit(r, 500, 500, 0, 250, false, false);
    }

    @Test
    void mesoGuard_cancelsWhenInsufficientMeso() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1000, null, 0, 50, 200, false, 0);

        // needs 250 meso, only 200 available -> cancels and drains the remaining 200
        assertSplit(r, 500, 500, 0, 200, true, false);
    }

    @Test
    void mesoGuard_usesIntegerDivisionForOddDamage() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                1001, null, 0, 50, 1000, false, 0);

        // 1001 / 2 == 500 (int division, mirrors Math.round(int/2) in the original code)
        assertSplit(r, 500, 500, 0, 250, false, false);
    }

    // ---- splitDamage: no buffs / battleship ----

    @Test
    void noBuffs_takesFullDamage() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                500, null, 0, null, 0, false, 0);

        assertSplit(r, 500, 500, 0, 0, false, false);
    }

    @Test
    void noBuffs_flagsBattleshipWhenRiding() {
        DamageAbsorptionProcessor.DamageSplitResult r = DamageAbsorptionProcessor.splitDamage(
                500, null, 0, null, 0, true, 0);

        // player still takes the full hit; battleship is also damaged by the same amount
        assertSplit(r, 500, 500, 0, 0, false, true);
    }

    // ---- applyTo: side-effect wiring (mocked Character) ----

    @Test
    void applyTo_magicGuardDeductsHpAndMpOnly() {
        DamageAbsorptionProcessor.DamageSplitResult split = DamageAbsorptionProcessor.splitDamage(
                1000, 80, 2000, null, 0, false, 0);

        DamageAbsorptionProcessor.applyTo(chr, split);

        verify(chr).addMPHP(-200, -800);
        verify(chr, never()).gainMeso(anyInt(), anyBoolean());
        verify(chr, never()).cancelBuffStats(BuffStat.MESOGUARD);
        verify(chr, never()).decreaseBattleshipHp(anyInt());
    }

    @Test
    void applyTo_mesoGuardCancelDrainsAllMesoAndCancelsBuff() {
        DamageAbsorptionProcessor.DamageSplitResult split = DamageAbsorptionProcessor.splitDamage(
                1000, null, 0, 50, 200, false, 0);

        DamageAbsorptionProcessor.applyTo(chr, split);

        verify(chr).gainMeso(-200, false);
        verify(chr).cancelBuffStats(BuffStat.MESOGUARD);
        verify(chr).addMPHP(-500, 0);
        verify(chr, never()).decreaseBattleshipHp(anyInt());
    }

    @Test
    void applyTo_battleshipDamagesShipAndPlayerHp() {
        DamageAbsorptionProcessor.DamageSplitResult split = DamageAbsorptionProcessor.splitDamage(
                500, null, 0, null, 0, true, 0);

        DamageAbsorptionProcessor.applyTo(chr, split);

        verify(chr).decreaseBattleshipHp(500);
        verify(chr).addMPHP(-500, 0);
        verify(chr, never()).gainMeso(anyInt(), anyBoolean());
        verify(chr, never()).cancelBuffStats(BuffStat.MESOGUARD);
    }

    private static void assertSplit(DamageAbsorptionProcessor.DamageSplitResult r,
                                    int displayDamage, int hpLoss, int mpLoss,
                                    int mesoLoss, boolean cancelMesoGuard, boolean damageBattleship) {
        assertEquals(displayDamage, r.displayDamage(), "displayDamage");
        assertEquals(hpLoss, r.hpLoss(), "hpLoss");
        assertEquals(mpLoss, r.mpLoss(), "mpLoss");
        assertEquals(mesoLoss, r.mesoLoss(), "mesoLoss");
        if (cancelMesoGuard) {
            assertTrue(r.cancelMesoGuard(), "cancelMesoGuard");
        } else {
            assertFalse(r.cancelMesoGuard(), "cancelMesoGuard");
        }
        if (damageBattleship) {
            assertTrue(r.damageBattleship(), "damageBattleship");
        } else {
            assertFalse(r.damageBattleship(), "damageBattleship");
        }
    }
}
