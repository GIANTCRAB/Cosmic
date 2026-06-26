/*
    This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
               Matthias Butz <matze@odinms.de>
               Jan Christian Meyer <vimes@odinms.de>

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

package server.partyquest;

import client.BuffStat;
import client.Character;
import client.Skill;
import config.YamlConfig;
import constants.id.ItemId;
import constants.id.MapId;
import constants.id.MobId;
import net.server.channel.Channel;
import net.server.world.Party;
import net.packet.Packet;
import server.ItemInformationProvider;
import server.TimerManager;
import server.life.LifeFactory;
import server.life.Monster;
import server.maps.MapleMap;
import tools.PacketCreator;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author kevintjuh93
 */
public class Pyramid extends PartyQuest {
    public enum PyramidMode {
        EASY(0), NORMAL(1), HARD(2), HELL(3);
        final int mode;

        PyramidMode(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }
    }

    /**
     * Classification of a single hit dealt to a monster inside Nett's Pyramid.
     * Used both by {@link server.life.Monster} (to dispatch the hook) and by unit tests.
     */
    public enum HitType {
        MISS, KILL, COOL
    }

    // Scoring counters. kill/miss/cool/skill/buffcount are touched only on the net thread
    // (attack / use-skill handlers); stage is also written from the TimerManager stage timer, so it
    // is volatile. gauge is written from BOTH threads (net thread on kill/cool/miss, TimerManager
    // on the drain) -> AtomicInteger.
    int kill = 0, miss = 0, cool = 0, exp = 0, decrease = 1;
    final int baseMapId;
    byte coolAdd = 5, missSub = 4;
    final AtomicInteger gauge = new AtomicInteger();
    final AtomicInteger count = new AtomicInteger();
    byte rank;
    int skill = 0;
    volatile byte stage = 0;
    int buffcount = 0;
    PyramidMode mode;

    // The current disposable map instance and the channel that minted it (for minting/disposing the
    // stage maps). Disposable maps are fresh per run -- they carry no leftover mobs and are NOT
    // auto-respawned by the global RespawnTask (which only walks the cached maps), so mob spawning
    // is entirely under this class's control.
    Channel cs;
    MapleMap map;

    // Scheduled tasks. Every read/write is guarded by lifecycleLock.
    ScheduledFuture<?> timer = null;
    ScheduledFuture<?> gaugeSchedule = null;
    ScheduledFuture<?> respawnTask = null;
    private final Object lifecycleLock = new Object();

    public Pyramid(Party party, PyramidMode mode, int baseMapId, Channel cs) {
        super(party);
        this.cs = cs;
        this.baseMapId = baseMapId;
        initMode(mode);
    }

    /**
     * Test-only constructor that avoids the {@code Server.getInstance()} bootstrap performed by
     * {@link PartyQuest#PartyQuest(Party)}. Participants are supplied directly; {@code cs} is null
     * (stage advance, which needs it, is not exercised by pure logic tests).
     */
    Pyramid(List<Character> participants, PyramidMode mode, int baseMapId) {
        super(participants);
        this.baseMapId = baseMapId;
        initMode(mode);
    }

    private void initMode(PyramidMode mode) {
        this.mode = mode;

        byte plus = (byte) mode.getMode();
        coolAdd += plus;
        missSub += plus;
        decrease = decreaseForMode(mode, getParticipants().size());
    }

    /**
     * Per-second gauge drain. The base drain scales with difficulty; in a party ({@code partySize > 1})
     * the drain additionally increases by the party size, so a full party depletes the gauge much
     * faster. Solo ({@code partySize <= 1}) uses the base drain only.
     */
    static int decreaseForMode(PyramidMode mode, int partySize) {
        int base = switch (mode.getMode()) {
            case 0 -> 1;        // EASY
            case 1, 2 -> 2;     // NORMAL, HARD
            case 3 -> 3;        // HELL
            default -> 1;
        };
        return partySize > 1 ? base + partySize : base;
    }

    /**
     * Final rank (0=S, 1=A, 2=B, 3=C, 4=D) derived from how many hits/cool the player landed.
     * A full clear (stage 5) is graded against the full tier table; an early exit uses a coarser one.
     * Kill requirements are scaled up by {@code mode.getMode() + 1} so harder modes demand more
     * kills for the same rank (EASY is the baseline, with a multiplier of 1).
     */
    static byte computeRank(int stage, int totalKills, PyramidMode mode) {
        int scale = mode.getMode() + 1;
        if (stage == 5) {
            if (totalKills >= 3000 * scale) return 0;
            if (totalKills >= 2000 * scale) return 1;
            if (totalKills >= 1500 * scale) return 2;
            if (totalKills >= 500 * scale) return 3;
            return 4;
        }
        return totalKills >= 2000 * scale ? (byte) 3 : (byte) 4;
    }

    /**
     * EXP awarded for a given rank/mode, scaled by the world-configured {@code expRate}, plus the
     * per-hit ({@code kill*(m+1)*2}) and per-cool ({@code cool*(m+1)*10}) bonuses that reward
     * aggressive play.
     */
    static int computeExp(byte rank, PyramidMode mode, int kills, int cools, int expRate) {
        int m = mode.getMode();
        int base = switch (rank) {
            case 0 -> 60500 + 55000 * m;
            case 1 -> 55000 + 50000 * m;
            case 2 -> 46750 + 42500 * m;
            case 3 -> 22000 + 20000 * m;
            default -> 0;
        };
        return (base + (kills * (m + 1) * 2) + (cools * (m + 1) * 10)) * expRate;
    }

    /**
     * Decides whether a single monster hit counts as a miss, a normal kill, or a "cool" (a heavy
     * hit landed on a Pharaoh Jr. Yeti that carries {@code coolDamage} stats).
     *
     * @param damage     damage dealt this hit ({@code <= 0} means the attack did not connect)
     * @param coolDamage the mob's {@code coolDamage} threshold (0 if the mob has none)
     * @param coolProb   the mob's {@code coolDamageProb} percent chance (0 if the mob has none)
     * @param roll       a 0..100 roll (caller supplies {@code Math.random()*100})
     */
    public static HitType classifyHit(int damage, int coolDamage, int coolProb, double roll) {
        if (damage <= 0) {
            return HitType.MISS;
        }
        if (coolDamage > 0 && damage >= coolDamage && roll < coolProb) {
            return HitType.COOL;
        }
        return HitType.KILL;
    }

    // ----------------------------------------------------------------------
    // Yeti spawning (stages 4 & 5)
    // ----------------------------------------------------------------------

    /**
     * Transparent Pharaoh Yeti evasion, as defined in {@code 9700023.img.xml} ({@code eva=500}).
     * This is what makes the yeti near-unhittable: the client resolves acc-vs-eva itself and reports
     * a 0-damage miss on failure. The server is client-authoritative here, so the value is only used
     * to size the counter-debuff applied by the "Rage of Pharaoh" massacre skill.
     */
    static final int YETI_EVASION = 500;

    /**
     * How long the massacre skill's accuracy-empowerment (AVOID debuff on yetis) lasts, in
     * milliseconds. Long enough to land a few hits, short enough that the yeti reverts to its
     * evasive state between charges. Decoupled from the skill's own WZ duration (which is a
     * player-buff duration, not a monster-debuff one).
     */
    static final long MASSACRE_EMPOWER_DURATION_MILLIS = 10_000L;

    /**
     * Number of Transparent Pharaoh Yetis to spawn at the start of a stage. Yetis only appear on the
     * final two stages, and the count scales with difficulty so harder modes spawn more harassers.
     * N.B. the in-class {@code stage} field is 0-indexed (0..4 map to the displayed stages 1..5), so
     * the "last two stages" are field values 3 and 4:
     * <ul>
     *   <li>Field 0-2 (displayed 1-3): never ({@code 0})</li>
     *   <li>Field 3 (displayed 4): EASY=0, NORMAL=1, HARD=2, HELL=3</li>
     *   <li>Field 4 (displayed 5): EASY=1, NORMAL=2, HARD=3, HELL=4</li>
     * </ul>
     * The finale always spawns one more yeti than the penultimate stage.
     */
    static int yetisForStage(byte stage, PyramidMode mode) {
        if (stage < 3) {
            return 0;
        }
        return mode.getMode() + (stage >= 4 ? 1 : 0);
    }

    /**
     * Picks which "approaching yeti" scare animation ({@code 0..4}) to broadcast, if any, for a given
     * combined kill+cool total. Mirrors GMS: the highest tier {@code i} in {@code 5..1} whose
     * {@code i*100} boundary the total is an exact positive multiple of, subject to a 20% probability
     * roll. Returns {@code -1} when no scare should fire.
     *
     * <p>The roll is applied once (upstream re-rolls per tier, which only differs at totals that are
     * common multiples of several boundaries -- e.g. 2000 -- where this returns the highest tier
     * instead of potentially falling through to a lower one). The single-roll form keeps the helper
     * fully deterministic given its inputs.
     *
     * @param killPlusCool the combined kill+cool count (must be {@code > 0} to ever scare)
     * @param roll         a {@code 0..100} roll (caller supplies {@code Math.random()*100})
     */
    static int yetiScareTier(int killPlusCool, double roll) {
        if (killPlusCool <= 0 || roll >= 20) {
            return -1;
        }
        for (int i = 5; i >= 1; i--) {
            if (killPlusCool % (i * 100) == 0) {
                return i - 1;
            }
        }
        return -1;
    }

    /**
     * The {@link MonsterStatus#AVOID} value applied to yetis when the "Rage of Pharaoh" massacre
     * skill is used, sized to exactly cancel the yeti's {@link #YETI_EVASION} so the client stops
     * reporting misses for the skill's duration. Negative because a positive AVOID value raises a
     * mob's avoidability (see {@code MobSkill} EVA handling), so negation lowers it.
     */
    static int yetiAvoidDebuffValue() {
        return -YETI_EVASION;
    }

    // ----------------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------------

    /**
     * Entry: force-warp every participant into a fresh disposable pyramid map, then start the stage
     * (clock + "killing/first" start effects + massacre UI init + Act Gauge drain + stage timer +
     * mob respawn task). The disposable map carries no leftover mobs and is not auto-respawned, so
     * spawning is fully controlled here.
     */
    public void startEntry(MapleMap entryMap) {
        this.map = entryMap;
        for (Character chr : getParticipants()) {
            chr.forceChangeMap(entryMap, entryMap.getPortal(0));
        }
        commenceStage();
    }

    /**
     * Starts (or restarts, on stage advance) the current stage: (re)starts the gauge drain (which
     * resets the hidden gauge to 100 and the broadcast count to 0), then sends each participant the
     * stage countdown + "killing/first" start effects + the full massacre UI sync, and finally
     * schedules the stage timer and the mob respawn task. Mirrors GMS's stage-entry sequence.
     */
    private void commenceStage() {
        synchronized (lifecycleLock) {
            if (map == null) {
                return;
            }
            int time = (stage > 0) ? 180 : 120;
            startGaugeSchedule();
            for (Character chr : getParticipants()) {
                chr.sendPacket(PacketCreator.getClock(time));
                chr.sendPacket(PacketCreator.showEffect("killing/first/number/" + (stage + 1)));
                chr.sendPacket(PacketCreator.showEffect("killing/first/stage"));
                chr.sendPacket(PacketCreator.showEffect("killing/first/start"));
                sendInfo(chr);
            }
            scheduleStageAdvance();
            spawnYetis(yetisForStage(stage, mode));
            respawnTask = TimerManager.getInstance().register(() -> {
                MapleMap m = map;
                if (m != null) {
                    m.respawn();
                }
            }, YamlConfig.config.server.RESPAWN_INTERVAL);
        }
    }

    /**
     * No-op retained for the {@code MovePlayerHandler} hook. The lifecycle is entry-driven
     * ({@link #commenceStage} on each stage entry), so movement no longer gates anything.
     */
    public void onPlayerMove(Character chr) {
    }

    private void startGaugeSchedule() {
        if (gaugeSchedule == null) {
            gauge.set(100);
            count.set(0);
            gaugeSchedule = TimerManager.getInstance().register(() -> {
                int g = gauge.addAndGet(-decrease);
                if (g <= 0) {
                    warpOut();
                    return;
                }
            }, 1000, 1000);
        }
    }

    private void scheduleStageAdvance() {
        int value = (stage > 0) ? 180 : 120;
        timer = TimerManager.getInstance().schedule(() -> {
            byte s = (byte) (stage + 1);
            stage = s;
            if (s >= 5) {
                // All five stages cleared -> route to the result map, whose onUserEnter
                // (Massacre_result) calls sendScore then clears the PQ. (map + 500 is not a map.)
                warpOut();
            } else {
                advanceStage(baseMapId + (s * 100));
            }
        }, SECONDS.toMillis(value));
    }

    /**
     * Move every participant to the next stage's fresh disposable map, dispose the previous one,
     * and commence the next stage (effects + UI sync + gauge/timer/respawn restart).
     */
    void advanceStage(int stageMapId) {
        synchronized (lifecycleLock) {
            if (map == null || cs == null) {
                return;
            }
            MapleMap oldMap = this.map;
            cancelRunTasks();
            MapleMap next = cs.getMapFactory().getDisposableMap(stageMapId);
            this.map = next;
            for (Character chr : getParticipants()) {
                chr.forceChangeMap(next, next.getPortal(0));
            }
            if (oldMap != null) {
                oldMap.dispose();
            }
            commenceStage();
        }
    }

    /**
     * Gauge depleted or all stages cleared: end the run, route players to the shared result map
     * (whose onUserEnter {@code Massacre_result} calls {@link #sendScore} and then clears the PQ),
     * drop the long-lived Pharaoh's Blessing buffs, and dispose the disposable map. The PQ is
     * intentionally NOT cleared here so the result script can still read the score.
     */
    void warpOut() {
        synchronized (lifecycleLock) {
            if (map == null) {
                return;
            }
            cancelRunTasks();
            MapleMap oldMap = this.map;
            this.map = null;
            for (Character chr : getParticipants()) {
                cancelPyramidBuffs(chr);
                chr.changeMap(MapId.NETTS_PYRAMID, 0);
            }
            if (oldMap != null) {
                oldMap.dispose();
            }
        }
    }

    /**
     * Forfeit / shutdown cleanup: cancel every task and dispose the disposable map. Does not warp --
     * the caller is responsible for routing the player out.
     */
    public void dispose() {
        synchronized (lifecycleLock) {
            cancelRunTasks();
            for (Character chr : getParticipants()) {
                cancelPyramidBuffs(chr);
            }
            MapleMap oldMap = this.map;
            this.map = null;
            if (oldMap != null) {
                oldMap.dispose();
            }
        }
    }

    private void cancelRunTasks() {
        if (gaugeSchedule != null) {
            gaugeSchedule.cancel(false);
            gaugeSchedule = null;
        }
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
        if (respawnTask != null) {
            respawnTask.cancel(false);
            respawnTask = null;
        }
    }

    /**
     * Drops the Pharaoh's Blessing buffs granted by {@link #checkBuffs}. They are item effects that
     * use the {@link BuffStat#BERSERK}/{@link BuffStat#BOOSTER} stats and carry a long (40-minute)
     * duration, so they would otherwise linger after the run ends. The stats are cancelled directly
     * (rather than via {@code cancelEffect(itemId)}) because that sourceid-keyed path does not
     * reliably clear this shared-stat buff at runtime. While the blessing is active it supersedes
     * any class Booster/Berserk on those same stats, so stripping the stat on exit only drops the
     * (already-overwritten) blessing -- the player simply re-casts any class buff afterwards.
     */
    private static void cancelPyramidBuffs(Character chr) {
        chr.cancelBuffStats(BuffStat.BERSERK);
        chr.cancelBuffStats(BuffStat.BOOSTER);
    }

    @Override
    public void onParticipantDetach(Character chr) {
        cancelPyramidBuffs(chr);
    }

    // ----------------------------------------------------------------------
    // Gameplay (gauge / counters)
    // ----------------------------------------------------------------------

    public void kill() {
        kill++;
        gauge.updateAndGet(g -> Math.min(100, g + 1));
        count.incrementAndGet();
        broadcastInfo("hit", kill);
        checkBuffs();
        maybeBroadcastYetiScare();
    }

    public void cool() {
        cool++;
        gauge.updateAndGet(g -> Math.min(100, g + coolAdd));
        count.incrementAndGet();
        broadcastInfo("cool", cool);
        checkBuffs();
        maybeBroadcastYetiScare();
    }

    public void miss() {
        miss++;
        gauge.updateAndGet(g -> Math.max(0, g - missSub));
        broadcastInfo("miss", miss);
    }

    public void broadcastInfo(String info, int amount) {
        Packet energy = PacketCreator.getEnergy("massacre_" + info, amount);
        Packet gaugePacket = PacketCreator.pyramidGauge(count.get());
        for (Character chr : participantsOnPyramidMap()) {
            chr.sendPacket(energy);
            chr.sendPacket(gaugePacket);
        }
    }

    /**
     * Participants currently standing on a Nett's Pyramid map. (Gameplay broadcasts happen after the
     * fade, so by then everyone has loaded the field.)
     */
    private List<Character> participantsOnPyramidMap() {
        List<Character> onMap = new ArrayList<>();
        for (Character chr : getParticipants()) {
            if (MapId.isNettsPyramid(chr.getMapId())) {
                onMap.add(chr);
            }
        }
        return onMap;
    }

    /**
     * Resyncs the massacre UI (counters + gauge) to a single player. Sent on each stage entry.
     */
    public void sendInfo(Character chr) {
        chr.sendPacket(PacketCreator.getEnergy("massacre_party", getParticipants().size() > 1 ? 1 : 0));
        chr.sendPacket(PacketCreator.getEnergy("massacre_hit", kill));
        chr.sendPacket(PacketCreator.getEnergy("massacre_miss", miss));
        chr.sendPacket(PacketCreator.getEnergy("massacre_cool", cool));
        chr.sendPacket(PacketCreator.getEnergy("massacre_skill", skill));
        chr.sendPacket(PacketCreator.getEnergy("massacre_laststage", stage));
        chr.sendPacket(PacketCreator.pyramidGauge(count.get()));
    }

    public boolean useSkill() {
        if (skill < 1) {
            return false;
        }

        skill--;
        broadcastInfo("skill", skill);
        return true;
    }

    // ----------------------------------------------------------------------
    // Yeti gameplay (spawn / scare / massacre empowerment)
    // ----------------------------------------------------------------------

    /**
     * Spawns {@code count} Transparent Pharaoh Yetis ({@code 9700023}) onto the current disposable
     * map. Each yeti carries its own {@code removeAfter} (90s) and revive chain in WZ, so it cycles
     * itself -- this method only seeds the initial wave at stage entry. Spawn position mirrors GMS
     * (the player's position); yetis are placed at the first on-map participant, snapping to ground
     * via {@link MapleMap#spawnMonsterOnGroundBelow}. A no-op when {@code count <= 0} or the run has
     * no map/participants yet (e.g. during tests).
     */
    void spawnYetis(int count) {
        if (count <= 0) {
            return;
        }
        final MapleMap m = map;
        if (m == null) {
            return;
        }
        Point at = yetiSpawnOrigin();
        if (at == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Monster yeti = LifeFactory.getMonster(MobId.TRANSPARENT_PHARAOH_YETI_SPAWN);
            if (yeti != null) {
                m.spawnMonsterOnGroundBelow(yeti, at);
            }
        }
    }

    /**
     * Broadcasts the "approaching yeti" scare animation when the combined kill+cool total crosses a
     * boundary (every {@code i*100}, highest {@code i} first) with a 20% probability. Driven by the
     * pure {@link #yetiScareTier}; the randomness is injected here so the decision itself stays
     * deterministic and unit-testable.
     */
    private void maybeBroadcastYetiScare() {
        int tier = yetiScareTier(kill + cool, Math.random() * 100);
        if (tier >= 0) {
            broadcastYetiScare(tier);
        }
    }

    /**
     * Sends the {@code killing/yeti<tier>} effect to every participant on a pyramid map.
     */
    void broadcastYetiScare(int tier) {
        Packet effect = PacketCreator.showEffect("killing/yeti" + tier);
        for (Character chr : participantsOnPyramidMap()) {
            chr.sendPacket(effect);
        }
    }

    /**
     * Empowers every alive Pharaoh Yeti on the current map to be hittable for the massacre skill's
     * duration, by applying an AVOID debuff sized to cancel the yeti's evasion. Called by
     * {@code SpecialMoveHandler} on a successful {@link #useSkill()}. The value comes from the pure
     * {@link #yetiAvoidDebuffValue}; only the application (map iteration + {@link Monster#applyStatus}
     * scheduling) lives here, so it is not itself unit-tested.
     */
    public void empowerYetisForMassacre(Character caster, Skill massacreSkill) {
        final MapleMap m = map;
        if (m == null || caster == null) {
            return;
        }
        MonsterStatusEffect debuff = new MonsterStatusEffect(
                Collections.singletonMap(MonsterStatus.AVOID, yetiAvoidDebuffValue()),
                massacreSkill, null, false);
        for (Monster mob : m.getAllMonsters()) {
            if (mob.isAlive() && MobId.isPyramidYeti(mob.getId())) {
                mob.applyStatus(caster, debuff, false, MASSACRE_EMPOWER_DURATION_MILLIS);
            }
        }
    }
    /**
     * Spawn origin for yetis: the position of the first participant currently on a pyramid map, or
     * {@code null} if none are loaded yet.
     */
    private Point yetiSpawnOrigin() {
        for (Character chr : participantsOnPyramidMap()) {
            return chr.getPosition();
        }
        return null;
    }

    public void checkBuffs() {
        int total = (kill + cool);
        if (buffcount == 0 && total >= 250) {
            buffcount++;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (Character chr : getParticipants()) {
                ii.getItemEffect(ItemId.PHARAOHS_BLESSING_1).applyTo(chr);
            }

        } else if (buffcount == 1 && total >= 500) {
            buffcount++;
            skill++;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (Character chr : getParticipants()) {
                chr.sendPacket(PacketCreator.getEnergy("massacre_skill", skill));
                ii.getItemEffect(ItemId.PHARAOHS_BLESSING_2).applyTo(chr);
            }
        } else if (buffcount == 2 && total >= 1000) {
            buffcount++;
            skill++;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (Character chr : getParticipants()) {
                chr.sendPacket(PacketCreator.getEnergy("massacre_skill", skill));
                ii.getItemEffect(ItemId.PHARAOHS_BLESSING_3).applyTo(chr);
            }
        } else if (buffcount == 3 && total >= 1500) {
            buffcount++;
            skill++;
            broadcastInfo("skill", skill);
        } else if (buffcount == 4 && total >= 2000) {
            buffcount++;
            skill++;
            ItemInformationProvider ii = ItemInformationProvider.getInstance();
            for (Character chr : getParticipants()) {
                chr.sendPacket(PacketCreator.getEnergy("massacre_skill", skill));
                ii.getItemEffect(ItemId.PHARAOHS_BLESSING_4).applyTo(chr);
            }
        } else if (buffcount == 5 && total >= 2500) {
            buffcount++;
            skill++;
            broadcastInfo("skill", skill);
        } else if (buffcount == 6 && total >= 3000) {
            buffcount++;
            skill++;
            broadcastInfo("skill", skill);
        }
    }

    public void sendScore(Character chr) {
        if (exp == 0) {
            int totalkills = (kill + cool);
            rank = computeRank(stage, totalkills, mode);
            exp = computeExp(rank, mode, kill, cool, chr.getWorldServer().getExpRate());
        }
        chr.sendPacket(PacketCreator.pyramidScore(rank, exp));
        chr.gainExp(exp, true, true);
    }
}
