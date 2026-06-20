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
import client.Character;

/**
 * Splits incoming damage into the HP/MP/meso/battleship components after applying
 * Magic Guard, Meso Guard, or Battleship absorption. This holds only the
 * defensive-absorption step (the part shared between player-taken damage and boss
 * reflect/counter damage) and intentionally does NOT apply WDEF/MDEF, Achilles,
 * High Defense, Power Guard, or Combo Barrier.
 */
public class DamageAbsorptionProcessor {

    public record DamageSplitResult(int displayDamage, int hpLoss, int mpLoss,
                                    int mesoLoss, boolean cancelMesoGuard,
                                    boolean damageBattleship) {}

    /**
     * Pure split of incoming damage into its absorbed components.
     *
     * @param damage             the incoming damage (already post-defense for normal hits;
     *                           the fixed counter value for reflect). Must be &gt;= 0.
     * @param magicGuardPercent  Magic Guard absorb percent (e.g. 80), or null when inactive
     * @param currentMp          current MP (Magic Guard overflow spills back to HP)
     * @param mesoGuardPercent   Meso Guard meso-cost percent, or null when inactive
     * @param currentMeso        current meso (insufficient meso cancels the buff)
     * @param ridingBattleship   whether the player is currently riding a battleship
     * @param mpAttack           additional MP burn; Magic Guard only engages when this is 0
     * @return the computed split, including the damage value to display to the client
     */
    public static DamageSplitResult splitDamage(int damage,
                                                Integer magicGuardPercent, int currentMp,
                                                Integer mesoGuardPercent, int currentMeso,
                                                boolean ridingBattleship, int mpAttack) {
        if (magicGuardPercent != null && mpAttack == 0) {
            int mpLoss = (int) (damage * (magicGuardPercent.doubleValue() / 100.0));
            int hpLoss = damage - mpLoss;
            if (mpLoss > currentMp) {
                hpLoss += mpLoss - currentMp;
                mpLoss = currentMp;
            }
            return new DamageSplitResult(damage, hpLoss, mpLoss, 0, false, false);
        } else if (mesoGuardPercent != null) {
            int halvedDamage = damage / 2;
            int mesoLoss = (int) (halvedDamage * (mesoGuardPercent.doubleValue() / 100.0));
            boolean cancel = currentMeso < mesoLoss;
            return new DamageSplitResult(halvedDamage, halvedDamage, mpAttack,
                    cancel ? currentMeso : mesoLoss, cancel, false);
        } else {
            return new DamageSplitResult(damage, damage, mpAttack, 0, false, ridingBattleship);
        }
    }

    /**
     * Applies a computed split to a character as side effects. The order mirrors the
     * original inline implementation: meso -&gt; buff cancel -&gt; battleship -&gt; HP/MP.
     */
    public static void applyTo(Character chr, DamageSplitResult r) {
        if (r.mesoLoss() > 0) {
            chr.gainMeso(-r.mesoLoss(), false);
        }
        if (r.cancelMesoGuard()) {
            chr.cancelBuffStats(BuffStat.MESOGUARD);
        }
        if (r.damageBattleship()) {
            chr.decreaseBattleshipHp(r.hpLoss());
        }
        chr.addMPHP(-r.hpLoss(), -r.mpLoss());
    }
}
