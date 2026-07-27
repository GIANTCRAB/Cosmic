/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.partyquest;

import client.Character;
import constants.id.ItemId;
import constants.id.MapId;
import constants.id.MobId;
import net.server.channel.Channel;
import server.TimerManager;
import server.life.LifeFactory;
import server.life.Monster;
import server.life.MonsterDropEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Pharaoh Yeti's Tomb -- the solo reward room reached by spending a Pharaoh Yeti gem at Duarte after
 * clearing Nett's Pyramid. Unlike the battle-stage {@link Pyramid}, the tomb has no gauge, no stages,
 * and no scoring: a single {@link MobId#PHARAOH_JR_YETI} (the 1-HP "Pharaoh's clone") spawns, and on
 * death it drops the {@link ItemId#PHARAOHS_TREASURE_CHEST chest} matching the difficulty the player
 * cleared to earn the gem. The player then opens that chest (a WZ reward-table use-item) for a chance
 * at the Pharaoh Belt (and, on HELL, the Immortal Pharaoh Belt).
 *
 * <p>Each entry mints a fresh {@link net.server.channels... disposable map} so two players spending
 * gems never share a room. The instance owns its map's lifecycle: a poll task disposes the map (and
 * cancels itself) once the player leaves, so the tomb's item/aggro monitors do not leak.
 *
 * <p>All decision logic (chest selection, yeti count, the drop list) is extracted as pure
 * package-private statics so the reward chain is unit-testable without standing up the server, WZ,
 * or timers -- mirroring the convention used by {@code Pyramid} ({@code gemForMode},
 * {@code yetisForStage}, ...). Only {@link #enter()} and {@link #dispose()} touch I/O.
 */
public class PharaohTomb {
    /**
     * Fixed spawn position for the Jr. Yeti, in map coordinates of {@link MapId#TOMB_OF_PHARAOH_YETI}.
     * Sits on the main ground (y=125 per the WZ footholds); {@link MapleMap#spawnMonsterOnGroundBelow}
     * snaps the y to the foothold beneath, so only the x (a short offset from the player spawn at 0)
     * is meaningful. Kept deterministic rather than player-relative so spawning is independent of the
     * (post-warp, client-fading) player position and is straightforward to assert in tests.
     */
    static final Point YETI_SPAWN_POINT = new Point(150, 100);

    /**
     * How often the disposal poll runs. Matches {@code MapMonitor}'s 5s cadence. Only fires while the
     * player is inside; {@link #dispose()} cancels it eagerly on shutdown/exit.
     */
    private static final long DISPOSE_POLL_MILLIS = 5000L;

    private final Character chr;
    private final Channel cs;
    private final Pyramid.PyramidMode mode;

    // Guarded by the tomb's own lock: enter() and the dispose poll can race (poll fires on the
    // TimerManager thread while dispose() may be invoked from the net thread).
    private MapleMap map;
    private ScheduledFuture<?> disposeMonitor;
    private final Object lock = new Object();

    public PharaohTomb(Character chr, Channel cs, Pyramid.PyramidMode mode) {
        this.chr = chr;
        this.cs = cs;
        this.mode = mode;
    }

    public Pyramid.PyramidMode getMode() {
        return mode;
    }

    /**
     * Entry: mint a fresh disposable tomb map, seed it with the mode-matched Jr. Yeti, warp the player
     * in, and start the disposal poll. The map is acquired via {@code getDisposableMap} (a brand-new
     * untracked instance every call), so concurrent tomb entries never collide.
     */
    public void enter() {
        MapleMap tombMap = cs.getMapFactory().getDisposableMap(MapId.TOMB_OF_PHARAOH_YETI);
        synchronized (lock) {
            this.map = tombMap;
        }
        spawnJrYeti(tombMap);
        chr.forceChangeMap(tombMap, tombMap.getPortal(0));
        synchronized (lock) {
            disposeMonitor = TimerManager.getInstance().register(this::disposeIfEmpty, DISPOSE_POLL_MILLIS);
        }
    }

    /**
     * Spawns {@link #yetisToSpawnInTomb()} Pharaoh Jr. Yetis onto the tomb map, each carrying the
     * mode-matched chest as its {@link Monster#setDropOverride(List) drop override}. Split out from
     * {@link #enter()} so the spawn step is independently exercisable in tests.
     */
    void spawnJrYeti(MapleMap tombMap) {
        int count = yetisToSpawnInTomb();
        List<MonsterDropEntry> drops = tombDropList(chestForMode(mode));
        for (int i = 0; i < count; i++) {
            Monster yeti = createJrYeti();
            if (yeti != null) {
                yeti.setDropOverride(drops);
                tombMap.spawnMonsterOnGroundBelow(yeti, YETI_SPAWN_POINT);
            }
        }
    }

    /**
     * Builds a fresh Pharaoh Jr. Yeti monster. The lone seam that touches WZ ({@link LifeFactory}),
     * extracted and left non-final so tests in this package can override it with a mock yeti and
     * exercise {@link #enter()} / {@link #spawnJrYeti(MapleMap)} deterministically, without boot
     * strapping WZ or depending on static-mock ordering.
     */
    Monster createJrYeti() {
        return LifeFactory.getMonster(MobId.PHARAOH_JR_YETI);
    }

    /**
     * Poll callback: dispose the tomb once the player has left (killed the yeti, looted the chest, and
     * walked out the exit portal -- or disconnected). Runs on the TimerManager thread, hence the lock.
     */
    private void disposeIfEmpty() {
        MapleMap m;
        synchronized (lock) {
            m = map;
        }
        if (m != null && m.getCharacters().isEmpty()) {
            dispose();
        }
    }

    /**
     * Shutdown / post-exit cleanup: cancel the disposal poll, kill any leftover yeti, clear drops, and
     * dispose the disposable map (which cancels its item/aggro monitors). Safe to call repeatedly.
     */
    public void dispose() {
        synchronized (lock) {
            if (disposeMonitor != null) {
                disposeMonitor.cancel(false);
                disposeMonitor = null;
            }
            MapleMap m = map;
            map = null;
            if (m != null) {
                m.killAllMonsters();
                m.clearDrops();
                m.dispose();
            }
        }
    }

    // ----------------------------------------------------------------------
    // Pure decision helpers (unit-tested; no I/O)
    // ----------------------------------------------------------------------

    /**
     * The Pharaoh's Treasure Chest variant that should drop in the tomb for a run cleared under
     * {@code mode}. Only HELL grants the upgraded chest ({@link ItemId#PHARAOHS_TREASURE_CHEST_HELL}),
     * whose WZ reward table is the sole source of the Immortal Pharaoh Belt (1132013); every other
     * difficulty yields the standard chest ({@link ItemId#PHARAOHS_TREASURE_CHEST}), which can only
     * produce the Pharaoh Belt (1132012). The gem the player spent to enter is the non-forgeable proof
     * of which difficulty they cleared, so the mode is authoritative here.
     */
    static int chestForMode(Pyramid.PyramidMode mode) {
        return mode == Pyramid.PyramidMode.HELL ? ItemId.PHARAOHS_TREASURE_CHEST_HELL : ItemId.PHARAOHS_TREASURE_CHEST;
    }

    /**
     * Number of Pharaoh Jr. Yetis to spawn per tomb entry. Fixed at 1: matches Duarte's dialog
     * ("defeating the Pharaoh Jr. Yeti") and the 1-gem-1-chest reward economy (each gem is earned by
     * clearing the PQ once). The Jr. Yeti is a 1-HP trivial kill, so count scales difficulty-free.
     */
    static int yetisToSpawnInTomb() {
        return 1;
    }

    /**
     * The drop list attached to each spawned Jr. Yeti: a single guaranteed-drop entry for the given
     * chest. {@code chance = 1_000_000} exceeds the {@code Randomizer.nextInt(999999)} roll ceiling in
     * {@code MapleMap.dropItemsFromMonsterOnMap} even at 1x drop rate, so the chest always drops exactly
     * once (min = max = 1), independent of the player's drop-rate buffs. {@code questid = 0} routes it
     * as a normal (non-quest) drop.
     */
    static List<MonsterDropEntry> tombDropList(int chestItemId) {
        return List.of(new MonsterDropEntry(chestItemId, 1_000_000, 1, 1, (short) 0));
    }
}
