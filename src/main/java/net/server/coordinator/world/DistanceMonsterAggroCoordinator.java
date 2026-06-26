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

import client.Character;
import config.YamlConfig;
import net.server.Server;
import server.TimerManager;
import server.life.Monster;
import server.maps.MapleMap;
import tools.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Distance-weighted aggro coordinator.
 * <p>
 * Damage contributes aggro scaled by how close the attacker was to the monster
 * at the moment of the hit: a melee-range hit earns {@code meleeMultiplier}
 * (2.0x by default) times its damage, falling off linearly to {@code minMultiplier}
 * (0.1x by default) at {@code maxRangeSq} and beyond. This applies uniformly to
 * normal monsters and bosses, and is independent of the weapon or skill used --
 * only the Euclidean distance at hit time matters.
 * <p>
 * Decay is also proximity aware: an attacker standing within melee reach holds
 * their accumulated aggro indefinitely (the decay tick is skipped for them), so
 * a tank hugging a boss never bleeds threat while idle. Once they move out of
 * melee range, normal damage-instance decay resumes. Attackers whose position
 * cannot be resolved are treated as out-of-range and decay normally.
 *
 * @author Ronan
 */
public class DistanceMonsterAggroCoordinator implements MonsterAggroCoordinator {
    private final Lock lock = new ReentrantLock();
    private final Lock idleLock = new ReentrantLock(true);
    private long lastStopTime = Server.getInstance().getCurrentTime();

    private ScheduledFuture<?> aggroMonitor = null;

    private final Map<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> mobAggroEntries = new HashMap<>();
    private final Map<Monster, List<MonsterAggroOps.AggroEntry>> mobSortedAggros = new HashMap<>();

    private final Set<Integer> mapPuppetEntries = new HashSet<>();

    private final long aggroInterval;
    private final long meleeRangeSq;
    private final long maxRangeSq;
    private final double meleeMultiplier;
    private final double minMultiplier;

    public DistanceMonsterAggroCoordinator() {
        this(YamlConfig.config.server.AGGRO_MELEE_RANGE_SQ,
                YamlConfig.config.server.AGGRO_MAX_RANGE_SQ,
                YamlConfig.config.server.AGGRO_MELEE_MULTIPLIER,
                YamlConfig.config.server.AGGRO_MIN_MULTIPLIER,
                YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL);
    }

    /**
     * Package-private constructor for deterministic testing: all tuning knobs
     * (including the aggro-update interval that drives decay cadence) are
     * injected rather than read from {@link YamlConfig}.
     */
    DistanceMonsterAggroCoordinator(long meleeRangeSq, long maxRangeSq, double meleeMultiplier,
                                    double minMultiplier, long aggroInterval) {
        this.meleeRangeSq = meleeRangeSq;
        this.maxRangeSq = maxRangeSq;
        this.meleeMultiplier = meleeMultiplier;
        this.minMultiplier = minMultiplier;
        this.aggroInterval = aggroInterval;
    }

    @Override
    public void stopAggroCoordinator() {
        idleLock.lock();
        try {
            if (aggroMonitor == null) {
                return;
            }

            aggroMonitor.cancel(false);
            aggroMonitor = null;
        } finally {
            idleLock.unlock();
        }

        lastStopTime = Server.getInstance().getCurrentTime();
    }

    @Override
    public void startAggroCoordinator() {
        idleLock.lock();
        try {
            if (aggroMonitor != null) {
                return;
            }

            aggroMonitor = TimerManager.getInstance().register(() -> {
                runAggroUpdate(1);
                runSortLeadingCharactersAggro();
            }, YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL, YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL);
        } finally {
            idleLock.unlock();
        }

        int timeDelta = (int) Math.ceil((Server.getInstance().getCurrentTime() - lastStopTime) / YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL);
        if (timeDelta > 0) {
            runAggroUpdate(timeDelta);
        }
    }

    @Override
    public void addAggroDamage(Monster mob, int cid, int damage) { // assumption: should not trigger after dispose()
        if (!mob.isAlive()) {
            return;
        }

        List<MonsterAggroOps.AggroEntry> sortedAggro = mobSortedAggros.get(mob);
        Map<Integer, MonsterAggroOps.AggroEntry> mobAggro = mobAggroEntries.get(mob);
        if (mobAggro == null) {
            if (lock.tryLock()) {   // can run unreliably, as fast as possible... try lock that is!
                try {
                    mobAggro = mobAggroEntries.get(mob);
                    if (mobAggro == null) {
                        mobAggro = new HashMap<>();
                        mobAggroEntries.put(mob, mobAggro);

                        sortedAggro = new LinkedList<>();
                        mobSortedAggros.put(mob, sortedAggro);
                    } else {
                        sortedAggro = mobSortedAggros.get(mob);
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                return;
            }
        }

        synchronized (mobAggro) {
            synchronized (sortedAggro) {
                MonsterAggroOps.AggroEntry aggroEntry = mobAggro.get(cid);
                boolean isNew = (aggroEntry == null);
                if (isNew) {
                    aggroEntry = MonsterAggroOps.AggroEntry.create(cid);
                } else if (damage < 1) {
                    return;
                }

                int scaledDamage = scaleDamageByDistance(mob, cid, damage);
                MonsterAggroOps.AggroEntry updated = MonsterAggroOps.recordDamage(aggroEntry, scaledDamage, aggroInterval);
                mobAggro.put(cid, updated);
                MonsterAggroOps.replaceOrAppend(sortedAggro, updated);
            }
        }
    }

    /**
     * Scales raw {@code damage} by the distance-derived multiplier. Melee hits
     * earn the full {@code meleeMultiplier}; hits at or beyond {@code maxRangeSq}
     * earn {@code minMultiplier}; anything between is linearly interpolated.
     * Unresolvable attackers default to {@code minMultiplier}.
     */
    private int scaleDamageByDistance(Monster mob, int cid, int damage) {
        if (damage < 1) {
            return 0;
        }

        double multiplier = minMultiplier;
        MapleMap map = mob.getMap();
        if (map != null) {
            Character chr = map.getCharacterById(cid);
            if (chr != null) {
                long distSq = (long) mob.getPosition().distanceSq(chr.getPosition());
                multiplier = MonsterAggroOps.distanceMultiplier(distSq, meleeRangeSq, maxRangeSq, meleeMultiplier, minMultiplier);
            }
        }

        return (int) Math.round(damage * multiplier);
    }

    /**
     * True when the attacker currently stands within melee reach of the mob, in
     * which case their aggro entry is frozen (no decay this tick). Returns false
     * when proximity cannot be confirmed so that normal decay applies.
     */
    private boolean isFrozenWithinMelee(Monster mob, int cid) {
        MapleMap map = mob.getMap();
        if (map == null) {
            return false;
        }
        Character chr = map.getCharacterById(cid);
        if (chr == null) {
            return false;
        }

        long distSq = (long) mob.getPosition().distanceSq(chr.getPosition());
        return MonsterAggroOps.isWithinMelee(distSq, meleeRangeSq);
    }

    void runAggroUpdate(int deltaTime) {
        List<Pair<Monster, Map<Integer, MonsterAggroOps.AggroEntry>>> aggroMobs = new LinkedList<>();
        lock.lock();
        try {
            for (Entry<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> e : mobAggroEntries.entrySet()) {
                aggroMobs.add(new Pair<>(e.getKey(), e.getValue()));
            }
        } finally {
            lock.unlock();
        }

        for (Pair<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> am : aggroMobs) {
            Monster mob = am.getLeft();
            Map<Integer, MonsterAggroOps.AggroEntry> mobAggro = am.getRight();
            List<MonsterAggroOps.AggroEntry> sortedAggro = mobSortedAggros.get(mob);

            if (sortedAggro != null) {
                List<Integer> toRemove = new LinkedList<>();
                Map<Integer, MonsterAggroOps.AggroEntry> updates = new HashMap<>();

                synchronized (mobAggro) {
                    synchronized (sortedAggro) {
                        for (MonsterAggroOps.AggroEntry pae : mobAggro.values()) {
                            if (isFrozenWithinMelee(mob, pae.cid())) {
                                continue;   // holding melee proximity: keep aggro, skip decay
                            }

                            MonsterAggroOps.TickResult res = MonsterAggroOps.tickExpiry(pae, deltaTime, aggroInterval);
                            if (res.expired()) {
                                toRemove.add(pae.cid());
                            } else {
                                updates.put(pae.cid(), res.entry());
                            }
                        }

                        for (Entry<Integer, MonsterAggroOps.AggroEntry> u : updates.entrySet()) {
                            mobAggro.put(u.getKey(), u.getValue());
                            MonsterAggroOps.replaceOrAppend(sortedAggro, u.getValue());
                        }

                        if (!toRemove.isEmpty()) {
                            for (Integer cid : toRemove) {
                                mobAggro.remove(cid);
                                MonsterAggroOps.removeByCid(sortedAggro, cid);
                            }

                            if (mobAggro.isEmpty()) {   // all aggro on this mob expired
                                if (!mob.isBoss()) {
                                    mob.aggroResetAggro();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean isLeadingCharacterAggro(Monster mob, Character player) {
        if (mob.isLeadingPuppetInVicinity()) {
            return false;
        } else if (mob.isCharacterPuppetInVicinity(player)) {
            return true;
        }

        // by assuming the quasi-sorted nature of "mobAggroList", this method
        // returns whether the player given as parameter can be elected as next aggro leader

        List<MonsterAggroOps.AggroEntry> mobAggroList = mobSortedAggros.get(mob);
        if (mobAggroList != null) {
            synchronized (mobAggroList) {
                mobAggroList = new ArrayList<>(mobAggroList.subList(0, Math.min(mobAggroList.size(), 5)));
            }

            MapleMap map = mob.getMap();
            for (MonsterAggroOps.AggroEntry pae : mobAggroList) {
                Character chr = map.getCharacterById(pae.cid());
                if (chr != null) {
                    if (player.getId() == pae.cid()) {
                        return true;
                    } else if (pae.updateStreak() < YamlConfig.config.server.MOB_STATUS_AGGRO_PERSISTENCE && chr.isAlive()) {  // verifies currently leading players activity
                        return false;
                    }
                }
            }
        }

        return false;
    }

    public void runSortLeadingCharactersAggro() {
        List<List<MonsterAggroOps.AggroEntry>> aggroList;
        lock.lock();
        try {
            aggroList = new ArrayList<>(mobSortedAggros.values());
        } finally {
            lock.unlock();
        }

        for (List<MonsterAggroOps.AggroEntry> mobAggroList : aggroList) {
            synchronized (mobAggroList) {
                MonsterAggroOps.sortByAccumulatedDamageDesc(mobAggroList);
            }
        }
    }

    @Override
    public void removeAggroEntries(Monster mob) {
        lock.lock();
        try {
            mobAggroEntries.remove(mob);
            mobSortedAggros.remove(mob);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addPuppetAggro(Character player) {
        synchronized (mapPuppetEntries) {
            mapPuppetEntries.add(player.getId());
        }
    }

    @Override
    public void removePuppetAggro(Integer cid) {
        synchronized (mapPuppetEntries) {
            mapPuppetEntries.remove(cid);
        }
    }

    @Override
    public List<Integer> getPuppetAggroList() {
        synchronized (mapPuppetEntries) {
            return new ArrayList<>(mapPuppetEntries);
        }
    }

    @Override
    public void dispose() {
        stopAggroCoordinator();

        lock.lock();
        try {
            mobAggroEntries.clear();
            mobSortedAggros.clear();
        } finally {
            lock.unlock();
        }
    }
}
