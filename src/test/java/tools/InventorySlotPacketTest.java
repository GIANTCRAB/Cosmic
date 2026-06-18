/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package tools;

import config.YamlConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventorySlotPacketTest {
    private boolean previousExtended;

    @BeforeEach
    void saveExtended() {
        previousExtended = YamlConfig.config.server.USE_EXTENDED_INVENTORY_SLOTS;
    }

    @AfterEach
    void restoreExtended() {
        YamlConfig.config.server.USE_EXTENDED_INVENTORY_SLOTS = previousExtended;
    }

    private static ByteBuf body(Packet packet) {
        ByteBuf buf = Unpooled.copiedBuffer(packet.getBytes());
        buf.readShortLE();
        return buf;
    }

    @Test
    void updateInventorySlotLimitDefaultWritesSingleByte() {
        YamlConfig.config.server.USE_EXTENDED_INVENTORY_SLOTS = false;

        Packet p = PacketCreator.updateInventorySlotLimit(2, 96);
        ByteBuf body = body(p);

        assertEquals(2, body.readByte());
        assertEquals(96, body.readByte());
        assertEquals(0, body.readableBytes());
    }

    @Test
    void updateInventorySlotLimitExtendedWritesTwoBytes() {
        YamlConfig.config.server.USE_EXTENDED_INVENTORY_SLOTS = true;

        Packet p = PacketCreator.updateInventorySlotLimit(2, 300);
        ByteBuf body = body(p);

        assertEquals(2, body.readByte());
        assertEquals(300, body.readShortLE());
        assertEquals(0, body.readableBytes());
    }
}
