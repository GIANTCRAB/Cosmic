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
import client.inventory.manipulator.InventoryManipulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import scripting.AbstractPlayerInteraction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryExchangeProcessor#exchange}. The processor is the
 * transactional core behind {@code cm.exchangeItems(...)}: it must apply a set of
 * removals and additions all-or-nothing, bracketed by
 * {@link Character#lockTransfer()} / {@link Character#unlockTransfer()} so it cannot
 * race with {@link Character#saveCharToDB(boolean)}, and roll back any partially
 * applied state if a commit step fails.
 *
 * <p>{@link InventoryManipulator} is stubbed with {@link MockedStatic} (it touches
 * the WZ-dependent {@code ItemInformationProvider} singleton); collaborators are
 * plain Mockito mocks, mirroring {@code StorageProcessorTest}.
 */
@ExtendWith(MockitoExtension.class)
class InventoryExchangeProcessorTest {
    @Mock private AbstractPlayerInteraction api;
    @Mock private Client client;
    @Mock private Character chr;

    @BeforeEach
    void wireHappyPathDefaults() {
        lenient().when(api.getClient()).thenReturn(client);
        lenient().when(api.getPlayer()).thenReturn(chr);
        lenient().when(api.haveItem(anyInt(), anyInt())).thenReturn(true);
        lenient().when(api.canHoldAllAfterRemoving(anyList(), anyList(), anyList(), anyList())).thenReturn(true);
    }

    @Test
    void success_appliesAllRemovalsThenAdditionsUnderTransferLock() {
        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class)) {
            im.when(() -> InventoryManipulator.addById(eq(client), anyInt(), anyShort())).thenReturn(true);

            boolean result = InventoryExchangeProcessor.exchange(api,
                    new int[]{4001009, 4001010}, new int[]{10, 5},
                    new int[]{1302058}, new int[]{1});

            assertTrue(result);
            InOrder inOrder = inOrder(chr);
            inOrder.verify(chr).lockTransfer();
            inOrder.verify(chr).unlockTransfer();
            im.verify(() -> InventoryManipulator.removeById(eq(client), any(), eq(4001009), eq(10), anyBoolean(), anyBoolean()));
            im.verify(() -> InventoryManipulator.removeById(eq(client), any(), eq(4001010), eq(5), anyBoolean(), anyBoolean()));
            im.verify(() -> InventoryManipulator.addById(eq(client), eq(1302058), eq((short) 1)));
            verify(client, times(3)).sendPacket(any());   // 2 losses + 1 gain
        }
    }

    @Test
    void missingPossession_returnsFalseWithoutLockingOrMutating() {
        when(api.haveItem(4001009, 10)).thenReturn(false);

        boolean result = InventoryExchangeProcessor.exchange(api,
                new int[]{4001009}, new int[]{10}, new int[]{1302058}, new int[]{1});

        assertFalse(result);
        verify(chr, never()).lockTransfer();
        verify(api, never()).canHoldAllAfterRemoving(anyList(), anyList(), anyList(), anyList());
        verify(client, never()).sendPacket(any());
    }

    @Test
    void spaceCheckFails_returnsFalseWithoutLockingOrMutating() {
        when(api.canHoldAllAfterRemoving(anyList(), anyList(), anyList(), anyList())).thenReturn(false);

        boolean result = InventoryExchangeProcessor.exchange(api,
                new int[]{4001009}, new int[]{10}, new int[]{1302058}, new int[]{1});

        assertFalse(result);
        verify(chr, never()).lockTransfer();
        verify(client, never()).sendPacket(any());
    }

    @Test
    void midCommitRemoveThrows_rollsBackAndReleasesLock() {
        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class)) {
            im.when(() -> InventoryManipulator.removeById(eq(client), any(), eq(4001010), eq(5), anyBoolean(), anyBoolean()))
                    .thenThrow(new RuntimeException("insufficient"));

            boolean result = InventoryExchangeProcessor.exchange(api,
                    new int[]{4001009, 4001010}, new int[]{10, 5},
                    new int[]{1302058}, new int[]{1});

            assertFalse(result);
            verify(chr).lockTransfer();
            verify(chr).unlockTransfer();
            // Forward reward add never reached...
            im.verify(() -> InventoryManipulator.addById(eq(client), eq(1302058), anyShort()), never());
            // ...but the first (already-applied) removal is restored by the rollback.
            im.verify(() -> InventoryManipulator.addById(eq(client), eq(4001009), eq((short) 10)));
            verify(client, never()).sendPacket(any());   // rolled back -> no net change -> no feedback
        }
    }

    @Test
    void addByIdReturnsFalse_rollsBackAndReleasesLock() {
        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class)) {
            im.when(() -> InventoryManipulator.addById(eq(client), eq(1302058), eq((short) 1))).thenReturn(false);

            boolean result = InventoryExchangeProcessor.exchange(api,
                    new int[]{4001009}, new int[]{10}, new int[]{1302058}, new int[]{1});

            assertFalse(result);
            verify(chr).lockTransfer();
            verify(chr).unlockTransfer();
            im.verify(() -> InventoryManipulator.removeById(eq(client), any(), eq(4001009), eq(10), anyBoolean(), anyBoolean()));
            im.verify(() -> InventoryManipulator.addById(eq(client), eq(1302058), eq((short) 1)));
            // Rollback re-adds the item that was removed before the failed add.
            im.verify(() -> InventoryManipulator.addById(eq(client), eq(4001009), eq((short) 10)));
            verify(client, never()).sendPacket(any());   // rolled back -> no feedback
        }
    }

    @Test
    void duplicateRemoveIds_areAggregatedIntoSingleRemoval() {
        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class)) {
            im.when(() -> InventoryManipulator.addById(eq(client), anyInt(), anyShort())).thenReturn(true);

            boolean result = InventoryExchangeProcessor.exchange(api,
                    new int[]{4001009, 4001009}, new int[]{4, 6},
                    new int[]{1302058}, new int[]{1});

            assertTrue(result);
            verify(api).haveItem(4001009, 10);
            im.verify(() -> InventoryManipulator.removeById(eq(client), any(), eq(4001009), eq(10), anyBoolean(), anyBoolean()));
            im.verify(() -> InventoryManipulator.removeById(eq(client), any(), anyInt(), anyInt(), anyBoolean(), anyBoolean()), times(1));
            verify(client, times(2)).sendPacket(any());   // 1 aggregated loss + 1 gain
        }
    }
}
