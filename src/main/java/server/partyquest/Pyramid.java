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

import client.Character;
import constants.id.ItemId;
import constants.id.MapId;
import net.server.world.Party;
import net.packet.Packet;
import server.ItemInformationProvider;
import server.TimerManager;
import tools.PacketCreator;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

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

    int kill = 0, miss = 0, cool = 0, exp = 0, map;
    byte coolAdd = 5, missSub = 4, decrease = 1;
    short gauge;
    byte rank, skill = 0, stage = 0, buffcount = 0;
    PyramidMode mode;

    ScheduledFuture<?> timer = null;
    ScheduledFuture<?> gaugeSchedule = null;

    public Pyramid(Party party, PyramidMode mode, int mapid) {
        super(party);
        initMode(mode, mapid);
    }

    /**
     * Test-only constructor that avoids the {@code Server.getInstance()} bootstrap performed by
     * {@link PartyQuest#PartyQuest(Party)}. Participants are supplied directly.
     */
    Pyramid(List<Character> participants, PyramidMode mode, int mapid) {
        super(participants);
        initMode(mode, mapid);
    }

    private void initMode(PyramidMode mode, int mapid) {
        this.mode = mode;
        this.map = mapid;

        byte plus = (byte) mode.getMode();
        coolAdd += plus;
        missSub += plus;
        decrease = (byte) decreaseForMode(mode);
    }

    /**
     * Per-mode gauge drain per second. Previously this was a {@code switch} with missing
     * {@code break} statements, causing EASY/NORMAL/HARD to all fall through to the HELL value (3).
     */
    static int decreaseForMode(PyramidMode mode) {
        return switch (mode.getMode()) {
            case 0 -> 1;        // EASY
            case 1, 2 -> 2;     // NORMAL, HARD
            case 3 -> 3;        // HELL
            default -> 1;
        };
    }

    /**
     * Final rank (0=S, 1=A, 2=B, 3=C, 4=D) derived from how many hits/cool the player landed.
     * A full clear (stage 5) is graded against the full tier table; an early exit uses a coarser one.
     */
    static byte computeRank(int stage, int totalKills) {
        if (stage == 5) {
            if (totalKills >= 3000) return 0;
            if (totalKills >= 2000) return 1;
            if (totalKills >= 1500) return 2;
            if (totalKills >= 500) return 3;
            return 4;
        }
        return totalKills >= 2000 ? (byte) 3 : (byte) 4;
    }

    /**
     * EXP awarded for a given rank/mode, plus the per-hit ({@code kill*2}) and per-cool
     * ({@code cool*10}) bonuses that reward aggressive play.
     */
    static int computeExp(byte rank, PyramidMode mode, int kills, int cools) {
        int m = mode.getMode();
        int base = switch (rank) {
            case 0 -> 60500 + 5500 * m;
            case 1 -> 55000 + 5000 * m;
            case 2 -> 46750 + 4250 * m;
            case 3 -> 22000 + 2000 * m;
            default -> 0;
        };
        return base + (kills * 2) + (cools * 10);
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

    public void startGaugeSchedule() {
        if (gaugeSchedule == null) {
            gauge = 100;
            gaugeSchedule = TimerManager.getInstance().register(() -> {
                gauge -= decrease;
                if (gauge <= 0) {
                    warp(MapId.NETTS_PYRAMID);
                    return;
                }
                broadcastGauge();
            }, 1000);
        }
    }

    public void kill() {
        kill++;
        gauge++;
        if (gauge >= 100) {
            gauge = 100;
        }
        broadcastInfo("hit", kill);
        checkBuffs();
    }

    public void cool() {
        cool++;
        gauge = (short) Math.min(100, gauge + coolAdd);
        broadcastInfo("cool", cool);
        checkBuffs();
    }

    public void miss() {
        miss++;
        gauge = (short) Math.max(0, gauge - missSub);
        broadcastInfo("miss", miss);
    }

    public int timer() {
        int value;
        if (stage > 0) {
            value = 180;
        } else {
            value = 120;
        }

        timer = TimerManager.getInstance().schedule(() -> {
            stage++;
            if (stage >= 5) {
                // All five stages cleared -> route to the result map, whose onUserEnter
                // (Massacre_result) calls sendScore. (map + 500 does not exist as a map.)
                warp(MapId.NETTS_PYRAMID);
            } else {
                warp(map + (stage * 100));
            }
        }, SECONDS.toMillis(value));
        broadcastInfo("party", getParticipants().size() > 1 ? 1 : 0);
        broadcastInfo("hit", kill);
        broadcastInfo("miss", miss);
        broadcastInfo("cool", cool);
        broadcastInfo("skill", skill);
        broadcastInfo("laststage", stage);
        startGaugeSchedule();
        return value;
    }

    public void warp(int mapid) {
        for (Character chr : getParticipants()) {
            chr.changeMap(mapid, 0);
        }
        if (gaugeSchedule != null) {
            gaugeSchedule.cancel(false);
            gaugeSchedule = null;
        }
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
    }

    public void broadcastInfo(String info, int amount) {
        for (Character chr : getParticipants()) {
            chr.sendPacket(PacketCreator.getEnergy("massacre_" + info, amount));
            chr.sendPacket(PacketCreator.pyramidGauge(gauge));
        }
    }

    private void broadcastGauge() {
        Packet packet = PacketCreator.pyramidGauge(gauge);
        for (Character chr : getParticipants()) {
            chr.sendPacket(packet);
        }
    }

    /**
     * Resyncs the massacre UI (counters + gauge) to a single player. Used when a party member
     * enters a stage after the leader has already started it.
     */
    public void sendInfo(Character chr) {
        chr.sendPacket(PacketCreator.getEnergy("massacre_party", getParticipants().size() > 1 ? 1 : 0));
        chr.sendPacket(PacketCreator.getEnergy("massacre_hit", kill));
        chr.sendPacket(PacketCreator.getEnergy("massacre_miss", miss));
        chr.sendPacket(PacketCreator.getEnergy("massacre_cool", cool));
        chr.sendPacket(PacketCreator.getEnergy("massacre_skill", skill));
        chr.sendPacket(PacketCreator.getEnergy("massacre_laststage", stage));
        chr.sendPacket(PacketCreator.pyramidGauge(gauge));
    }

    public boolean useSkill() {
        if (skill < 1) {
            return false;
        }

        skill--;
        broadcastInfo("skill", skill);
        return true;
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
            rank = computeRank(stage, totalkills);
            exp = computeExp(rank, mode, kill, cool);
        }
        chr.sendPacket(PacketCreator.pyramidScore(rank, exp));
        chr.gainExp(exp, true, true);
    }
}
