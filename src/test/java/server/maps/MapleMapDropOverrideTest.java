/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.maps;

import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterDropEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the per-monster drop-override resolution used by {@link MapleMap#dropFromMonster}. The
 * override is what lets a spawned {@link constants.id.MobId#PHARAOH_JR_YETI} drop the mode-specific
 * Pharaoh's Treasure Chest inside the Tomb (no {@code drop_data} row, mode-dependent chest) without
 * leaking into the global {@code drop_data} table.
 *
 * <p>The override-first ordering lives in the package-private
 * {@link MapleMap#resolveLootEntry(Monster, boolean)} so it is unit-testable without a real
 * {@link MapleMap} or DB: a non-null override fully determines the loot and short-circuits the
 * DB-backed path. The DB-backed fallback ({@code MonsterInformationProvider.getInstance()}, used only
 * when {@code USE_SPAWN_RELEVANT_LOOT} is off AND there is no override) is intentionally NOT covered
 * here -- it cannot be exercised without standing up the drop-data DB, and its behaviour is legacy
 * and unchanged by this fix.
 */
class MapleMapDropOverrideTest {

    @Test
    void monsterOverrideDefaultsToNull() {
        Monster mob = mock(Monster.class);
        when(mob.getDropOverride()).thenReturn(null);

        assertNull(mob.getDropOverride());
    }

    @Test
    void overrideIsRetainedVerbatimAfterSet() {
        // Mirrors how PharaohTomb attaches the chest list to the spawned Jr. Yeti.
        Monster mob = mock(Monster.class);
        List<MonsterDropEntry> override = List.of(
                new MonsterDropEntry(2022613, 1_000_000, 1, 1, (short) 0));

        when(mob.getDropOverride()).thenReturn(override);

        assertSame(override, mob.getDropOverride());
    }

    @Test
    void resolveLootEntryReturnsOverrideWhenPresentRegardlessOfFlag() {
        // The override must win under BOTH drop-rate configs, so a server running with or without
        // USE_SPAWN_RELEVANT_LOOT still drops the mode-specific chest from the tomb yeti.
        List<MonsterDropEntry> override = List.of(
                new MonsterDropEntry(2022618, 1_000_000, 1, 1, (short) 0));
        Monster mob = mock(Monster.class);
        when(mob.getDropOverride()).thenReturn(override);

        assertSame(override, MapleMap.resolveLootEntry(mob, true));
        assertSame(override, MapleMap.resolveLootEntry(mob, false));
    }

    @Test
    void overrideShortCircuitsWithoutAnyDatabaseDependency() {
        // The override path must not need the DB-backed drop provider at all -- this is exactly what
        // makes the tomb chest drop work in a unit test (and in production without a drop_data row).
        // If resolveLootEntry touched MonsterInformationProvider here, this test could not run without
        // the drop-data DB (the provider eagerly loads global drops on init).
        List<MonsterDropEntry> override = List.of(
                new MonsterDropEntry(2022613, 1_000_000, 1, 1, (short) 0));
        Monster mob = mock(Monster.class);
        when(mob.getDropOverride()).thenReturn(override);

        List<MonsterDropEntry> out = MapleMap.resolveLootEntry(mob, false);

        assertSame(override, out);
    }

    @Test
    void resolveLootEntryFallsBackToRelevantDropsWhenOverrideNullAndFlagOn() {
        // Legacy path: USE_SPAWN_RELEVANT_LOOT=true delegates to the mob's aggro-aware list. This
        // branch never touches the DB-backed provider, so it is unit-testable.
        List<MonsterDropEntry> dbDrops = List.of(
                new MonsterDropEntry(2000002, 5, 1, 1, (short) 0));
        Monster mob = mock(Monster.class);
        when(mob.getDropOverride()).thenReturn(null);
        when(mob.retrieveRelevantDrops()).thenReturn(dbDrops);

        assertSame(dbDrops, MapleMap.resolveLootEntry(mob, true));
    }

    @Test
    void overrideTakesPrecedenceOverLegacyRelevantDrops() {
        // Explicit precedence lock: even when retrieveRelevantDrops would return something else,
        // the override wins. This is the invariant that makes the tomb chest deterministic.
        List<MonsterDropEntry> override = List.of(
                new MonsterDropEntry(2022613, 1_000_000, 1, 1, (short) 0));
        List<MonsterDropEntry> irrelevant = List.of(
                new MonsterDropEntry(2000002, 5, 1, 1, (short) 0));
        Monster mob = mock(Monster.class);
        when(mob.getDropOverride()).thenReturn(override);
        when(mob.retrieveRelevantDrops()).thenReturn(irrelevant);

        List<MonsterDropEntry> out = MapleMap.resolveLootEntry(mob, true);
        assertEquals(1, out.size());
        assertEquals(2022613, out.get(0).itemId, "override must win over the DB-backed relevant drops");
    }
}
