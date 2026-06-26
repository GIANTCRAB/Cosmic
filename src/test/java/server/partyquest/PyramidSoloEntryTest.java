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
import client.Client;
import client.Job;
import net.server.PlayerStorage;
import net.server.Server;
import net.server.channel.Channel;
import net.server.world.Party;
import net.server.world.PartyCharacter;
import net.server.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Regression test for the solo Nett's Pyramid entry NPE. Constructing a Pyramid from the dummy solo
 * Party invokes the PartyQuest(Party) constructor, which previously NPE'd on
 * {@code party.getLeader().getChannel()} because the solo leader was never registered as a member.
 * This reproduces that exact path and proves the leader-resolution + participant lookup now succeed.
 */
class PyramidSoloEntryTest {

    private static final int SOLO_MAP = 926010000;      // Pyramid Dunes entrance
    private static final int PYRAMID_ENTRY = 926010100; // EASY solo stage-1 map

    @Test
    void soloPartyConstructingPyramidResolvesParticipantWithoutThrowing() {
        Character solo = mock(Character.class);
        Client client = mock(Client.class);
        when(solo.getName()).thenReturn("Solo");
        when(solo.getLevel()).thenReturn(50);
        when(solo.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(solo.getWorld()).thenReturn(0);
        when(solo.getId()).thenReturn(42);
        when(solo.getJob()).thenReturn(Job.BEGINNER);
        when(solo.getMapId()).thenReturn(SOLO_MAP);
        when(solo.getGuildId()).thenReturn(0);

        // Exactly how createPyramid assembles the solo Party (Party + addMember).
        PartyCharacter pc = new PartyCharacter(solo);
        Party party = new Party(-1, pc);
        party.addMember(pc);

        try (MockedStatic<Server> ms = mockStatic(Server.class)) {
            Server server = mock(Server.class);
            World world = mock(World.class);
            Channel channel = mock(Channel.class);
            PlayerStorage storage = mock(PlayerStorage.class);

            ms.when(Server::getInstance).thenReturn(server);
            when(server.getWorld(0)).thenReturn(world);
            when(world.getChannel(1)).thenReturn(channel);
            when(channel.getPlayerStorage()).thenReturn(storage);
            when(storage.getCharacterById(42)).thenReturn(solo);

            Pyramid py = assertDoesNotThrow(
                    () -> new Pyramid(party, Pyramid.PyramidMode.EASY, PYRAMID_ENTRY, mock(Channel.class)));

            assertEquals(1, py.getParticipants().size());
            assertTrue(py.getParticipants().contains(solo));
        }
    }
}
