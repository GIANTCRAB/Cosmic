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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AreaBossSpawnTest {

    @Test
    void accessorsReturnConstructorValues() {
        AreaBossSpawn s = new AreaBossSpawn(104000400, 2220000, 279, -496, "A cool breeze was felt when Mano appeared.");

        assertEquals(104000400, s.mapId());
        assertEquals(2220000, s.mobId());
        assertEquals(279, s.x());
        assertEquals(-496, s.y());
        assertEquals("A cool breeze was felt when Mano appeared.", s.message());
    }

    @Test
    void equalsAndHashCodeFollowRecordSemantics() {
        AreaBossSpawn a = new AreaBossSpawn(104000400, 2220000, 279, -496, "Mano");
        AreaBossSpawn b = new AreaBossSpawn(104000400, 2220000, 279, -496, "Mano");
        AreaBossSpawn c = new AreaBossSpawn(104000400, 2220001, 279, -496, "Mano");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void rejectsNonPositiveMapId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AreaBossSpawn(0, 2220000, 279, -496, "Mano"));
    }

    @Test
    void rejectsNonPositiveMobId() {
        assertThrows(IllegalArgumentException.class,
                () -> new AreaBossSpawn(104000400, 0, 279, -496, "Mano"));
    }

    @Test
    void rejectsBlankMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new AreaBossSpawn(104000400, 2220000, 279, -496, "   "));
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> new AreaBossSpawn(104000400, 2220000, 279, -496, null));
    }

    @Test
    void allowsNegativeCoordinates() {
        AreaBossSpawn s = new AreaBossSpawn(105090310, 8220008, -626, -604, "Snack Bar");
        assertEquals(-626, s.x());
        assertEquals(-604, s.y());
    }
}
