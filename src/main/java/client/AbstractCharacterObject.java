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
package client;

import config.YamlConfig;
import constants.game.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.maps.AbstractAnimatedMapObject;
import server.maps.MapleMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author RonanLana
 */
public abstract class AbstractCharacterObject extends AbstractAnimatedMapObject {
    private static final Logger log = LoggerFactory.getLogger(AbstractCharacterObject.class);

    protected MapleMap map;
    protected int str, dex, luk, int_, hp, maxhp, mp, maxmp;
    protected int hpMpApUsed, remainingAp;
    protected int[] remainingSp = new int[10];
    protected transient int clientmaxhp, clientmaxmp, localmaxhp = 50, localmaxmp = 5;
    protected float transienthp = Float.NEGATIVE_INFINITY, transientmp = Float.NEGATIVE_INFINITY;

    private AbstractCharacterListener listener = null;
    protected Map<Stat, Integer> statUpdates = new HashMap<>();

    protected final Lock effLock = new ReentrantLock(true);
    protected final Lock statRlock;
    protected final Lock statWlock;

    protected AbstractCharacterObject() {
        ReadWriteLock statLock = new ReentrantReadWriteLock(true);
        this.statRlock = statLock.readLock();
        this.statWlock = statLock.writeLock();

        for (int i = 0; i < remainingSp.length; i++) {
            remainingSp[i] = 0;
        }
    }

    protected void setListener(AbstractCharacterListener listener) {
        this.listener = listener;
    }

    public void setMap(MapleMap map) {
        this.map = map;
    }

    public MapleMap getMap() {
        return map;
    }

    public int getStr() {
        statRlock.lock();
        try {
            return str;
        } finally {
            statRlock.unlock();
        }
    }

    public int getDex() {
        statRlock.lock();
        try {
            return dex;
        } finally {
            statRlock.unlock();
        }
    }

    public int getInt() {
        statRlock.lock();
        try {
            return int_;
        } finally {
            statRlock.unlock();
        }
    }

    public int getLuk() {
        statRlock.lock();
        try {
            return luk;
        } finally {
            statRlock.unlock();
        }
    }

    public int getRemainingAp() {
        statRlock.lock();
        try {
            return remainingAp;
        } finally {
            statRlock.unlock();
        }
    }

    protected int getRemainingSp(int jobid) {
        statRlock.lock();
        try {
            return remainingSp[GameConstants.getSkillBook(jobid)];
        } finally {
            statRlock.unlock();
        }
    }

    public int[] getRemainingSps() {
        statRlock.lock();
        try {
            return Arrays.copyOf(remainingSp, remainingSp.length);
        } finally {
            statRlock.unlock();
        }
    }

    public int getHpMpApUsed() {
        statRlock.lock();
        try {
            return hpMpApUsed;
        } finally {
            statRlock.unlock();
        }
    }

    public boolean isAlive() {
        statRlock.lock();
        try {
            return hp > 0;
        } finally {
            statRlock.unlock();
        }
    }

    public int getHp() {
        statRlock.lock();
        try {
            return hp;
        } finally {
            statRlock.unlock();
        }
    }

    public int getMp() {
        statRlock.lock();
        try {
            return mp;
        } finally {
            statRlock.unlock();
        }
    }

    /** Permanent maximum HP, including gains above the combat/client cap. */
    public int getMaxHp() {
        statRlock.lock();
        try {
            return maxhp;
        } finally {
            statRlock.unlock();
        }
    }

    /** Permanent maximum MP, including gains above the combat/client cap. */
    public int getMaxMp() {
        statRlock.lock();
        try {
            return maxmp;
        } finally {
            statRlock.unlock();
        }
    }

    /** Capped base maximum; the client applies equipment and buffs separately. */
    public int getClientMaxHp() {
        return clientmaxhp;
    }

    /** Capped base maximum; the client applies equipment and buffs separately. */
    public int getClientMaxMp() {
        return clientmaxmp;
    }

    /** Effective combat maximum after equipment, buffs, and the 30,000 cap. */
    public int getCurrentMaxHp() {
        return localmaxhp;
    }

    /** Effective combat maximum after equipment, buffs, and the 30,000 cap. */
    public int getCurrentMaxMp() {
        return localmaxmp;
    }

    private void setHpMpApUsed(int mpApUsed) {
        this.hpMpApUsed = mpApUsed;
    }

    private void dispatchHpChanged(final int oldHp) {
        listener.onHpChanged(oldHp);
    }

    private void dispatchHpmpPoolUpdated() {
        listener.onHpmpPoolUpdate();
    }

    private void dispatchStatUpdated() {
        listener.onStatUpdate();
    }

    private void dispatchStatPoolUpdateAnnounced() {
        listener.onAnnounceStatPoolUpdate();
    }

    protected void setHp(int newHp) {
        int oldHp = hp;

        int thp = newHp;
        if (thp < 0) {
            thp = 0;
        } else if (thp > localmaxhp) {
            thp = localmaxhp;
        }

        if (this.hp != thp) {
            this.transienthp = Float.NEGATIVE_INFINITY;
        }
        this.hp = thp;

        dispatchHpChanged(oldHp);
    }

    protected void setMp(int newMp) {
        int tmp = newMp;
        if (tmp < 0) {
            tmp = 0;
        } else if (tmp > localmaxmp) {
            tmp = localmaxmp;
        }

        if (this.mp != tmp) {
            this.transientmp = Float.NEGATIVE_INFINITY;
        }
        this.mp = tmp;
    }

    private void setRemainingAp(int remainingAp) {
        this.remainingAp = remainingAp;
    }

    private void setRemainingSp(int remainingSp, int skillbook) {
        this.remainingSp[skillbook] = remainingSp;
    }

    protected void setMaxHp(int hp_) {
        if (this.maxhp < hp_) {
            this.transienthp = Float.NEGATIVE_INFINITY;
        }
        this.maxhp = hp_;
        this.clientmaxhp = HpMpStats.cap(hp_);
    }

    protected void setMaxMp(int mp_) {
        if (this.maxmp < mp_) {
            this.transientmp = Float.NEGATIVE_INFINITY;
        }
        this.maxmp = mp_;
        this.clientmaxmp = HpMpStats.cap(mp_);
    }

    private static long clampStat(int v, int min, int max) {
        return (v < min) ? min : ((v > max) ? max : v);
    }

    private static long calcStatPoolNode(Integer v, int displacement) {
        long r;
        if (v == null) {
            r = -32768;
        } else {
            r = clampStat(v, -32767, 32767);
        }

        return ((r & 0x0FFFF) << displacement);
    }

    private static long calcStatPoolLong(Integer v1, Integer v2, Integer v3, Integer v4) {
        long ret = 0;

        ret |= calcStatPoolNode(v1, 48);
        ret |= calcStatPoolNode(v2, 32);
        ret |= calcStatPoolNode(v3, 16);
        ret |= calcStatPoolNode(v4, 0);

        return ret;
    }

    private record HpMpUpdate(Integer hp, Integer mp, Integer maxHp, Integer maxMp) {
    }

    private void changeStatPool(HpMpUpdate hpMpPool, Long strDexIntLuk, Long newSp, int newAp, boolean silent) {
        changeStatPool(hpMpPool, strDexIntLuk, newSp, newAp, silent, false);
    }

    private void changeStatPool(HpMpUpdate hpMpPool, Long strDexIntLuk, Long newSp, int newAp,
                                boolean silent, boolean apReset) {
        effLock.lock();
        statWlock.lock();
        try {
            statUpdates.clear();
            boolean poolUpdate = false;
            boolean statUpdate = false;
            int oldHp = hp, oldMp = mp;
            int oldMaxHp = localmaxhp, oldMaxMp = localmaxmp;

            if (hpMpPool != null) {
                if (hpMpPool.maxHp() != null) {
                    poolUpdate = true;
                    setMaxHp(Math.max(50, hpMpPool.maxHp()));
                    statUpdates.put(Stat.MAXHP, clientmaxhp);
                    statUpdates.put(Stat.HP, hp);
                }

                if (hpMpPool.maxMp() != null) {
                    poolUpdate = true;
                    setMaxMp(Math.max(5, hpMpPool.maxMp()));
                    statUpdates.put(Stat.MAXMP, clientmaxmp);
                    statUpdates.put(Stat.MP, mp);
                }
            }

            if (strDexIntLuk != null) {
                short newStr = (short) (strDexIntLuk >> 48);
                short newDex = (short) (strDexIntLuk >> 32);
                short newInt = (short) (strDexIntLuk >> 16);
                short newLuk = strDexIntLuk.shortValue();

                if (newStr >= 4) {
                    setStr(newStr);
                    statUpdates.put(Stat.STR, str);
                }

                if (newDex >= 4) {
                    setDex(newDex);
                    statUpdates.put(Stat.DEX, dex);
                }

                if (newInt >= 4) {
                    setInt(newInt);
                    statUpdates.put(Stat.INT, int_);
                }

                if (newLuk >= 4) {
                    setLuk(newLuk);
                    statUpdates.put(Stat.LUK, luk);
                }

                statUpdate = true;
            }

            if (newAp >= 0) {
                setRemainingAp(newAp);
                statUpdates.put(Stat.AVAILABLEAP, remainingAp);
            }

            if (newSp != null) {
                short sp = (short) (newSp >> 16);
                short skillbook = newSp.shortValue();

                setRemainingSp(sp, skillbook);
                statUpdates.put(Stat.AVAILABLESP, remainingSp[skillbook]);
            }

            boolean currentPoolUpdate = hpMpPool != null && (hpMpPool.hp() != null || hpMpPool.mp() != null);
            if (!statUpdates.isEmpty() || currentPoolUpdate) {
                if (poolUpdate) {
                    dispatchHpmpPoolUpdated();
                }

                if (statUpdate && !poolUpdate) {
                    dispatchStatUpdated();
                }

                // Explicit current values must be clamped against the newly derived maximums.
                if (currentPoolUpdate) {
                    if (hpMpPool.hp() != null) {
                        setHp(hpMpPool.hp());
                        statUpdates.put(Stat.HP, hp);
                    }
                    if (hpMpPool.mp() != null) {
                        setMp(hpMpPool.mp());
                        statUpdates.put(Stat.MP, mp);
                    }
                }

                // Washing spends the permanent maximum, not hidden current combat HP/MP.
                if (apReset && !YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE) {
                    if (localmaxhp < oldMaxHp) {
                        setHp(Math.max(oldHp > 0 ? 1 : 0, oldHp + localmaxhp - oldMaxHp));
                        statUpdates.put(Stat.HP, hp);
                    }
                    if (localmaxmp < oldMaxMp) {
                        setMp(Math.max(0, oldMp + localmaxmp - oldMaxMp));
                        statUpdates.put(Stat.MP, mp);
                    }
                }

                if (!silent) {
                    dispatchStatPoolUpdateAnnounced();
                }
            }
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void healHpMp() {
        updateHpMp(HpMpStats.MAX_EFFECTIVE);
    }

    public void updateHpMp(int x) {
        updateHpMp(x, x);
    }

    public void updateHpMp(int newhp, int newmp) {
        changeHpMp(newhp, newmp, false);
    }

    protected void changeHpMp(int newhp, int newmp, boolean silent) {
        changeHpMpPool(newhp, newmp, null, null, silent);
    }

    private void changeHpMpPool(Integer hp, Integer mp, Integer maxhp, Integer maxmp, boolean silent) {
        changeStatPool(new HpMpUpdate(hp, mp, maxhp, maxmp), null, null, -1, silent);
    }

    public void updateHp(int hp) {
        updateHpMaxHp(hp, null);
    }

    public void updateMaxHp(int maxhp) {
        updateHpMaxHp(null, maxhp);
    }

    public void updateHpMaxHp(int hp, int maxhp) {
        updateHpMaxHp(Integer.valueOf(hp), Integer.valueOf(maxhp));
    }

    private void updateHpMaxHp(Integer hp, Integer maxhp) {
        changeHpMpPool(hp, null, maxhp, null, false);
    }

    public void updateMp(int mp) {
        updateMpMaxMp(mp, null);
    }

    public void updateMaxMp(int maxmp) {
        updateMpMaxMp(null, maxmp);
    }

    public void updateMpMaxMp(int mp, int maxmp) {
        updateMpMaxMp(Integer.valueOf(mp), Integer.valueOf(maxmp));
    }

    private void updateMpMaxMp(Integer mp, Integer maxmp) {
        changeHpMpPool(null, mp, null, maxmp, false);
    }

    public void updateMaxHpMaxMp(int maxhp, int maxmp) {
        changeHpMpPool(null, null, maxhp, maxmp, false);
    }

    protected void enforceMaxHpMp() {
        effLock.lock();
        statWlock.lock();
        try {
            if (mp > localmaxmp || hp > localmaxhp) {
                changeHpMp(hp, mp, false);
            }
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public int safeAddHP(int delta) {
        effLock.lock();
        statWlock.lock();
        try {
            if (hp + delta <= 0) {
                delta = -hp + 1;
            }

            addHP(delta);
            return delta;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void addHP(int delta) {
        effLock.lock();
        statWlock.lock();
        try {
            updateHp(hp + delta);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void addMP(int delta) {
        effLock.lock();
        statWlock.lock();
        try {
            updateMp(mp + delta);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void addMPHP(int hpDelta, int mpDelta) {
        effLock.lock();
        statWlock.lock();
        try {
            updateHpMp(hp + hpDelta, mp + mpDelta);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    protected void addMaxMPMaxHP(int hpdelta, int mpdelta, boolean silent) {
        effLock.lock();
        statWlock.lock();
        try {
            changeHpMpPool(null, null, HpMpStats.addMaximum(maxhp, hpdelta),
                    HpMpStats.addMaximum(maxmp, mpdelta), silent);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void addMaxHP(int delta) {
        effLock.lock();
        statWlock.lock();
        try {
            updateMaxHp(HpMpStats.addMaximum(maxhp, delta));
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void addMaxMP(int delta) {
        effLock.lock();
        statWlock.lock();
        try {
            updateMaxMp(HpMpStats.addMaximum(maxmp, delta));
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    private void setStr(int str) {
        this.str = str;
    }

    private void setDex(int dex) {
        this.dex = dex;
    }

    private void setInt(int int_) {
        this.int_ = int_;
    }

    private void setLuk(int luk) {
        this.luk = luk;
    }

    public boolean assignStr(int x) {
        return assignStrDexIntLuk(x, null, null, null);
    }

    public boolean assignDex(int x) {
        return assignStrDexIntLuk(null, x, null, null);
    }

    public boolean assignInt(int x) {
        return assignStrDexIntLuk(null, null, x, null);
    }

    public boolean assignLuk(int x) {
        return assignStrDexIntLuk(null, null, null, x);
    }

    public boolean assignHP(int deltaHP, int deltaAp) {
        return assignHpMp(deltaHP, null, deltaAp);
    }

    public boolean assignMP(int deltaMP, int deltaAp) {
        return assignHpMp(null, deltaMP, deltaAp);
    }

    private boolean assignHpMp(Integer deltaHp, Integer deltaMp, int deltaAp) {
        effLock.lock();
        statWlock.lock();
        try {
            long nextAp = (long) remainingAp - deltaAp;
            long nextUsed = (long) hpMpApUsed + deltaAp;
            long nextHp = (long) maxhp + (deltaHp == null ? 0 : deltaHp);
            long nextMp = (long) maxmp + (deltaMp == null ? 0 : deltaMp);
            if (!validApCount(nextAp) || !validApCount(nextUsed)
                    || !validMaximum(nextHp, 50) || !validMaximum(nextMp, 5)) {
                return false;
            }

            HpMpUpdate update = new HpMpUpdate(null, null,
                    deltaHp == null ? null : (int) nextHp, deltaMp == null ? null : (int) nextMp);
            setHpMpApUsed((int) nextUsed);
            changeStatPool(update, null, null, (int) nextAp, false);
            return true;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    /**
     * Reassigns one AP in a single stat transaction. HP/MP amounts are pool units;
     * primary-stat amounts are always one point. Source minimums are job-specific.
     */
    public boolean applyApReset(Stat from, Stat to, int sourceLoss, int destinationGain, int minHp, int minMp) {
        if (!isApStat(from) || !isApStat(to) || from == to || sourceLoss <= 0 || destinationGain <= 0) {
            return false;
        }

        effLock.lock();
        statWlock.lock();
        try {
            boolean fromPool = from == Stat.MAXHP || from == Stat.MAXMP;
            boolean toPool = to == Stat.MAXHP || to == Stat.MAXMP;
            if (fromPool && hpMpApUsed < 1) {
                return false;
            }

            int[] primary = {str, dex, int_, luk};
            int fromIndex = primaryStatIndex(from), toIndex = primaryStatIndex(to);
            if (fromIndex >= 0) {
                if (primary[fromIndex] < 5) {
                    return false;
                }
                primary[fromIndex]--;
            }
            if (toIndex >= 0) {
                if (primary[toIndex] >= YamlConfig.config.server.MAX_AP) {
                    return false;
                }
                primary[toIndex]++;
            }
            for (int value : primary) {
                if (value < 4 || value > YamlConfig.config.server.MAX_AP) {
                    return false;
                }
            }

            int oldMaxHp = maxhp, oldMaxMp = maxmp, oldPoolAp = hpMpApUsed;
            long nextHp = (long) oldMaxHp - (from == Stat.MAXHP ? sourceLoss : 0)
                    + (to == Stat.MAXHP ? destinationGain : 0);
            long nextMp = (long) oldMaxMp - (from == Stat.MAXMP ? sourceLoss : 0)
                    + (to == Stat.MAXMP ? destinationGain : 0);
            long nextUsed = (long) hpMpApUsed + (toPool ? 1 : 0) - (fromPool ? 1 : 0);
            if (!validMaximum(nextHp, from == Stat.MAXHP ? Math.max(50, minHp) : 50)
                    || !validMaximum(nextMp, from == Stat.MAXMP ? Math.max(5, minMp) : 5)
                    || !validApCount(nextUsed)) {
                return false;
            }

            HpMpUpdate update = fromPool || toPool ? new HpMpUpdate(null, null,
                    from == Stat.MAXHP || to == Stat.MAXHP ? (int) nextHp : null,
                    from == Stat.MAXMP || to == Stat.MAXMP ? (int) nextMp : null) : null;
            Long primaryUpdate = fromIndex >= 0 || toIndex >= 0
                    ? calcStatPoolLong(primary[0], primary[1], primary[2], primary[3]) : null;
            setHpMpApUsed((int) nextUsed);
            changeStatPool(update, primaryUpdate, null, remainingAp, false, true);
            log.debug("AP reset applied: character={} instance={} source={} destination={} "
                            + "trueMaxHP={}->{} trueMaxMP={}->{} clientMaxHP={} clientMaxMP={} "
                            + "poolAP={}->{} availableAP={}",
                    getObjectId(), System.identityHashCode(this), from, to,
                    oldMaxHp, maxhp, oldMaxMp, maxmp, clientmaxhp, clientmaxmp,
                    oldPoolAp, hpMpApUsed, remainingAp);
            return true;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    private static boolean validMaximum(long value, int minimum) {
        return value >= minimum && value <= Integer.MAX_VALUE;
    }

    private static boolean validApCount(long value) {
        return value >= 0 && value <= Integer.MAX_VALUE;
    }

    private static boolean isApStat(Stat stat) {
        return stat != null && (primaryStatIndex(stat) >= 0 || stat == Stat.MAXHP || stat == Stat.MAXMP);
    }

    private static int primaryStatIndex(Stat stat) {
        return switch (stat) {
            case STR -> 0;
            case DEX -> 1;
            case INT -> 2;
            case LUK -> 3;
            default -> -1;
        };
    }

    private static int apAssigned(Integer x) {
        return x != null ? x : 0;
    }

    public boolean assignStrDexIntLuk(int deltaStr, int deltaDex, int deltaInt, int deltaLuk) {
        return assignStrDexIntLuk(Integer.valueOf(deltaStr), Integer.valueOf(deltaDex), Integer.valueOf(deltaInt), Integer.valueOf(deltaLuk));
    }

    private boolean assignStrDexIntLuk(Integer deltaStr, Integer deltaDex, Integer deltaInt, Integer deltaLuk) {
        effLock.lock();
        statWlock.lock();
        try {
            int apUsed = apAssigned(deltaStr) + apAssigned(deltaDex) + apAssigned(deltaInt) + apAssigned(deltaLuk);
            if (apUsed > remainingAp) {
                return false;
            }

            int newStr = str, newDex = dex, newInt = int_, newLuk = luk;
            if (deltaStr != null) {
                newStr += deltaStr;   // thanks Rohenn for noticing an NPE case after "null" started being used
            }
            if (deltaDex != null) {
                newDex += deltaDex;
            }
            if (deltaInt != null) {
                newInt += deltaInt;
            }
            if (deltaLuk != null) {
                newLuk += deltaLuk;
            }

            if (newStr < 4 || newStr > YamlConfig.config.server.MAX_AP) {
                return false;
            }

            if (newDex < 4 || newDex > YamlConfig.config.server.MAX_AP) {
                return false;
            }

            if (newInt < 4 || newInt > YamlConfig.config.server.MAX_AP) {
                return false;
            }

            if (newLuk < 4 || newLuk > YamlConfig.config.server.MAX_AP) {
                return false;
            }

            int newAp = remainingAp - apUsed;
            updateStrDexIntLuk(newStr, newDex, newInt, newLuk, newAp);
            return true;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void updateStrDexIntLuk(int x) {
        updateStrDexIntLuk(x, x, x, x, -1);
    }

    public void changeRemainingAp(int x, boolean silent) {
        effLock.lock();
        statWlock.lock();
        try {
            changeStrDexIntLuk(str, dex, int_, luk, x, silent);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void gainAp(int deltaAp, boolean silent) {
        effLock.lock();
        statWlock.lock();
        try {
            changeRemainingAp(Math.max(0, remainingAp + deltaAp), silent);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    protected void updateStrDexIntLuk(int str, int dex, int int_, int luk, int remainingAp) {
        changeStrDexIntLuk(str, dex, int_, luk, remainingAp, false);
    }

    private void changeStrDexIntLuk(Integer str, Integer dex, Integer int_, Integer luk, int remainingAp, boolean silent) {
        long strDexIntLuk = calcStatPoolLong(str, dex, int_, luk);
        changeStatPool(null, strDexIntLuk, null, remainingAp, silent);
    }

    private void changeStrDexIntLukSp(Integer str, Integer dex, Integer int_, Integer luk, int remainingAp, int remainingSp, int skillbook, boolean silent) {
        long strDexIntLuk = calcStatPoolLong(str, dex, int_, luk);
        long sp = calcStatPoolLong(0, 0, remainingSp, skillbook);
        changeStatPool(null, strDexIntLuk, sp, remainingAp, silent);
    }

    protected void updateStrDexIntLukSp(int str, int dex, int int_, int luk, int remainingAp, int remainingSp, int skillbook) {
        changeStrDexIntLukSp(str, dex, int_, luk, remainingAp, remainingSp, skillbook, false);
    }

    protected void setRemainingSp(int[] sps) {
        effLock.lock();
        statWlock.lock();
        try {
            System.arraycopy(sps, 0, remainingSp, 0, sps.length);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    protected void updateRemainingSp(int remainingSp, int skillbook) {
        changeRemainingSp(remainingSp, skillbook, false);
    }

    protected void changeRemainingSp(int remainingSp, int skillbook, boolean silent) {
        long sp = calcStatPoolLong(0, 0, remainingSp, skillbook);
        changeStatPool(null, null, sp, Short.MIN_VALUE, silent);
    }

    public void gainSp(int deltaSp, int skillbook, boolean silent) {
        effLock.lock();
        statWlock.lock();
        try {
            changeRemainingSp(Math.max(0, remainingSp[skillbook] + deltaSp), skillbook, silent);
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }
}
