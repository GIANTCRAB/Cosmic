/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.world;

import client.Character;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the solo Nett's Pyramid entry bug. The solo dummy Party built in
 * {@code NPCConversationManager.createPyramid} via {@code new Party(-1, pc)} was never given its
 * leader as a member, so {@link Party#getLeader()} returned null and {@code PartyQuest(Party)} NPE'd
 * on {@code leader.getChannel()} whenever a solo player tried to enter. These tests pin the contract
 * the fix relies on: a solo Party is only usable once its leader has been added as a member.
 */
@ExtendWith(MockitoExtension.class)
class PartyTest {

    @Mock
    private Character leader;

    @Mock
    private Client client;

    private PartyCharacter newSoloLeader() {
        when(leader.getName()).thenReturn("Solo");
        when(leader.getLevel()).thenReturn(50);
        when(leader.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(leader.getWorld()).thenReturn(0);
        when(leader.getId()).thenReturn(42);
        when(leader.getJob()).thenReturn(Job.BEGINNER);
        when(leader.getMapId()).thenReturn(926010000);
        when(leader.getGuildId()).thenReturn(0);
        return new PartyCharacter(leader);
    }

    @Test
    void freshSoloPartyHasNoResolvableLeaderUntilLeaderIsAddedAsMember() {
        // The latent trap that caused the bug: Party(int, PartyCharacter) records only the leader
        // id; the members list stays empty until addMember is called, so getLeader() returns null.
        PartyCharacter pc = newSoloLeader();
        Party party = new Party(-1, pc);

        assertEquals(42, party.getLeaderId());
        assertNull(party.getLeader(), "leader not yet a member -> getLeader() must be null");
        assertTrue(party.getMembers().isEmpty(), "members list must be empty before addMember");
    }

    @Test
    void addLeaderAsMemberResolvesLeaderAndPopulatesMembers() {
        // The fix's contract: after addMember, getLeader() resolves and the solo leader is the
        // only member -- what createPyramid must guarantee before handing the Party to Pyramid.
        PartyCharacter pc = newSoloLeader();
        Party party = new Party(-1, pc);
        party.addMember(pc);

        assertSame(pc, party.getLeader());
        assertEquals(1, party.getMembers().size());
        assertTrue(party.getMembers().contains(pc));
    }
}
