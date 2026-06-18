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

import client.Character;
import client.Client;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardcoreProcessorTest {
    @Mock
    private Character chr;

    @Mock
    private Client client;

    private boolean previousHardcoreMode;

    @BeforeEach
    void enableHardcoreMode() {
        previousHardcoreMode = YamlConfig.config.server.USE_HARDCORE_MODE;
        YamlConfig.config.server.USE_HARDCORE_MODE = true;
    }

    @AfterEach
    void restoreHardcoreMode() {
        YamlConfig.config.server.USE_HARDCORE_MODE = previousHardcoreMode;
    }

    @Test
    void isHardcoreModeEnabledReflectsConfig() {
        YamlConfig.config.server.USE_HARDCORE_MODE = false;
        assertFalse(HardcoreProcessor.isHardcoreModeEnabled());

        YamlConfig.config.server.USE_HARDCORE_MODE = true;
        assertTrue(HardcoreProcessor.isHardcoreModeEnabled());
    }

    @Test
    void disabledModeLeavesNormalDeathUntouched() {
        YamlConfig.config.server.USE_HARDCORE_MODE = false;

        boolean result = HardcoreProcessor.processPermanentDeathIfEnabled(chr);

        assertFalse(result);
        verifyNoInteractions(chr);
    }

    @Test
    void enabledModeFlushesStorageBeforeDeletingCharacterAndDisconnects() {
        when(chr.getClient()).thenReturn(client);
        when(chr.deletePermanently()).thenReturn(true);

        boolean result = HardcoreProcessor.processPermanentDeathIfEnabled(chr);

        assertTrue(result);

        var inOrder = inOrder(chr, client);
        inOrder.verify(chr).flushStorage();           // account storage persisted before deletion
        inOrder.verify(chr).leaveParty();             // detach from party before deletion (prevents ghost member)
        inOrder.verify(chr).leaveFamily();            // detach from family before deletion (prevents ghost entry)
        inOrder.verify(chr).deletePermanently();      // character + inventory deleted
        inOrder.verify(chr).markPendingHardcoreDeletion();
        inOrder.verify(client).forceDisconnect();
    }

    @Test
    void enabledModeDetachesFromPartyAndFamilyBeforeDeletion() {
        when(chr.getClient()).thenReturn(client);
        when(chr.deletePermanently()).thenReturn(true);

        HardcoreProcessor.processPermanentDeathIfEnabled(chr);

        var inOrder = inOrder(chr);
        inOrder.verify(chr).leaveParty();
        inOrder.verify(chr).leaveFamily();
        inOrder.verify(chr).deletePermanently();
    }

    @Test
    void enabledModePreservesAccountStorage() {
        when(chr.getClient()).thenReturn(client);
        when(chr.deletePermanently()).thenReturn(true);

        HardcoreProcessor.processPermanentDeathIfEnabled(chr);

        verify(chr).flushStorage();
        verify(chr).deletePermanently();
        verify(chr, org.mockito.Mockito.never()).setUsedStorage();
    }

    @Test
    void enabledModeToleratesNullClient() {
        when(chr.getClient()).thenReturn(null);
        when(chr.deletePermanently()).thenReturn(true);

        assertDoesNotThrow(() -> HardcoreProcessor.processPermanentDeathIfEnabled(chr));

        verify(chr).flushStorage();
        verify(chr).deletePermanently();
        verify(chr).markPendingHardcoreDeletion();
    }
}
