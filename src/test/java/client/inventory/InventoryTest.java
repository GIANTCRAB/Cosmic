/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.inventory;

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryTest {
    private int previousMaxSlots;

    @BeforeEach
    void saveMaxSlots() {
        previousMaxSlots = YamlConfig.config.server.MAX_INVENTORY_SLOTS;
    }

    @AfterEach
    void restoreMaxSlots() {
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = previousMaxSlots;
    }

    @Test
    void slotLimitRetainsValueAboveOldByteCeiling() {
        final var limit = 200;
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = limit;
        final var inv = new Inventory(null, InventoryType.USE, limit);
        assertEquals(limit, inv.getSlotLimit());
    }

    @Test
    void slotLimitRespectsMaxInventorySlots() {
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = 96;
        final var inv = new Inventory(null, InventoryType.USE, 200);
        assertEquals(YamlConfig.config.server.MAX_INVENTORY_SLOTS, inv.getSlotLimit());
    }

    @Test
    void setSlotLimitRoundTripsAboveOldByteCeiling() {
        final var limit = 250;
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = limit;
        final var inv = new Inventory(null, InventoryType.ETC, 24);
        inv.setSlotLimit(limit);
        assertEquals(limit, inv.getSlotLimit());
    }

    @Test
    void setSlotLimitRespectsMaxInventorySlots() {
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = 96;
        final var inv = new Inventory(null, InventoryType.ETC, 24);
        inv.setSlotLimit(250);
        assertEquals(YamlConfig.config.server.MAX_INVENTORY_SLOTS, inv.getSlotLimit());
    }
}
