package client;

import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.processor.stat.AssignAPProcessor;
import constants.id.MapId;
import net.opcodes.SendOpcode;
import net.packet.Packet;
import net.server.PlayerStorage;
import net.server.channel.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.ItemInformationProvider;
import server.maps.GenericPortal;
import server.maps.MapManager;
import server.maps.MapleMap;
import testutil.HpMpSession;
import tools.PacketCreator;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CharacterHpMpLifecycleTest {
    @Test
    void resetsKeepClientWorldAndChannelReferencesOnTheSameLiveCharacter() {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, 50_000, 3);
            assertLiveRegistrations(session);

            for (int washes = 1; washes <= 3; washes++) {
                session.useReset(Stat.MAXMP, Stat.MAXHP);

                assertLiveRegistrations(session);
                assertEquals(5_000 + 16 * washes, session.chr.getMaxHp());
                assertEquals(50_000 - 12 * washes, session.chr.getMaxMp());
                assertEquals(1, session.chr.getHpMpApUsed());
                assertEquals(0, session.chr.getRemainingAp());
                assertEquals(3 - washes, session.resets.getQuantity());
            }
        }
    }

    @Test
    void preWashEntryKeepsItsScalarsWithoutReplacingTheRegisteredLiveCharacter() {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, 50_000, 2);
            Equip hat = new Equip(1002000, (short) -1);
            session.chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(hat);
            Character beforeWash = session.chr.generateCharacterEntry();

            assertNotSame(session.chr, beforeWash);
            assertEquals(session.chr.getId(), beforeWash.getId());
            assertEquals(session.chr.getName(), beforeWash.getName());
            assertEquals(session.chr.getLevel(), beforeWash.getLevel());
            assertEquals(session.chr.getJob(), beforeWash.getJob());
            assertNull(beforeWash.getClient());
            assertNull(beforeWash.getMap());

            // Current entry generation omits this counter and aliases EQUIPPED. These are
            // characterizations of a list view, not evidence that it becomes live wash state.
            assertEquals(0, beforeWash.getHpMpApUsed());
            assertEquals(1, session.chr.getHpMpApUsed());
            assertSame(session.chr.getInventory(InventoryType.EQUIPPED),
                    beforeWash.getInventory(InventoryType.EQUIPPED));
            assertSame(hat, beforeWash.getInventory(InventoryType.EQUIPPED).getItem((short) -1));
            assertNotSame(session.chr.getInventory(InventoryType.CASH),
                    beforeWash.getInventory(InventoryType.CASH));

            session.useReset(Stat.MAXMP, Stat.MAXHP);
            session.useReset(Stat.MAXMP, Stat.MAXHP);
            session.map.runCharacterStatUpdate();
            Character afterWash = session.chr.generateCharacterEntry();

            assertLiveRegistrations(session);
            assertNotSame(beforeWash, afterWash);
            assertNotSame(session.chr, afterWash);
            assertEquals(5_000, beforeWash.getHp());
            assertEquals(5_000, beforeWash.getMaxHp());
            assertEquals(30_000, beforeWash.getMp());
            assertEquals(50_000, beforeWash.getMaxMp());
            assertEquals(0, beforeWash.getRemainingAp());
            assertEquals(5_032, session.chr.getMaxHp());
            assertEquals(49_976, session.chr.getMaxMp());
            assertEquals(5_032, afterWash.getMaxHp());
            assertEquals(49_976, afterWash.getMaxMp());
            assertEquals(1, session.chr.getHpMpApUsed());
            assertEquals(0, afterWash.getHpMpApUsed());
        }
    }

    @Test
    void generatedViewNativeListenerRecalculatesAndAnnouncesOnlyItsOwnPools() {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, 50_000, 1);
            Character view = session.chr.generateCharacterEntry();
            Client viewClient = session.newClient(1);
            view.setClient(viewClient);
            viewClient.setPlayer(view);
            view.setMap(session.map);

            view.updateHpMaxHp(1_234, 40_000);
            view.updateMpMaxMp(2_345, 65_536);

            assertSame(view, viewClient.getPlayer());
            assertSame(viewClient, view.getClient());
            assertEquals(40_000, view.getMaxHp());
            assertEquals(65_536, view.getMaxMp());
            assertEquals(30_000, view.getCurrentMaxHp());
            assertEquals(30_000, view.getCurrentMaxMp());
            assertEquals(1_234, view.getHp());
            assertEquals(2_345, view.getMp());
            assertEquals(2, session.packets(viewClient).size());
            assertEquals(Map.of(Stat.HP, 1_234, Stat.MAXHP, 30_000),
                    statUpdate(session.packets(viewClient).get(0)));
            assertEquals(Map.of(Stat.MP, 2_345, Stat.MAXMP, 30_000),
                    statUpdate(session.packets(viewClient).get(1)));
            assertTrue(session.packets(session.client).isEmpty());
            assertEquals(5_000, session.chr.getHp());
            assertEquals(5_000, session.chr.getMaxHp());
            assertEquals(30_000, session.chr.getMp());
            assertEquals(50_000, session.chr.getMaxMp());
            assertEquals(1, session.chr.getHpMpApUsed());
            assertEquals(0, view.getHpMpApUsed());
            assertLiveRegistrations(session);

            session.useReset(Stat.MAXMP, Stat.MAXHP);
            session.map.runCharacterStatUpdate();

            assertEquals(5_016, session.chr.getMaxHp());
            assertEquals(49_988, session.chr.getMaxMp());
            assertEquals(0, session.resets.getQuantity());
            assertEquals(40_000, view.getMaxHp());
            assertEquals(65_536, view.getMaxMp());
            assertEquals(1_234, view.getHp());
            assertEquals(2_345, view.getMp());
            assertEquals(2, session.packets(viewClient).size());
            assertFalse(session.packets(session.client).isEmpty());
            assertLiveRegistrations(session);
        }
    }

    @Test
    void drainingQueuedMapCallbacksAfterRepeatedResetsDoesNotRestoreMaxMp() {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, 50_000, 3);
            Character beforeWash = session.chr.generateCharacterEntry();
            session.map.runCharacterStatUpdate();
            CountingCallbackMap map = new CountingCallbackMap(session.map.getId());
            session.chr.setMap(map);

            for (int washes = 1; washes <= 3; washes++) {
                // A real HP update queues the character's native map callback before the wash.
                session.chr.updateHp(session.chr.getHp() - 1);
                session.useReset(Stat.MAXMP, Stat.MAXHP);
                session.chr.updateHp(session.chr.getHp() - 1);

                assertEquals(2 * washes, map.registeredCallbacks);
                assertEquals(2 * (washes - 1), map.completedCallbacks);
                assertEquals(50_000 - 12 * washes, session.chr.getMaxMp());
                map.runCharacterStatUpdate();

                assertEquals(2 * washes, map.completedCallbacks, "Native callbacks must actually run");
                assertEquals(5_000 + 16 * washes, session.chr.getMaxHp());
                assertEquals(50_000 - 12 * washes, session.chr.getMaxMp());
                assertEquals(30_000, session.chr.getClientMaxMp());
                assertEquals(30_000, session.chr.getCurrentMaxMp());
                assertEquals(1, session.chr.getHpMpApUsed());
                assertEquals(0, session.chr.getRemainingAp());
                assertEquals(3 - washes, session.resets.getQuantity());
                assertEquals(50_000, beforeWash.getMaxMp());
                assertLiveRegistrations(session, session.client, session.channel, session.channelPlayers, map);

                map.runCharacterStatUpdate();
                assertEquals(2 * washes, map.completedCallbacks, "A second drain must not replay callbacks");
                assertEquals(50_000 - 12 * washes, session.chr.getMaxMp());
            }
        }
    }

    @Test
    void newClientHandoffRetainsTheWorldStoredInstanceInventoriesAndSpentMp() {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, 50_000, 2);
            Character beforeWash = session.chr.generateCharacterEntry();
            Inventory cash = session.chr.getInventory(InventoryType.CASH);
            Inventory equipped = session.chr.getInventory(InventoryType.EQUIPPED);
            session.useReset(Stat.MAXMP, Stat.MAXHP);
            assertEquals(49_988, session.chr.getMaxMp());
            assertEquals(1, session.resets.getQuantity());
            assertLiveRegistrations(session);

            Channel destination = mock(Channel.class);
            MapManager destinationMaps = mock(MapManager.class);
            PlayerStorage destinationPlayers = new PlayerStorage();
            MapleMap destinationMap = new MapleMap(session.map.getId(), 0, 2, MapId.NONE, 1);
            GenericPortal spawn = new GenericPortal(0);
            spawn.setId(0);
            spawn.setName("sp");
            spawn.setPosition(new Point(175, 30));
            spawn.setTargetMapId(MapId.NONE);
            destinationMap.addPortal(spawn);
            when(session.server.getChannel(0, 2)).thenReturn(destination);
            when(session.world.getChannel(2)).thenReturn(destination);
            when(destination.getPlayerStorage()).thenReturn(destinationPlayers);
            when(destination.getMapFactory()).thenReturn(destinationMaps);
            when(destinationMaps.getMap(session.map.getId())).thenReturn(destinationMap);

            session.client.setAccountName("washing-account");
            session.client.setHighestLevelAchieved(119);
            Client replacement = session.newClient(2);
            Character transferred = session.world.getPlayerStorage().getCharacterById(session.chr.getId());
            assertSame(session.chr, transferred);
            assertNotSame(beforeWash, transferred);

            // Exercise the live-object handoff used by login, without partially driving its handler.
            assertSame(transferred, session.channelPlayers.removePlayer(transferred.getId()));
            replacement.setPlayer(transferred);
            transferred.newClient(replacement);
            destinationPlayers.addPlayer(transferred);
            session.client.setPlayer(null);

            verify(destinationMaps).getMap(session.map.getId());
            assertLiveRegistrations(session, replacement, destination, destinationPlayers, destinationMap);
            assertNull(session.channelPlayers.getCharacterById(transferred.getId()));
            assertNull(session.channelPlayers.getCharacterByName(transferred.getName()));
            assertEquals(0, session.channelPlayers.getSize());
            assertEquals("washing-account", replacement.getAccountName());
            assertEquals(119, replacement.getHighestLevelAchieved());
            assertEquals(new Point(175, 30), transferred.getPosition());
            assertTrue(transferred.isLoggedin());
            assertSame(cash, transferred.getInventory(InventoryType.CASH));
            assertSame(equipped, transferred.getInventory(InventoryType.EQUIPPED));
            assertSame(session.resets, cash.getItem((short) 1));
            assertEquals(5_016, transferred.getMaxHp());
            assertEquals(49_988, transferred.getMaxMp());
            assertEquals(1, transferred.getHpMpApUsed());
            assertEquals(0, transferred.getRemainingAp());

            session.packets(session.client).clear();
            session.packets(replacement).clear();
            // The fixture's cash-item helper targets the original client; exercise AP processing
            // directly on the replacement to verify its retained live eligibility and listener.
            assertTrue(AssignAPProcessor.APResetAction(replacement,
                    Stat.MAXMP.getValue(), Stat.MAXHP.getValue()));
            session.map.runCharacterStatUpdate();
            destinationMap.runCharacterStatUpdate();

            assertEquals(5_032, transferred.getMaxHp());
            assertEquals(49_976, transferred.getMaxMp());
            assertEquals(1, transferred.getHpMpApUsed());
            assertEquals(0, transferred.getRemainingAp());
            assertEquals(1, session.packets(replacement).size());
            assertEquals(Map.of(Stat.HP, 5_000, Stat.MAXHP, 5_032, Stat.MP, 30_000,
                            Stat.MAXMP, 30_000, Stat.AVAILABLEAP, 0),
                    statUpdate(session.packets(replacement).getFirst()));
            assertTrue(session.packets(session.client).isEmpty());
            assertEquals(50_000, beforeWash.getMaxMp());
            assertEquals(1, session.resets.getQuantity());
            assertLiveRegistrations(session, replacement, destination, destinationPlayers, destinationMap);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {29_999, 30_000, 30_001, 32_768, 65_536, Integer.MAX_VALUE})
    void cacheListEntryAndLiveMaxMpRetainIndependentIntegersAndClientCaps(int maximumMp) {
        try (HpMpSession session = new HpMpSession()) {
            session.preparePools(5_000, maximumMp, 2);
            ItemInformationProvider items = ItemInformationProvider.getInstance();
            when(items.canWearEquipment(any(), any())).thenReturn(List.of());
            Character beforeWash = session.chr.generateCharacterEntry();
            List<Character> cachedList = List.of(beforeWash);

            for (int washes = 1; washes <= 2; washes++) {
                session.useReset(Stat.MAXMP, Stat.MAXHP);
                session.map.runCharacterStatUpdate();
                int spentMaximumMp = maximumMp - 12 * washes;
                Character refreshed = session.chr.generateCharacterEntry();

                assertSame(beforeWash, cachedList.getFirst());
                assertNotSame(session.chr, cachedList.getFirst());
                assertEquals(maximumMp, beforeWash.getMaxMp());
                assertEquals(Math.min(30_000, maximumMp), beforeWash.getClientMaxMp());
                assertEquals(spentMaximumMp, session.chr.getMaxMp());
                assertEquals(Math.min(30_000, spentMaximumMp), session.chr.getClientMaxMp());
                assertEquals(Math.min(30_000, spentMaximumMp), session.chr.getCurrentMaxMp());
                assertEquals(spentMaximumMp, refreshed.getMaxMp());
                assertEquals(Math.min(30_000, spentMaximumMp), refreshed.getClientMaxMp());
                assertListedMaxMp(cachedList, Math.min(30_000, maximumMp));
                assertListedMaxMp(List.of(refreshed), Math.min(30_000, spentMaximumMp));
                assertEquals(0, beforeWash.getHpMpApUsed());
                assertEquals(0, refreshed.getHpMpApUsed());
                assertEquals(1, session.chr.getHpMpApUsed());
                assertEquals(0, session.chr.getRemainingAp());
                assertEquals(5_000 + 16 * washes, session.chr.getMaxHp());
                assertEquals(2 - washes, session.resets.getQuantity());
                assertLiveRegistrations(session);
            }
        }
    }

    private static void assertLiveRegistrations(HpMpSession session) {
        assertLiveRegistrations(session, session.client, session.channel, session.channelPlayers, session.map);
    }

    private static void assertLiveRegistrations(HpMpSession session, Client client, Channel channel,
                                                PlayerStorage channelPlayers, MapleMap map) {
        assertSame(session.chr, client.getPlayer());
        assertSame(client, session.chr.getClient());
        assertSame(session.world, client.getWorldServer());
        assertSame(session.world, session.chr.getWorldServer());
        assertSame(channel, client.getChannelServer());
        assertSame(channel, session.world.getChannel(client.getChannel()));
        assertSame(session.worldPlayers, client.getWorldServer().getPlayerStorage());
        assertSame(channelPlayers, client.getChannelServer().getPlayerStorage());
        assertSame(session.chr, session.worldPlayers.getCharacterById(session.chr.getId()));
        assertSame(session.chr, session.worldPlayers.getCharacterByName(session.chr.getName()));
        assertSame(session.chr, channelPlayers.getCharacterById(session.chr.getId()));
        assertSame(session.chr, channelPlayers.getCharacterByName(session.chr.getName()));
        assertEquals(1, session.worldPlayers.getSize());
        assertEquals(1, channelPlayers.getSize());
        assertSame(map, session.chr.getMap());
    }

    private static Map<Stat, Integer> statUpdate(byte[] packet) {
        ByteBuffer body = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(SendOpcode.STAT_CHANGED.getValue(), Short.toUnsignedInt(body.getShort()));
        assertEquals(1, body.get());
        int mask = body.getInt();
        Map<Stat, Integer> values = new EnumMap<>(Stat.class);
        for (Stat stat : List.of(Stat.HP, Stat.MAXHP, Stat.MP, Stat.MAXMP, Stat.AVAILABLEAP)) {
            if ((mask & stat.getValue()) != 0) {
                values.put(stat, Short.toUnsignedInt(body.getShort()));
                mask &= ~stat.getValue();
            }
        }
        assertEquals(0, mask, "Unexpected stats in pool-only update");
        assertEquals(0, body.remaining());
        return values;
    }

    private static void assertListedMaxMp(List<Character> entries, int maximumMp) {
        Packet packet = PacketCreator.showAllCharacterInfo(0, entries, false);
        ByteBuffer body = ByteBuffer.wrap(packet.getBytes()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(SendOpcode.VIEW_ALL_CHAR.getValue(), Short.toUnsignedInt(body.getShort()));
        assertEquals(0, body.get()); // status
        assertEquals(0, body.get()); // world
        assertEquals(1, body.get()); // entry count
        assertEquals(entries.getFirst().getId(), body.getInt());
        // Fixed name, appearance, three pet IDs, level/job, four primary stats, HP/maxHP/MP.
        body.position(body.position() + 13 + 1 + 1 + 4 + 4 + 3 * 8 + 1 + 2 + 4 * 2 + 3 * 2);
        assertEquals(maximumMp, Short.toUnsignedInt(body.getShort()));
    }

    /** Observes native callback execution while retaining MapleMap's actual queue and drain. */
    private static final class CountingCallbackMap extends MapleMap {
        private int registeredCallbacks;
        private int completedCallbacks;

        private CountingCallbackMap(int mapId) {
            super(mapId, 0, 1, MapId.NONE, 1);
        }

        @Override
        public void registerCharacterStatUpdate(Runnable callback) {
            registeredCallbacks++;
            super.registerCharacterStatUpdate(() -> {
                callback.run();
                completedCallbacks++;
            });
        }
    }
}
