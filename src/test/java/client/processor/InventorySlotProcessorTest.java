/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.processor;

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventorySlotProcessorTest {
    private int previousMaxSlots;

    @BeforeEach
    void saveMaxSlots() {
        previousMaxSlots = YamlConfig.config.server.MAX_INVENTORY_SLOTS;
    }

    @AfterEach
    void restoreMaxSlots() {
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = previousMaxSlots;
    }

    @ParameterizedTest
    @CsvSource({
            "24, 4, 96, true",
            "92, 4, 96, true",
            "93, 4, 96, false",
            "96, 0, 96, true",
            "96, 1, 96, false",
            "120, 7, 127, true",
            "120, 8, 127, false",
            "120, 80, 200, true",
            "120, 81, 200, false",
            "0, 0, 96, true"
    })
    void canGainSlotsBoundaryCases(int currentLimit, int slotsToAdd, int maxSlots, boolean expected) {
        assertEquals(expected, InventorySlotProcessor.canGainSlots(currentLimit, slotsToAdd, maxSlots));
    }

    @Test
    void maxSlotsReflectsConfig() {
        YamlConfig.config.server.MAX_INVENTORY_SLOTS = 96;
        assertEquals(96, InventorySlotProcessor.maxSlots());

        YamlConfig.config.server.MAX_INVENTORY_SLOTS = 127;
        assertEquals(127, InventorySlotProcessor.maxSlots());

        YamlConfig.config.server.MAX_INVENTORY_SLOTS = 200;
        assertEquals(200, InventorySlotProcessor.maxSlots());
    }
}
