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
import org.junit.jupiter.api.Test;
import testutil.Mocks;

import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link Pyramid#broadcastInfo} map filter: gameplay massacre/gauge packets are only
 * delivered to participants currently standing on a Nett's Pyramid map, never to one still on the
 * entrance (a non-massacre field).
 */
class PyramidBroadcastTest {

    private static final int SOLO_PYRAMID_MAP = 926010100;
    private static final int PARTY_PYRAMID_MAP = 926020300;
    private static final int PYRAMID_DUNES_ENTRANCE = 926010000;  // NOT a massacre field

    @Test
    void broadcastInfo_deliversToParticipantsOnPyramidMaps() {
        Character onPyramidA = participantOn(SOLO_PYRAMID_MAP);
        Character onPyramidB = participantOn(PARTY_PYRAMID_MAP);
        Pyramid py = new Pyramid(List.of(onPyramidA, onPyramidB), Pyramid.PyramidMode.EASY, SOLO_PYRAMID_MAP);

        py.broadcastInfo("hit", 7);

        verify(onPyramidA, atLeastOnce()).sendPacket(org.mockito.ArgumentMatchers.any());
        verify(onPyramidB, atLeastOnce()).sendPacket(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void broadcastInfo_skipsParticipantStillOnEntranceMap() {
        // A member who hasn't warped into the pyramid yet must not receive massacre/gauge packets.
        Character onPyramid = participantOn(SOLO_PYRAMID_MAP);
        Character onEntrance = participantOn(PYRAMID_DUNES_ENTRANCE);
        Pyramid py = new Pyramid(List.of(onPyramid, onEntrance), Pyramid.PyramidMode.EASY, SOLO_PYRAMID_MAP);

        py.broadcastInfo("hit", 3);

        verify(onPyramid, atLeastOnce()).sendPacket(org.mockito.ArgumentMatchers.any());
        verify(onEntrance, never()).sendPacket(org.mockito.ArgumentMatchers.any());
    }

    private static Character participantOn(int mapId) {
        Character chr = Mocks.chr();
        when(chr.getMapId()).thenReturn(mapId);
        return chr;
    }
}
