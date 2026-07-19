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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the static catalogue of area boss spawns that {@link AreaBossTask}
 * iterates on every tick. Covers five groups: the 21 original JS-migrated
 * bosses (excluding the 6 Door bosses, which moved to {@link SpawnOnEntryRegistry}),
 * 8 WZ-SpawnPoint bosses, 16 "weaken" bosses, 22 long-timer regional bosses,
 * 5 short-timer mini-bosses, and 5 MV boss-room entries. This test pins the
 * count, validates every entry, and prevents accidental duplication or drift.
 */
class AreaBossRegistryTest {

    private static final int EXPECTED_ENTRY_COUNT = 77;
    private static final int EXPECTED_OVERRIDABLE_WZ_COUNT = 33;

    private final AreaBossRegistry registry = new AreaBossRegistry();

    @Test
    void containsAllAreaBossEntries() {
        assertEquals(EXPECTED_ENTRY_COUNT, registry.spawns().size());
    }

    @Test
    void everyEntryHasValidIdsAndMessage() {
        for (AreaBossSpawn s : registry.spawns()) {
            assertTrue(s.mapId() > 0, "mapId must be positive: " + s);
            assertTrue(s.mobId() > 0, "mobId must be positive: " + s);
            assertFalse(s.message().isBlank(), "message must not be blank: " + s);
        }
    }

    @Test
    void noDuplicateMapMobPairs() {
        Set<String> seen = new HashSet<>();
        for (AreaBossSpawn s : registry.spawns()) {
            String key = s.mapId() + ":" + s.mobId();
            assertTrue(seen.add(key), "duplicate (mapId, mobId) pair: " + key);
        }
    }

    @Test
    void knownMigratedScriptsArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 104000400, 2220000), "Mano should be registered");
        assertTrue(contains(spawns, 110040000, 5220001), "King Clang should be registered");
        assertTrue(contains(spawns, 220050200, 5220003), "Timer3 should be registered");
    }

    @Test
    void doorBossesAreExcludedBecauseTheySpawnOnEntry() {
        // The 6 Door bosses are quest bosses spawned on player map entry via
        // SpawnOnEntryRegistry, NOT periodic respawns. They must NOT appear
        // here — adding them back would put them on a respawn timer and
        // change their semantics.
        List<AreaBossSpawn> spawns = registry.spawns();
        assertFalse(contains(spawns, 677000001, 9400612), "Marbas must not be in AreaBossRegistry (use SpawnOnEntryRegistry)");
        assertFalse(contains(spawns, 677000003, 9400610), "Amdusias must not be in AreaBossRegistry");
        assertFalse(contains(spawns, 677000005, 9400609), "Andras must not be in AreaBossRegistry");
        assertFalse(contains(spawns, 677000007, 9400611), "Crocell must not be in AreaBossRegistry");
        assertFalse(contains(spawns, 677000009, 9400613), "Valefor must not be in AreaBossRegistry");
        assertFalse(contains(spawns, 677000012, 9400633), "Astaroth must not be in AreaBossRegistry");
    }

    @Test
    void knownWzBossesArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 240020401, 8180000), "Manon should be registered");
        assertTrue(contains(spawns, 240020101, 8180001), "Griffey should be registered");
        assertTrue(contains(spawns, 230040420, 8510000), "Pianus right should be registered");
        assertTrue(contains(spawns, 230040420, 8520000), "Pianus left should be registered");
        assertTrue(contains(spawns, 105090900, 8130100), "Jr. Balrog should be registered");
        assertTrue(contains(spawns, 270010500, 8220004), "Dodo should be registered");
        assertTrue(contains(spawns, 270020500, 8220005), "Lilynouch should be registered");
        assertTrue(contains(spawns, 270030500, 8220006), "Lyka should be registered");
    }

    @Test
    void knownWeakenBossesArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 103000105, 5090000), "Shade (103000105) should be registered");
        assertTrue(contains(spawns, 103000202, 5090000), "Shade (103000202) should be registered");
        assertTrue(contains(spawns, 211041100, 6090000), "Riche (211041100) should be registered");
        assertTrue(contains(spawns, 211041400, 6090000), "Riche (211041400) should be registered");
        assertTrue(contains(spawns, 211050000, 6090001), "Snow Witch should be registered");
        assertTrue(contains(spawns, 222010300, 6090003), "Scholar Ghost should be registered");
        assertTrue(contains(spawns, 261020600, 6090004), "Rurumo (261020600) should be registered");
        assertTrue(contains(spawns, 261020401, 7090000), "Security Camera should be registered");
        assertTrue(contains(spawns, 261010102, 8090000), "Deet and Roi should be registered");
        assertTrue(contains(spawns, 250020300, 5090001), "Master Dummy should be registered");
    }

    @Test
    void knownRegionalBossesArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 800020130, 9400014), "Black Crow should be registered");
        assertTrue(contains(spawns, 800010100, 9400205), "Blue Mushmom should be registered");
        assertTrue(contains(spawns, 610010005, 9400575), "Bigfoot (610010005) should be registered");
        assertTrue(contains(spawns, 610010104, 9400575), "Bigfoot (610010104) should be registered");
        assertTrue(contains(spawns, 610010202, 9400549), "Headless Horseman (610010202) should be registered");
        assertTrue(contains(spawns, 682000001, 9400549), "Headless Horseman (682000001) should be registered");
        assertTrue(contains(spawns, 801030000, 9400120), "Male Boss should be registered");
        assertTrue(contains(spawns, 801040003, 9400121), "Female Boss should be registered");
        assertTrue(contains(spawns, 801040004, 9400122), "Bodyguard (801040004) should be registered");
        assertTrue(contains(spawns, 801040100, 9400122), "Bodyguard (801040100) should be registered");
    }

    @Test
    void knownMiniBossesArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 100000005, 6130101), "Mushmom should be registered");
        assertTrue(contains(spawns, 105070002, 6300005), "Zombie Mushmom should be registered");
        assertTrue(contains(spawns, 221020701, 4130103), "Rombot should be registered");
        assertTrue(contains(spawns, 221030601, 5120100), "MT-09 should be registered");
        assertTrue(contains(spawns, 211040101, 8220001), "Snowman should be registered");
    }

    @Test
    void knownMvBossRoomIsPresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 674030300, 9400748), "MV should be registered");
        assertTrue(contains(spawns, 674030300, 9400744), "Crimson Balrog Minion should be registered");
        assertTrue(contains(spawns, 674030300, 9400745), "Jr. Balrog Minion should be registered");
        assertTrue(contains(spawns, 674030300, 9400746), "Muscle Stone Minion should be registered");
        assertTrue(contains(spawns, 674030300, 9400747), "Bain Minion should be registered");
    }

    @Test
    void overridableWzMobIdsContainsExactlyTheExpectedBosses() {
        Set<Integer> ids = registry.overridableWzMobIds();

        assertEquals(EXPECTED_OVERRIDABLE_WZ_COUNT, ids.size());
        // Original 8 WZ bosses
        assertTrue(ids.contains(8180000));   // Manon
        assertTrue(ids.contains(8180001));   // Griffey
        assertTrue(ids.contains(8510000));   // Pianus right
        assertTrue(ids.contains(8520000));   // Pianus left
        assertTrue(ids.contains(8130100));   // Jr. Balrog
        assertTrue(ids.contains(8220004));   // Dodo
        assertTrue(ids.contains(8220005));   // Lilynouch
        assertTrue(ids.contains(8220006));   // Lyka
        // Weaken-boss family
        assertTrue(ids.contains(5090000));   // Shade
        assertTrue(ids.contains(5090001));   // Master Dummy
        assertTrue(ids.contains(6090000));   // Riche
        assertTrue(ids.contains(6090001));   // Snow Witch
        assertTrue(ids.contains(6090003));   // Scholar Ghost
        assertTrue(ids.contains(6090004));   // Rurumo
        assertTrue(ids.contains(7090000));   // Security Camera
        assertTrue(ids.contains(8090000));   // Deet and Roi
        // Regional long-timer
        assertTrue(ids.contains(9400014));   // Black Crow
        assertTrue(ids.contains(9400205));   // Blue Mushmom
        assertTrue(ids.contains(9400575));   // Bigfoot
        assertTrue(ids.contains(9400549));   // Headless Horseman
        assertTrue(ids.contains(9400120));   // Male Boss
        assertTrue(ids.contains(9400121));   // Female Boss
        assertTrue(ids.contains(9400122));   // Bodyguard
        // Mini-bosses
        assertTrue(ids.contains(6130101));   // Mushmom
        assertTrue(ids.contains(6300005));   // Zombie Mushmom
        assertTrue(ids.contains(4130103));   // Rombot
        assertTrue(ids.contains(5120100));   // MT-09
        assertTrue(ids.contains(8220001));   // Snowman
        // MV boss room
        assertTrue(ids.contains(9400744));   // Crimson Balrog Minion
        assertTrue(ids.contains(9400745));   // Jr. Balrog Minion
        assertTrue(ids.contains(9400746));   // Muscle Stone Minion
        assertTrue(ids.contains(9400747));   // Bain Minion
        assertTrue(ids.contains(9400748));   // MV
    }

    @Test
    void overridableWzMobIdsExcludesTheOriginalAreaBossScripts() {
        // The 27 JS-migrated bosses must NOT appear in the overridable set:
        // they never had WZ SpawnPoints in the first place.
        Set<Integer> ids = registry.overridableWzMobIds();

        assertFalse(ids.contains(2220000));  // Mano
        assertFalse(ids.contains(5220001));  // King Clang
        assertFalse(ids.contains(9400612));  // Marbas
    }

    @Test
    void overridableWzMobIdsIsUnmodifiable() {
        Set<Integer> ids = registry.overridableWzMobIds();

        assertThrows(UnsupportedOperationException.class, () -> ids.add(9999999));
    }

    @Test
    void getSpawnsReturnsUnmodifiableView() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertThrows(UnsupportedOperationException.class, () -> spawns.add(
                new AreaBossSpawn(1, 1, 0, 0, "x")));
    }

    @Test
    void getSpawnsReturnsSameInstanceEveryCall() {
        assertSame(registry.spawns(), registry.spawns());
    }

    private static boolean contains(List<AreaBossSpawn> spawns, int mapId, int mobId) {
        for (AreaBossSpawn s : spawns) {
            if (s.mapId() == mapId && s.mobId() == mobId) {
                return true;
            }
        }
        return false;
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual, "expected the same instance, got " + expected + " and " + actual);
    }
}
