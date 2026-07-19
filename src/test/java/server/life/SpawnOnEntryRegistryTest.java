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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the contents of {@link SpawnOnEntryRegistry}: the six Astaroth-door
 * quest bosses, their maps, and the spawn-on-entry contract. Also verifies
 * the separation from {@link AreaBossRegistry} so a Door boss cannot
 * accidentally end up on a periodic respawn timer.
 */
class SpawnOnEntryRegistryTest {

    private static final int EXPECTED_ENTRY_COUNT = 6;

    private final SpawnOnEntryRegistry registry = SpawnOnEntryRegistry.getDefault();

    @Test
    void containsExactlyTheSixDoorBosses() {
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
    void noDuplicateMapIds() {
        // Each map can have at most one spawn-on-entry boss; forMap(int) returns
        // a single result, so this invariant must hold.
        long uniqueMaps = registry.spawns().stream().map(AreaBossSpawn::mapId).distinct().count();
        assertEquals(registry.spawns().size(), uniqueMaps, "duplicate map ids would break the one-boss-per-map contract");
    }

    @Test
    void forMapReturnsTheRegisteredBoss() {
        AreaBossSpawn marbas = registry.forMap(677000001);
        assertNotNull(marbas);
        assertEquals(9400612, marbas.mobId());
        assertEquals("Marbas has appeared!", marbas.message());

        AreaBossSpawn astaroth = registry.forMap(677000012);
        assertNotNull(astaroth);
        assertEquals(9400633, astaroth.mobId());
    }

    @Test
    void forMapReturnsNullForUnknownMap() {
        assertNull(registry.forMap(100000000));   // Henesys town
        assertNull(registry.forMap(0));
        assertNull(registry.forMap(-1));
    }

    @Test
    void allSixDoorBossesArePresent() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertTrue(contains(spawns, 677000001, 9400612), "Marbas should be registered");
        assertTrue(contains(spawns, 677000003, 9400610), "Amdusias should be registered");
        assertTrue(contains(spawns, 677000005, 9400609), "Andras should be registered");
        assertTrue(contains(spawns, 677000007, 9400611), "Crocell should be registered");
        assertTrue(contains(spawns, 677000009, 9400613), "Valefor should be registered");
        assertTrue(contains(spawns, 677000012, 9400633), "Astaroth should be registered");
    }

    @Test
    void spawnsListIsUnmodifiable() {
        List<AreaBossSpawn> spawns = registry.spawns();
        assertThrows(UnsupportedOperationException.class, () -> spawns.add(
                new AreaBossSpawn(1, 1, 0, 0, "x")));
    }

    @Test
    void getDefaultReturnsSameSingletonInstance() {
        assertSame(SpawnOnEntryRegistry.getDefault(), SpawnOnEntryRegistry.getDefault());
    }

    @Test
    void doorBossesAreNotInAreaBossRegistry() {
        // Cross-registry sanity: Door bosses must NOT appear in the periodic
        // AreaBossRegistry. Putting them there would silently convert them
        // from spawn-on-entry to periodic-respawn semantics.
        AreaBossRegistry areaRegistry = new AreaBossRegistry();
        for (AreaBossSpawn s : registry.spawns()) {
            assertFalse(contains(areaRegistry.spawns(), s.mapId(), s.mobId()),
                    "Door boss " + s + " must not appear in AreaBossRegistry");
        }
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
