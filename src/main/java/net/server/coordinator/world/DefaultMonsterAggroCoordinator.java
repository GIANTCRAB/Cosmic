/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 * @author Ronan
 */
public class DefaultMonsterAggroCoordinator implements MonsterAggroCoordinator {
    private final Lock lock = new ReentrantLock();
    private final Lock idleLock = new ReentrantLock(true);
    private long lastStopTime = Server.getInstance().getCurrentTime();

    private ScheduledFuture<?> aggroMonitor = null;

    private final Map<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> mobAggroEntries = new HashMap<>();
    private final Map<Monster, List<MonsterAggroOps.AggroEntry>> mobSortedAggros = new HashMap<>();

    private final Set<Integer> mapPuppetEntries = new HashSet<>();

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

        long aggroInterval = YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL;
        synchronized (mobAggro) {
            synchronized (sortedAggro) {
                MonsterAggroOps.AggroEntry aggroEntry = mobAggro.get(cid);
                boolean isNew = (aggroEntry == null);
                if (isNew) {
                    aggroEntry = MonsterAggroOps.AggroEntry.create(cid);
                } else if (damage < 1) {
                    return;
                }

                MonsterAggroOps.AggroEntry updated = MonsterAggroOps.recordDamage(aggroEntry, damage, aggroInterval);
                mobAggro.put(cid, updated);
                MonsterAggroOps.replaceOrAppend(sortedAggro, updated);
            }
        }
    }

    private void runAggroUpdate(int deltaTime) {
        List<Pair<Monster, Map<Integer, MonsterAggroOps.AggroEntry>>> aggroMobs = new LinkedList<>();
        lock.lock();
        try {
            for (Entry<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> e : mobAggroEntries.entrySet()) {
                aggroMobs.add(new Pair<>(e.getKey(), e.getValue()));
            }
        } finally {
            lock.unlock();
        }

        long aggroInterval = YamlConfig.config.server.MOB_STATUS_AGGRO_INTERVAL;

        for (Pair<Monster, Map<Integer, MonsterAggroOps.AggroEntry>> am : aggroMobs) {
            Map<Integer, MonsterAggroOps.AggroEntry> mobAggro = am.getRight();
            List<MonsterAggroOps.AggroEntry> sortedAggro = mobSortedAggros.get(am.getLeft());

            if (sortedAggro != null) {
                List<Integer> toRemove = new LinkedList<>();
                Map<Integer, MonsterAggroOps.AggroEntry> updates = new HashMap<>();

                synchronized (mobAggro) {
                    synchronized (sortedAggro) {
                        for (MonsterAggroOps.AggroEntry pae : mobAggro.values()) {
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
                                if (!am.getLeft().isBoss()) {
                                    am.getLeft().aggroResetAggro();
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
