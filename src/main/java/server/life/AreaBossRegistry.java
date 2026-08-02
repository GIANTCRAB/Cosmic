/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.life;

import constants.id.MobId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Static catalogue of every boss that the {@code AreaBossTask} respawns
 * periodically on the {@code AREA_BOSS_RESPAWN_INTERVAL} cadence.
 *
 * <p><b>Scope</b>: this registry is for bosses whose design is to be a
 * persistent world occupant that reappears on a timer after being killed.
 * Bosses that should spawn <em>once on player map entry</em> (typically quest
 * bosses like the Astaroth-door family) belong in {@link SpawnOnEntryRegistry}
 * instead — they are intentionally not on a respawn timer.
 *
 * <p>Entries fall into five groups:
 * <ul>
 *   <li>The original 27 entries transcribed one-for-one from the per-boss event
 *       scripts under {@code scripts/event/AreaBoss*.js} (minus the six Door
 *       bosses, which moved to {@link SpawnOnEntryRegistry}). These had no
 *       corresponding WZ SpawnPoint — they were purely force-spawned by JS.</li>
 *   <li>Eight entries for bosses that <em>do</em> have WZ SpawnPoints
 *       (Manon, Griffey, Pianus left &amp; right, Jr. Balrog, Dodo, Lilynouch,
 *       Lyka). Their mob ids appear in {@link #overridableWzMobIds()} so
 *       {@code MapFactory} can deny the WZ SpawnPoint and hand respawn
 *       responsibility to {@code AreaBossTask}.</li>
 *   <li>Sixteen entries for the "weaken area boss" family (Shade, Riche, Snow
 *       Witch, Scholar Ghost, Rurumo, Security Camera, Deet and Roi, Master
 *       Dummy). Same override model. The {@code weakenAreaBoss} debuff
 *       mechanic is per-Monster-instance and is unaffected by respawn source.</li>
 *   <li>Twenty-two entries for long-timer regional field bosses (Black Crow,
 *       Blue Mushmom, Bigfoot on 8 maps, Headless Horseman on 8 maps, three
 *       Showa bosses), five short-timer mini-bosses (Mushmom, Zombie Mushmom,
 *       Rombot, MT-09, Snowman), and five entries for the MV boss room at
 *       Treasure Dungeon (MV plus its four boss-flagged minions).</li>
 * </ul>
 *
 * <p>Two deliberate simplifications vs. the JS originals:
 * <ul>
 *   <li>The JS scripts picked a random X within a per-boss range on each spawn.
 *       Here a single fixed X is used (the midpoint of the original range) so
 *       spawns are deterministic and trivially testable. The Y ground position
 *       is unchanged.</li>
 *   <li>{@code AreaBossSnackBar.js} guarded against both mob ids {@code 8220008}
 *       and {@code 8220009}. Only {@code 8220008} is spawned here, so the guard
 *       checks that id alone.</li>
 * </ul>
 *
 * <p>The GMS-accurate {@code timeMob} hour windows (e.g. "Shade appears 22:00
 * to 14:00") are intentionally ignored: the Java engine already does not read
 * them, and the user-configured {@code AREA_BOSS_RESPAWN_INTERVAL} controls
 * timing uniformly.
 */
public record AreaBossRegistry(List<AreaBossSpawn> spawns, Set<Integer> overridableWzMobIds) {

    /**
     * Mob ids whose WZ SpawnPoints should be denied when
     * {@code AREA_BOSS_OVERRIDE_WZ_RESPAWN} is enabled, so that
     * {@code AreaBossTask} becomes the sole respawner for them.
     */
    static final Set<Integer> DEFAULT_OVERRIDABLE_WZ_MOB_IDS = Set.of(
            // Original 8 WZ bosses
            8180000,  // Manon
            8180001,  // Griffey
            MobId.PIANUS_R,  // Pianus (right)
            MobId.PIANUS_L,  // Pianus (left)
            8130100,  // Jr. Balrog
            8220004,  // Dodo
            8220005,  // Lilynouch
            8220006,  // Lyka
            // Weaken-boss family
            5090000,  // Shade
            5090001,  // Master Dummy
            6090000,  // Riche / Lich
            6090001,  // Snow Witch
            6090003,  // Scholar Ghost
            6090004,  // Rurumo
            7090000,  // Security Camera
            8090000,  // Deet and Roi
            // Long-timer regional field bosses
            9400014,  // Black Crow
            9400205,  // Blue Mushmom
            9400575,  // Bigfoot
            9400549,  // Headless Horseman
            9400120,  // Male Boss (Showa)
            9400121,  // Female Boss (Showa)
            9400122,  // Male Boss / Bodyguard (Showa)
            // Short-timer mini-bosses
            6130101,  // Mushmom
            6300005,  // Zombie Mushmom
            4130103,  // Rombot
            5120100,  // MT-09
            8220001,  // Snowman
            // MV boss room (Treasure Dungeon)
            9400744,  // Crimson Balrog Minion
            9400745,  // Jr. Balrog Minion
            9400746,  // Muscle Stone Minion
            9400747,  // Bain Minion
            9400748   // MV
    );

    private static final List<AreaBossSpawn> DEFAULT_SPAWNS = List.of(
            // --- Original 27 JS-migrated area bosses ---
            new AreaBossSpawn(800020120, 6090002, 560, 50, "From amongst the ruins shrouded by the mists, Bamboo Warrior appears."),
            new AreaBossSpawn(251010102, 5220004, 560, 50, "From the mists surrounding the herb garden, the gargantuous Giant Centipede appears."),
            new AreaBossSpawn(260010201, 3220001, 645, 275, "Deo slowly appeared out of the sand dust."),
            new AreaBossSpawn(107000300, 6220000, 90, 119, "The huge crocodile Dyle has come out from the swamp."),
            new AreaBossSpawn(200010300, 8220000, 208, 83, "Eliza has appeared with a black whirlwind."),
            new AreaBossSpawn(100040105, 5220002, 456, 278, "Faust appeared amidst the blue fog."),
            new AreaBossSpawn(100040106, 5220002, 474, 278, "Faust appeared amidst the blue fog."),
            new AreaBossSpawn(261030000, 8220002, -450, 180, "Kimera has appeared out of the darkness of the underground with a glitter in her eyes."),
            new AreaBossSpawn(110040000, 5220001, -400, 140, "A strange turban shell has appeared on the beach."),
            new AreaBossSpawn(250010504, 7220002, 150, 540, "The ghostly air around here has become stronger. The unpleasant sound of a cat crying can be heard."),
            new AreaBossSpawn(240040401, 8220003, 0, 1125, "Leviathan emerges from the canyon and the cold icy wind blows."),
            new AreaBossSpawn(104000400, 2220000, 279, -496, "A cool breeze was felt when Mano appeared."),
            new AreaBossSpawn(222010310, 7220001, -150, 33, "As the moon light dims, a long fox cry can be heard and the presence of the old fox can be felt"),
            new AreaBossSpawn(230020100, 4220001, -350, 520, "A strange shell has appeared from a grove of seaweed"),
            new AreaBossSpawn(105090310, 8220008, -626, -604, "Slowly, a suspicious food stand opens up on a strangely remote place."),
            new AreaBossSpawn(101030404, 3220000, 800, 1280, "Stumpy has appeared with a stumping sound that rings the Stone Mountain."),
            new AreaBossSpawn(250010304, 7220000, -450, 390, "Tae Roon has appeared with a soft whistling sound."),
            new AreaBossSpawn(220050100, 5220003, -385, 1030, "Tick-Tock Tick-Tock! Timer makes it's presence known."),
            new AreaBossSpawn(220050000, 5220003, -300, 1030, "Tick-Tock Tick-Tock! Timer makes it's presence known."),
            new AreaBossSpawn(220050200, 5220003, 0, 1030, "Tick-Tock Tick-Tock! Timer makes it's presence known."),
            new AreaBossSpawn(221040301, 6220001, -4224, 776, "Zeno has appeared with a heavy sound of machinery."),
            // --- Door bosses (Marbas, Amdusias, Andras, Crocell, Valefor, Astaroth)
            //     are NOT here: they are quest bosses spawned on player map entry, not
            //     periodic respawns. See SpawnOnEntryRegistry. ---
            // --- WZ-SpawnPoint bosses (original 8) ---
            new AreaBossSpawn(240020401, 8180000, -7, 444, "Manon has appeared."),
            new AreaBossSpawn(240020101, 8180001, 0, 432, "Griffey has appeared."),
            new AreaBossSpawn(230040420, MobId.PIANUS_R, 568, 133, "Pianus has appeared."),
            new AreaBossSpawn(230040420, MobId.PIANUS_L, -459, 133, "Pianus has appeared."),
            new AreaBossSpawn(105090900, 8130100, 113, 83, "Jr. Balrog has appeared."),
            new AreaBossSpawn(270010500, 8220004, 280, -938, "Dodo has appeared."),
            new AreaBossSpawn(270020500, 8220005, 66, -921, "Lilynouch has appeared."),
            new AreaBossSpawn(270030500, 8220006, -20, -584, "Lyka has appeared."),
            // --- Weaken-boss family (8 unique mobs, 16 map entries) ---
            //     The 11 reactor-triggered spawns below carry an explicit reactorId
            //     so AreaBossTask can revive the altar/tombstone/etc. when it
            //     re-spawns the boss. The NPC-triggered weaken bosses (Shade,
            //     Security Camera, Deet and Roi) and Master Dummy have no
            //     reactor and use the 5-arg constructor (reactorId empty).
            new AreaBossSpawn(103000105, 5090000, 1552, 181, "Shade has appeared."),
            new AreaBossSpawn(103000202, 5090000, 194, 185, "Shade has appeared."),
            AreaBossSpawn.of(211041100, 6090000, 1377, -32, "Riche has appeared.", 2119000),
            AreaBossSpawn.of(211041200, 6090000, 950, 21, "Riche has appeared.", 2119001),
            AreaBossSpawn.of(211041300, 6090000, 1266, -23, "Riche has appeared.", 2119002),
            AreaBossSpawn.of(211041400, 6090000, 1002, 14, "Riche has appeared.", 2119003),
            AreaBossSpawn.of(211010000, 6090001, 1375, -215, "Snow Witch has appeared.", 2119004),
            AreaBossSpawn.of(211020000, 6090001, 1308, -213, "Snow Witch has appeared.", 2119005),
            AreaBossSpawn.of(211050000, 6090001, 252, -156, "Snow Witch has appeared.", 2119006),
            AreaBossSpawn.of(222010300, 6090003, 1846, 136, "Scholar Ghost has appeared.", 2229009),
            AreaBossSpawn.of(261020200, 6090004, 316, 218, "Rurumo has appeared.", 2619003),
            AreaBossSpawn.of(261020400, 6090004, 273, 147, "Rurumo has appeared.", 2619004),
            AreaBossSpawn.of(261020600, 6090004, 95, 222, "Rurumo has appeared.", 2619005),
            new AreaBossSpawn(261020401, 7090000, 70, 155, "Security Camera has appeared."),
            new AreaBossSpawn(261010102, 8090000, 460, 210, "Deet and Roi has appeared."),
            new AreaBossSpawn(250020300, 5090001, 1249, -485, "Master Dummy has appeared."),
            // --- Long-timer regional field bosses (7 unique mobs, 22 map entries) ---
            new AreaBossSpawn(800020130, 9400014, 1366, 203, "Black Crow has appeared."),
            new AreaBossSpawn(800010100, 9400205, 450, 73, "Blue Mushmom has appeared."),
            new AreaBossSpawn(610010005, 9400575, -316, 202, "Bigfoot has appeared."),
            new AreaBossSpawn(610010012, 9400575, 833, 223, "Bigfoot has appeared."),
            new AreaBossSpawn(610010013, 9400575, 311, 213, "Bigfoot has appeared."),
            new AreaBossSpawn(610010100, 9400575, 472, 172, "Bigfoot has appeared."),
            new AreaBossSpawn(610010101, 9400575, 475, 176, "Bigfoot has appeared."),
            new AreaBossSpawn(610010102, 9400575, 472, 172, "Bigfoot has appeared."),
            new AreaBossSpawn(610010103, 9400575, 472, 172, "Bigfoot has appeared."),
            new AreaBossSpawn(610010104, 9400575, 472, 172, "Bigfoot has appeared."),
            new AreaBossSpawn(610010005, 9400549, 463, 132, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010010, 9400549, 814, 226, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010011, 9400549, 789, 223, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010013, 9400549, 785, 228, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010200, 9400549, -64, 134, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010201, 9400549, -67, 131, "Headless Horseman has appeared."),
            new AreaBossSpawn(610010202, 9400549, -70, 133, "Headless Horseman has appeared."),
            new AreaBossSpawn(682000001, 9400549, 528, 218, "Headless Horseman has appeared."),
            new AreaBossSpawn(801030000, 9400120, 1273, 306, "Male Boss has appeared."),
            new AreaBossSpawn(801040003, 9400121, -37, 151, "Female Boss has appeared."),
            new AreaBossSpawn(801040004, 9400122, 511, 147, "Bodyguard has appeared."),
            new AreaBossSpawn(801040100, 9400122, 409, 149, "Bodyguard has appeared."),
            // --- Short-timer mini-bosses (5 unique mobs, 5 map entries) ---
            new AreaBossSpawn(100000005, 6130101, -649, 204, "Mushmom has appeared."),
            new AreaBossSpawn(105070002, 6300005, 458, 395, "Zombie Mushmom has appeared."),
            new AreaBossSpawn(221020701, 4130103, 148, 1464, "Rombot has appeared."),
            new AreaBossSpawn(221030601, 5120100, -2350, 763, "MT-09 has appeared."),
            new AreaBossSpawn(211040101, 8220001, 356, 262, "The Snowman has appeared."),
            // --- MV boss room at Treasure Dungeon (5 boss-flagged mobs on 1 map) ---
            new AreaBossSpawn(674030300, 9400748, 436, -1366, "MV has appeared."),
            new AreaBossSpawn(674030300, 9400744, 244, -960, "Crimson Balrog Minion has appeared."),
            new AreaBossSpawn(674030300, 9400745, 281, -733, "Jr. Balrog Minion has appeared."),
            new AreaBossSpawn(674030300, 9400746, 337, -442, "Muscle Stone Minion has appeared."),
            new AreaBossSpawn(674030300, 9400747, 311, -210, "Bain Minion has appeared.")
    );

    /**
     * Default registry backed by every transcribed area boss spawn.
     */
    public AreaBossRegistry() {
        this(DEFAULT_SPAWNS, DEFAULT_OVERRIDABLE_WZ_MOB_IDS);
    }

    /**
     * Custom registry, primarily for tests. The given list is copied and made
     * unmodifiable.
     */
    public AreaBossRegistry(List<AreaBossSpawn> spawns) {
        this(spawns, DEFAULT_OVERRIDABLE_WZ_MOB_IDS);
    }

    /**
     * Custom registry with explicit overridable WZ mob id set, for tests that
     * need to vary both.
     */
    public AreaBossRegistry(List<AreaBossSpawn> spawns, Set<Integer> overridableWzMobIds) {
        this.spawns = Collections.unmodifiableList(new ArrayList<>(spawns));
        this.overridableWzMobIds = Set.copyOf(overridableWzMobIds);
    }

    /**
     * @return an unmodifiable view of every registered area boss spawn.
     */
    @Override
    public List<AreaBossSpawn> spawns() {
        return spawns;
    }

    /**
     * @return an unmodifiable set of WZ mob ids that should be denied at
     * SpawnPoint creation time when {@code AREA_BOSS_OVERRIDE_WZ_RESPAWN}
     * is enabled, so that {@code AreaBossTask} is the sole respawner.
     */
    @Override
    public Set<Integer> overridableWzMobIds() {
        return overridableWzMobIds;
    }
}
