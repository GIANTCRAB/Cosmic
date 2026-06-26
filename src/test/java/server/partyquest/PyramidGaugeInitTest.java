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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.TimerManager;
import server.maps.MapleMap;
import testutil.Mocks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the Act Gauge values the client sees. The visible {@code PYRAMID_GAUGE} bar
 * is driven by {@code count} (the monotonic broadcast value), NOT by {@code gauge} (the hidden life
 * meter). On stage entry {@code count} is 0 -- an empty bar that grows as the player lands kills --
 * and the per-second gauge drain is silent (it only triggers the fail-out, never broadcasts).
 */
class PyramidGaugeInitTest {

    private static final int PYRAMID_MAP = 926010100;

    private Pyramid py;

    @BeforeAll
    static void startTimerManager() {
        // commenceStage schedules the gauge drain via TimerManager.
        TimerManager.getInstance().start();
    }

    @AfterEach
    void disposePyramid() {
        if (py != null) {
            py.dispose();   // cancels the drain so it cannot warpOut between tests
        }
    }

    @Test
    void firstGaugeBroadcastOnStageEntryIsZeroCount() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));

        py.startEntry(mockMap());

        List<Integer> gaugeValues = gaugeValuesSentTo(a);
        assertFalse(gaugeValues.isEmpty(), "expected at least one PYRAMID_GAUGE packet on stage entry");
        assertEquals(0, gaugeValues.get(0),
                "the client's first gauge packet is count (empty bar that grows), not the hidden gauge (100)");
    }

    @Test
    void gaugeDrainIsHiddenAndDoesNotBroadcast() throws InterruptedException {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));

        py.startEntry(mockMap());
        assertEquals(100, py.gauge.get(), "gauge starts full (hidden life meter)");

        Thread.sleep(1500); // gauge drain registers with a 1000ms initial delay

        assertTrue(py.gauge.get() < 100, "the drain did fire and reduced the hidden gauge");
        assertEquals(1, gaugeValuesSentTo(a).size(),
                "the drain must not broadcast PYRAMID_GAUGE; only the entry packet should exist");
    }

    private static List<Integer> gaugeValuesSentTo(Character chr) {
        ArgumentCaptor<Packet> captor = ArgumentCaptor.forClass(Packet.class);
        verify(chr, atLeastOnce()).sendPacket(captor.capture());

        List<Integer> values = new ArrayList<>();
        for (Packet p : captor.getAllValues()) {
            ByteBuf buf = Unpooled.copiedBuffer(p.getBytes());
            // Every server packet starts with a 2-byte little-endian opcode.
            if (buf.readableBytes() >= 2 + 4) {
                short opcode = buf.readShortLE();
                if (opcode == SendOpcode.PYRAMID_GAUGE.getValue()) {
                    values.add(buf.readIntLE());
                }
            }
        }
        return values;
    }

    private static Pyramid newPyramid(List<Character> participants) {
        return new Pyramid(participants, Pyramid.PyramidMode.EASY, PYRAMID_MAP);
    }

    private static Character participantOn(int mapId, int id) {
        Character chr = Mocks.chr();
        when(chr.getMapId()).thenReturn(mapId);
        when(chr.getId()).thenReturn(id);
        return chr;
    }

    private static MapleMap mockMap() {
        return mock(MapleMap.class);
    }
}
