/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.processor.npc;

import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import client.inventory.manipulator.KarmaManipulator;
import config.YamlConfig;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.ByteBufOutPacket;
import net.packet.InPacket;
import net.packet.OutPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import server.Storage;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageProcessor}. Verifies that storage<->inventory
 * transfers are bracketed by {@link Character#lockTransfer()} /
 * {@link Character#unlockTransfer()} (so they cannot race with
 * {@link Character#saveCharToDB(boolean)}), and that the inventory lock spans
 * the full remove+store / takeOut+addFromDrop sequence.
 *
 * <p>Tests call the package-private overload
 * {@link StorageProcessor#storageAction(InPacket, Client, IntPredicate, IntFunction)}
 * with stub {@link IntPredicate} / {@link IntFunction} collaborators. The public
 * overload wraps the {@code ItemInformationProvider} singleton, which has a
 * WZ-file-dependent static initializer and so cannot be loaded in the test JVM.
 */
@ExtendWith(MockitoExtension.class)
class StorageProcessorTest {
    private static final int ETC_ITEM_ID = 4000000;     // ETC-range item
    private static final int STORE_FEE = 100;
    private static final int TAKEOUT_FEE = 0;

    @Mock private Client client;
    @Mock private Character chr;
    @Mock private Storage storage;
    @Mock private Inventory inv;
    @Mock private IntPredicate isPickupRestricted;
    @Mock private IntFunction<String> getName;

    private int previousMinGmLevel;
    private boolean previousUseStorageSort;

    @BeforeEach
    void wireClientAndConfig() {
        lenient().when(client.getPlayer()).thenReturn(chr);
        lenient().when(client.tryacquireClient()).thenReturn(true);
        lenient().when(chr.getStorage()).thenReturn(storage);
        lenient().when(chr.getLevel()).thenReturn(20);             // passes the level >= 15 gate
        lenient().when(chr.isGM()).thenReturn(false);               // no GM restrictions by default
        lenient().when(chr.gmLevel()).thenReturn(0);
        // Default collaborators: pickup is not restricted, item has a name.
        // Individual tests override these as needed.
        lenient().when(isPickupRestricted.test(anyInt())).thenReturn(false);
        lenient().when(getName.apply(anyInt())).thenReturn("ETC Item");

        previousMinGmLevel = YamlConfig.config.server.MINIMUM_GM_LEVEL_TO_USE_STORAGE;
        previousUseStorageSort = YamlConfig.config.server.USE_STORAGE_ITEM_SORT;
        YamlConfig.config.server.MINIMUM_GM_LEVEL_TO_USE_STORAGE = 3;
        YamlConfig.config.server.USE_STORAGE_ITEM_SORT = false;
    }

    @AfterEach
    void restoreConfig() {
        YamlConfig.config.server.MINIMUM_GM_LEVEL_TO_USE_STORAGE = previousMinGmLevel;
        YamlConfig.config.server.USE_STORAGE_ITEM_SORT = previousUseStorageSort;
    }

    // ---------------- STORE (mode 5) ----------------

    @Test
    void store_validItem_acquiresTransferLockBeforeAnyMutation() {
        Item item = mock(Item.class);
        Item copy = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 100);
        when(item.copy()).thenReturn(copy);
        when(copy.getItemId()).thenReturn(ETC_ITEM_ID);
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);
        when(inv.getItem((short) 1)).thenReturn(item);
        when(storage.isFull()).thenReturn(false);
        when(storage.getStoreFee()).thenReturn(STORE_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 10), client, isPickupRestricted, getName);
        }

        InOrder inOrder = inOrder(chr, inv, storage);
        inOrder.verify(chr).lockTransfer();
        inOrder.verify(inv).lockInventory();
        inOrder.verify(storage).store(copy);
        inOrder.verify(inv).unlockInventory();
        inOrder.verify(chr).unlockTransfer();
        verify(chr).setUsedStorage();
    }

    @Test
    void store_inventoryLockSpansStoreCall_soTransferIsAtomicWrtSave() {
        Item item = mock(Item.class);
        Item copy = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 100);
        when(item.copy()).thenReturn(copy);
        when(copy.getItemId()).thenReturn(ETC_ITEM_ID);
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);
        when(inv.getItem((short) 1)).thenReturn(item);
        when(storage.isFull()).thenReturn(false);
        when(storage.getStoreFee()).thenReturn(STORE_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 10), client, isPickupRestricted, getName);
        }

        // The bug we're guarding against: inv.unlockInventory() must NOT be called before storage.store().
        InOrder inOrder = inOrder(inv, storage);
        inOrder.verify(inv).lockInventory();
        inOrder.verify(storage).store(copy);
        inOrder.verify(inv).unlockInventory();
    }

    @Test
    void store_insufficientQuantity_sendsEnableActions_andDoesNotStore() {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 5);     // less than requested 10
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);
        when(inv.getItem((short) 1)).thenReturn(item);
        when(storage.isFull()).thenReturn(false);
        when(storage.getStoreFee()).thenReturn(STORE_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 10), client, isPickupRestricted, getName);
        }

        verify(storage, never()).store(any());
        verify(chr, never()).setUsedStorage();
        verify(client).sendPacket(any());
        // Inventory lock still acquired/released cleanly for the validation block.
        verify(inv).lockInventory();
        verify(inv).unlockInventory();
        // Transfer lock acquired and released even on the early-return path.
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    @Test
    void store_storageFull_doesNotRemoveFromInventory() {
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);
        when(storage.isFull()).thenReturn(true);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 1), client, isPickupRestricted, getName);
        }

        verify(inv, never()).lockInventory();
        verify(storage, never()).store(any());
        verify(chr, never()).setUsedStorage();
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    @Test
    void store_unlocksTransferEvenWhenStoreThrows() {
        Item item = mock(Item.class);
        Item copy = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 100);
        when(item.copy()).thenReturn(copy);
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);
        when(inv.getItem((short) 1)).thenReturn(item);
        when(storage.isFull()).thenReturn(false);
        when(storage.getStoreFee()).thenReturn(STORE_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);
        doThrow(new RuntimeException("simulated crash mid-store")).when(storage).store(copy);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            try {
                StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 10), client, isPickupRestricted, getName);
            } catch (RuntimeException expected) {
                // expected
            }
        }

        // transferLock is held in a finally: must be released even on exception.
        verify(chr).unlockTransfer();
        // Inventory lock is held in its own finally too.
        verify(inv).unlockInventory();
    }

    // ---------------- TAKE OUT (mode 4) ----------------

    @Test
    void takeOut_validItem_acquiresTransferLockBeforeTakeOutAndAddFromDrop() {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 10);
        when(item.getInventoryType()).thenReturn(InventoryType.ETC);
        when(storage.getSlots()).thenReturn((byte) 4);
        when(storage.getSlot(eq(InventoryType.ETC), anyByte())).thenReturn((byte) 0);
        when(storage.getItem((byte) 0)).thenReturn(item);
        when(storage.getTakeOutFee()).thenReturn(TAKEOUT_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(storage.takeOut(item)).thenReturn(true);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            im.when(() -> InventoryManipulator.checkSpace(eq(client), anyInt(), anyInt(), any())).thenReturn(true);

            StorageProcessor.storageAction(takeOutPacket((byte) InventoryType.ETC.getType(), (byte) 0), client, isPickupRestricted, getName);
        }

        InOrder inOrder = inOrder(chr, inv, storage);
        inOrder.verify(chr).lockTransfer();
        inOrder.verify(inv).lockInventory();
        inOrder.verify(storage).takeOut(item);
        inOrder.verify(inv).unlockInventory();
        inOrder.verify(chr).unlockTransfer();
        verify(chr).setUsedStorage();
    }

    @Test
    void takeOut_checkSpaceFails_doesNotTakeOutFromStorage() {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(ETC_ITEM_ID);
        when(item.getQuantity()).thenReturn((short) 10);
        when(storage.getSlots()).thenReturn((byte) 4);
        when(storage.getSlot(eq(InventoryType.ETC), anyByte())).thenReturn((byte) 0);
        when(storage.getItem((byte) 0)).thenReturn(item);
        when(storage.getTakeOutFee()).thenReturn(TAKEOUT_FEE);
        when(chr.getMeso()).thenReturn(1_000_000);

        try (MockedStatic<InventoryManipulator> im = mockStatic(InventoryManipulator.class);
             MockedStatic<KarmaManipulator> km = mockStatic(KarmaManipulator.class)) {
            im.when(() -> InventoryManipulator.checkSpace(eq(client), anyInt(), anyInt(), any())).thenReturn(false);

            StorageProcessor.storageAction(takeOutPacket((byte) InventoryType.ETC.getType(), (byte) 0), client, isPickupRestricted, getName);
        }

        verify(storage, never()).takeOut(any());
        verify(chr, never()).setUsedStorage();
        verify(inv, never()).lockInventory();
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    // ---------------- MESO TRANSFER (mode 7) ----------------

    @Test
    void mesoTransfer_insufficientFunds_sendsEnableActions_andDoesNotMutate() {
        when(storage.getMeso()).thenReturn(50);          // storage has less than requested 100
        when(chr.getMeso()).thenReturn(0);                // player can't deposit either

        StorageProcessor.storageAction(mesoPacket(100), client, isPickupRestricted, getName);

        verify(storage, never()).setMeso(anyInt());
        verify(chr, never()).gainMeso(anyInt(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(chr, never()).setUsedStorage();
        verify(client).sendPacket(any());
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    @Test
    void mesoTransfer_validWithdraw_movesMesosAndPersistsUsage() {
        when(storage.getMeso()).thenReturn(1_000);
        when(chr.getMeso()).thenReturn(0);

        StorageProcessor.storageAction(mesoPacket(100), client, isPickupRestricted, getName);

        verify(storage).setMeso(900);
        verify(chr).gainMeso(eq(100), anyBoolean(), anyBoolean(), anyBoolean());
        verify(chr).setUsedStorage();
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    // ---------------- GM RESTRICTION ----------------

    @Test
    void gmBelowMinimumLevel_blocked_sendsMessage_andDoesNotTouchStorage() {
        when(chr.isGM()).thenReturn(true);
        when(chr.gmLevel()).thenReturn(2);                 // below MINIMUM_GM_LEVEL_TO_USE_STORAGE (3)
        when(chr.getInventory(InventoryType.ETC)).thenReturn(inv);
        when(inv.getSlotLimit()).thenReturn(24);

        StorageProcessor.storageAction(storePacket((short) 1, ETC_ITEM_ID, (short) 10), client, isPickupRestricted, getName);

        verify(storage, never()).store(any());
        verify(inv, never()).lockInventory();
        verify(chr).dropMessage(eq(1), any());
        // GM block early-returns, but the transfer lock is still released (held in finally).
        verify(chr).lockTransfer();
        verify(chr).unlockTransfer();
    }

    // ---------------- Packet builders ----------------

    private static InPacket storePacket(short slot, int itemId, short quantity) {
        return buildPacket(out -> {                       // mode = Store
            out.writeByte((byte) 5);
            out.writeShort(slot);
            out.writeInt(itemId);
            out.writeShort(quantity);
        });
    }

    private static InPacket takeOutPacket(byte type, byte slot) {
        return buildPacket(out -> {                       // mode = Take out
            out.writeByte((byte) 4);
            out.writeByte(type);
            out.writeByte(slot);
        });
    }

    private static InPacket mesoPacket(int meso) {
        return buildPacket(out -> {                       // mode = Mesos
            out.writeByte((byte) 7);
            out.writeInt(meso);
        });
    }

    private static InPacket buildPacket(Consumer<OutPacket> writer) {
        OutPacket builder = new ByteBufOutPacket();
        writer.accept(builder);
        return new ByteBufInPacket(Unpooled.wrappedBuffer(builder.getBytes()));
    }

    @SuppressWarnings("unused")
    private static byte anyByte() {
        return org.mockito.ArgumentMatchers.anyByte();
    }
}
