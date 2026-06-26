/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.coordinator.world;

import java.util.List;

/**
 * Pure, composable helpers shared by the aggro coordinators.
 * <p>
 * All mutating-looking operations are side-effect free: they return new
 * immutable {@link AggroEntry} instances, leaving callers free to store and
 * replace them as they see fit. The field-update ordering faithfully mirrors
 * the legacy implementation (expiry recomputed with the pre-increment damage
 * instance count on damage, and the pre-decrement count on expiry) so that
 * decay cadence is preserved exactly.
 *
 * @author GIANTCRAB
 */
final class MonsterAggroOps {

    /** Damage-window (ms) an individual hit stays "fresh" before it begins decaying. */
    static final long DAMAGE_INSTANCE_LIFETIME_MS = 120000L;

    private MonsterAggroOps() {
    }

    /**
     * Immutable snapshot of a single player's aggro state against one monster.
     * Every transition (recording damage, ticking decay) yields a new instance.
     */
    record AggroEntry(int cid, int averageDamage, int currentDamageInstances,
                      long accumulatedDamage, int expireStreak, int updateStreak,
                      int toNextUpdate) {

        static AggroEntry create(int cid) {
            return new AggroEntry(cid, 0, 0, 0L, 0, 0, 0);
        }
    }

    /** Outcome of a single decay tick: the (possibly expired) resulting entry. */
    record TickResult(boolean expired, AggroEntry entry) {
    }

    /**
     * Number of aggro-update ticks before the oldest damage instance expires,
     * shrinking as the player stacks more recent hits (each instance halves the
     * window).
     */
    static int computeExpiryInterval(int expireStreak, int currentDamageInstances, long aggroInterval) {
        return (int) Math.ceil((DAMAGE_INSTANCE_LIFETIME_MS / aggroInterval) / Math.pow(2, expireStreak + currentDamageInstances));
    }

    /**
     * Returns a new entry with {@code damage} folded into the running average.
     * Recording a hit refreshes the decay window using the <em>pre-increment</em>
     * instance count, matching the legacy cadence.
     */
    static AggroEntry recordDamage(AggroEntry e, int damage, long aggroInterval) {
        long totalDamage = (long) e.averageDamage() * e.currentDamageInstances() + damage;

        int toNextUpdate = computeExpiryInterval(0, e.currentDamageInstances(), aggroInterval);

        int currentDamageInstances = e.currentDamageInstances() + 1;
        int averageDamage = (int) (totalDamage / currentDamageInstances);

        return new AggroEntry(e.cid(), averageDamage, currentDamageInstances, totalDamage, 0, 0, toNextUpdate);
    }

    /**
     * Advances the entry's decay clock by {@code deltaTime} ticks. When the
     * window elapses, one damage instance expires (and the accumulated total is
     * recomputed); once every instance has expired the entry is flagged for
     * removal. The expiry window is recomputed with the <em>pre-decrement</em>
     * instance count, matching the legacy cadence.
     */
    static TickResult tickExpiry(AggroEntry e, int deltaTime, long aggroInterval) {
        int updateStreak = e.updateStreak() + 1;
        int toNextUpdate = e.toNextUpdate() - deltaTime;

        if (toNextUpdate > 0) {
            return new TickResult(false, new AggroEntry(e.cid(), e.averageDamage(), e.currentDamageInstances(),
                    e.accumulatedDamage(), e.expireStreak(), updateStreak, toNextUpdate));
        }

        int expireStreak = e.expireStreak() + 1;
        int currentDamageInstances = e.currentDamageInstances() - 1;
        if (currentDamageInstances < 1) {
            return new TickResult(true, e);
        }

        int newToNextUpdate = computeExpiryInterval(expireStreak, e.currentDamageInstances(), aggroInterval);
        long accumulatedDamage = (long) e.averageDamage() * currentDamageInstances;
        return new TickResult(false, new AggroEntry(e.cid(), e.averageDamage(), currentDamageInstances,
                accumulatedDamage, expireStreak, updateStreak, newToNextUpdate));
    }

    /**
     * Inserts {@code entry} into {@code list}, replacing the existing element
     * that shares its {@code cid} (or appending when absent). Used to keep an
     * immutable-entry cache in sync with the authoritative map.
     */
    static void replaceOrAppend(List<AggroEntry> list, AggroEntry entry) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).cid() == entry.cid()) {
                list.set(i, entry);
                return;
            }
        }
        list.add(entry);
    }

    /** Removes the first element whose {@code cid} matches; returns true if found. */
    static boolean removeByCid(List<AggroEntry> list, int cid) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).cid() == cid) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Sorts {@code list} in place by descending accumulated damage using a stable
     * insertion sort (preserving relative order of tied entries). Mirrors the
     * legacy quasi-sorted leader ordering.
     */
    static void sortByAccumulatedDamageDesc(List<AggroEntry> list) {
        for (int i = 1; i < list.size(); i++) {
            AggroEntry pae = list.get(i);
            long curAccDmg = pae.accumulatedDamage();

            int j = i - 1;
            while (j >= 0 && curAccDmg > list.get(j).accumulatedDamage()) {
                j -= 1;
            }

            j += 1;
            if (j != i) {
                list.remove(i);
                list.add(j, pae);
            }
        }
    }

    // ----- Distance-weighted aggro helpers (used by DistanceMonsterAggroCoordinator) -----

    /**
     * Linearly interpolates an aggro multiplier between {@code hiMult} (at or
     * inside melee range) and {@code loMult} (at or beyond max range), clamped to
     * that band. With the default anchors this yields 2.0x at melee range and
     * 0.1x at typical max range.
     */
    static double distanceMultiplier(long distSq, long meleeSq, long maxSq, double hiMult, double loMult) {
        if (maxSq <= meleeSq) {
            return loMult;
        }
        if (distSq <= meleeSq) {
            return hiMult;
        }
        if (distSq >= maxSq) {
            return loMult;
        }

        double t = (double) (distSq - meleeSq) / (double) (maxSq - meleeSq);
        return hiMult + (loMult - hiMult) * t;
    }

    /** True when the attacker is within melee reach of the monster. */
    static boolean isWithinMelee(long distSq, long meleeSq) {
        return distSq <= meleeSq;
    }
}
