/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyCharacterTest {

    @Mock
    private Character maplechar;

    @Mock
    private Client client;

    private PartyCharacter newPartyCharacter() {
        when(maplechar.getName()).thenReturn("Tester");
        when(maplechar.getLevel()).thenReturn(30);
        when(maplechar.getClient()).thenReturn(client);
        when(client.getChannel()).thenReturn(1);
        when(maplechar.getWorld()).thenReturn(0);
        when(maplechar.getId()).thenReturn(42);
        when(maplechar.getJob()).thenReturn(Job.BEGINNER);
        when(maplechar.getMapId()).thenReturn(100000000);
        when(maplechar.getGuildId()).thenReturn(777);
        return new PartyCharacter(maplechar);
    }

    @Test
    void getGuildIdReturnsCachedSnapshot() {
        PartyCharacter mpc = newPartyCharacter();

        assertEquals(777, mpc.getGuildId());
    }

    @Test
    void getGuildIdSurvivesOfflineNullCharacter() {
        PartyCharacter mpc = newPartyCharacter();

        mpc.setOnline(false); // nulls the internal character reference

        assertNull(mpc.getPlayer());
        assertEquals(777, mpc.getGuildId()); // would have NPE'd before the cached field
    }

    @Test
    void isLeaderDoesNotThrowWhenCharacterIsNull() {
        PartyCharacter mpc = newPartyCharacter();

        mpc.setOnline(false); // nulls the internal character reference

        assertNull(mpc.getPlayer());
        Boolean result = assertDoesNotThrow(mpc::isLeader);
        assertFalse(result);
    }

    @Test
    void isLeaderReflectsPartyLeaderFlagWhenOnline() {
        PartyCharacter mpc = newPartyCharacter();
        when(maplechar.isPartyLeader()).thenReturn(true);

        assertTrue(mpc.isLeader());
    }
}
