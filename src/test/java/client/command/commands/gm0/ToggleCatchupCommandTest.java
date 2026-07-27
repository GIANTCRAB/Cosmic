/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.MetaProgressionToggle;
import client.command.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToggleCatchupCommandTest {

    private static final int CHARACTER_ID = 1337;

    private final Command command = new ToggleCatchupCommand();

    @Mock
    private Client client;

    @Mock
    private Character player;

    @BeforeEach
    void resetToggle() {
        MetaProgressionToggle.clear(CHARACTER_ID);
    }

    @Test
    void flipsToggleRefreshesStatsAndReportsDisabled() {
        when(client.tryacquireClient()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(CHARACTER_ID);

        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID)); // default on

        command.execute(client, new String[0]);

        assertFalse(MetaProgressionToggle.isEnabled(CHARACTER_ID),
                "command must flip the shared registry toggle to disabled");
        verify(player).equipChanged();
        verify(player).showHint(contains("disabled"), eq(300));
        verify(client).releaseClient();
    }

    @Test
    void reportsEnabledWhenToggledBackOn() {
        MetaProgressionToggle.toggle(CHARACTER_ID); // start disabled

        when(client.tryacquireClient()).thenReturn(true);
        when(client.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(CHARACTER_ID);

        command.execute(client, new String[0]);

        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID));
        verify(player).showHint(contains("enabled"), eq(300));
    }

    @Test
    void doesNothingWhenClientLockNotAcquired() {
        when(client.tryacquireClient()).thenReturn(false);

        command.execute(client, new String[0]);

        assertTrue(MetaProgressionToggle.isEnabled(CHARACTER_ID), "toggle must not change");
        verify(client, never()).getPlayer();
        verify(player, never()).equipChanged();
        verify(client, never()).releaseClient();
    }
}
