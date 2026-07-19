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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision-matrix tests for {@link WzBossSpawnOverride}. The helper has no
 * global state and no side effects, so each case is a one-liner.
 */
class WzBossSpawnOverrideTest {

    private static final Set<Integer> OVERRIDABLE = Set.of(8180000, 8180001, 8510000, 8520000);

    @Test
    void deniesSpawnWhenEnabledAndMobIdIsListed() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, OVERRIDABLE);

        assertTrue(override.shouldDenySpawn(8180000));
        assertTrue(override.shouldDenySpawn(8520000));
    }

    @Test
    void doesNotDenyWhenEnabledButMobIdIsNotListed() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, OVERRIDABLE);

        assertFalse(override.shouldDenySpawn(100100));   // random snail
        assertFalse(override.shouldDenySpawn(0));
        assertFalse(override.shouldDenySpawn(-1));
    }

    @Test
    void neverDeniesWhenDisabledEvenIfMobIdIsListed() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(false, OVERRIDABLE);

        assertFalse(override.shouldDenySpawn(8180000));
        assertFalse(override.shouldDenySpawn(8520000));
    }

    @Test
    void emptySetDeniesNothingEvenWhenEnabled() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, Set.of());

        assertFalse(override.shouldDenySpawn(8180000));
    }

    @Test
    void exposesEnabledFlag() {
        assertTrue(new WzBossSpawnOverride(true, OVERRIDABLE).enabled());
        assertFalse(new WzBossSpawnOverride(false, OVERRIDABLE).enabled());
    }

    @Test
    void getOverridableMobIdsReturnsSameContents() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, OVERRIDABLE);

        assertEquals(OVERRIDABLE, override.overridableMobIds());
    }

    @Test
    void constructorDefensivelyCopiesInputSet() {
        Set<Integer> mutable = new HashSet<>();
        mutable.add(8180000);
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, mutable);

        mutable.clear();   // mutate the source after construction

        assertTrue(override.overridableMobIds().contains(8180000),
                "constructor must copy the input set so external mutation does not leak in");
        assertEquals(1, override.overridableMobIds().size());
    }

    @Test
    void getOverridableMobIdsIsImmutable() {
        WzBossSpawnOverride override = new WzBossSpawnOverride(true, new HashSet<>(OVERRIDABLE));

        assertThrows(UnsupportedOperationException.class,
                () -> override.overridableMobIds().add(9999999));
    }

    @Test
    void constructorRejectsNullSet() {
        assertThrows(NullPointerException.class, () -> new WzBossSpawnOverride(true, null));
    }

    @Test
    void defaultRegistryExposesAllConfiguredWzBossMobIds() {
        Set<Integer> ids = new AreaBossRegistry().overridableWzMobIds();

        assertEquals(33, ids.size());
        // Original 8 WZ bosses
        assertTrue(ids.contains(8180000));   // Manon
        assertTrue(ids.contains(8180001));   // Griffey
        assertTrue(ids.contains(8510000));   // Pianus right
        assertTrue(ids.contains(8520000));   // Pianus left
        assertTrue(ids.contains(8130100));   // Jr. Balrog
        assertTrue(ids.contains(8220004));   // Dodo
        assertTrue(ids.contains(8220005));   // Lilynouch
        assertTrue(ids.contains(8220006));   // Lyka
        // Sample of newly added categories
        assertTrue(ids.contains(5090000), "Shade should be overridable");
        assertTrue(ids.contains(9400575), "Bigfoot should be overridable");
        assertTrue(ids.contains(9400549), "Headless Horseman should be overridable");
        assertTrue(ids.contains(6130101), "Mushmom should be overridable");
        assertTrue(ids.contains(9400748), "MV should be overridable");
    }
}
