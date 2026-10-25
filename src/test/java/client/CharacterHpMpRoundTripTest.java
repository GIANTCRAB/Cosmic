package client;

import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import constants.id.ItemId;
import net.server.channel.handlers.UseCashItemHandler;
import net.server.coordinator.partysearch.PartySearchCoordinator;
import net.server.coordinator.session.SessionCoordinator;
import net.server.services.task.world.CharacterSaveService;
import net.server.services.type.WorldServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import service.NoteService;
import testutil.CharacterStatJdbc;
import testutil.CharacterStatJdbc.Binding;
import testutil.CharacterStatJdbc.Stats;
import testutil.CharacterStatJdbc.Update;
import testutil.HpMpSession;
import testutil.Packets;
import tools.DatabaseConnection;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Handler consumption, deferred full save, real forced logout, and gameplay reload via JDBC. */
class CharacterHpMpRoundTripTest {
    private static final int CHARACTER_ID = 741_002;
    private static final Stats INITIAL = new Stats(29_992, 30_000, 29_992, 30_465, 0, 1);

    private final UseCashItemHandler handler = new UseCashItemHandler(mock(NoteService.class));
    private final List<Runnable> queuedSaves = new ArrayList<>();
    private HpMpSession session;
    private CharacterStatJdbc jdbc;
    private MockedStatic<DatabaseConnection> database;
    private MockedStatic<SessionCoordinator> coordinators;
    private SessionCoordinator coordinator;
    private PartySearchCoordinator partySearch;
    private CharacterSaveService saves;
    private Character first;

    @BeforeEach
    void setUp() throws SQLException {
        // The shared session initializes the item-provider singleton before the JDBC static mock.
        session = new HpMpSession();
        coordinator = mock(SessionCoordinator.class);
        coordinators = mockStatic(SessionCoordinator.class);
        coordinators.when(SessionCoordinator::getInstance).thenReturn(coordinator);
        partySearch = mock(PartySearchCoordinator.class);
        when(session.world.getPartySearchCoordinator()).thenReturn(partySearch);

        // Model the mocked world's/channel's removal hooks using the real PlayerStorages.
        when(session.channel.removePlayer(any(Character.class))).thenAnswer(invocation -> {
            Character character = invocation.getArgument(0);
            return session.channelPlayers.removePlayer(character.getId()) != null;
        });
        doAnswer(invocation -> {
            Character character = invocation.getArgument(0);
            character.getClient().getChannelServer().removePlayer(character);
            session.worldPlayers.removePlayer(character.getId());
            return null;
        }).when(session.world).removePlayer(any(Character.class));

        jdbc = new CharacterStatJdbc(CHARACTER_ID, session.map.getId(), INITIAL, 5_000);
        database = mockStatic(DatabaseConnection.class);
        database.when(DatabaseConnection::getConnection).thenAnswer(invocation -> jdbc.openConnection());
        saves = mock(CharacterSaveService.class);
        when(session.world.getServiceAccess(WorldServices.SAVE_CHARACTER)).thenReturn(saves);
        doAnswer(invocation -> {
            queuedSaves.add(invocation.getArgument(1));
            return null;
        }).when(saves).registerSaveCharacter(eq(CHARACTER_ID), any(Runnable.class));

        session.worldPlayers.removePlayer(session.chr.getId());
        session.channelPlayers.removePlayer(session.chr.getId());
        first = loadForGameplay(session.client);
        assertStats(INITIAL, first);
        assertResetStack(first, 5_000);
        jdbc.clearObservations();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
        if (coordinators != null) {
            coordinators.close();
        }
        if (session != null) {
            session.close();
        }
        MetaProgressionToggle.clear(CHARACTER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consumedResetsAndRawPoolsSurviveDeferredSaveForcedLogoutAndFreshGameplayLoad(
            boolean ratioUpdate) throws SQLException {
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        Item firstStack = resetStack(first);
        Runnable firstSave = queueSave(first); // Registered before any of the 1,200 mutations.
        assertNoSaveWorkYet(INITIAL, 5_000);

        wash(session.client, 1_200);
        Stats halfway = new Stats(ratioUpdate ? 30_000 : 29_992, 16_065, 49_192, 16_065, 0, 1);
        assertStats(halfway, first);
        assertSame(firstStack, resetStack(first));
        assertResetStack(first, 3_800);
        assertNoSaveWorkYet(INITIAL, 5_000);

        observeNextCommit(first, halfway, 3_800, INITIAL, 5_000);
        firstSave.run(); // The actual runnable registered by Character.saveCharToDB().
        assertFullSave(halfway, 3_800);
        assertEquals(List.of("character.update", "inventory.delete", "inventory.insert", "commit"), jdbc.events());

        // Keep a genuinely unexecuted closure from this generation. forceDisconnect flushes
        // synchronously before logOff, rather than depending on the deferred save service.
        jdbc.clearObservations();
        Runnable firstOutstanding = queueSave(first);
        assertNoSaveWorkYet(halfway, 3_800);
        observeNextCommit(first, halfway, 3_800, halfway, 3_800);
        session.client.forceDisconnect();
        assertForcedLogout(session.client, first, halfway, 3_800);

        Client secondClient = session.newClient(1);
        Character second = loadForGameplay(secondClient);
        assertNotSame(first, second);
        assertNotSame(firstStack, resetStack(second));
        assertStats(halfway, second);
        assertResetStack(second, 3_800);
        assertTrue(session.packets(secondClient).isEmpty());
        int oldClientPackets = session.packets(session.client).size();
        jdbc.clearObservations();

        Runnable secondOutstanding = queueSave(second); // Again registered before the mutations.
        Item secondStack = resetStack(second);
        wash(secondClient, 1_200); // The handler receives this new Client, not HpMpSession.client.
        Stats exhausted = new Stats(ratioUpdate ? 30_000 : 29_992, 1_665, 68_392, 1_665, 0, 1);
        assertStats(exhausted, second);
        assertSame(secondStack, resetStack(second));
        assertResetStack(second, 2_600);
        assertEquals(oldClientPackets, session.packets(session.client).size());
        assertNoSaveWorkYet(halfway, 3_800);

        wash(secondClient, 1); // Source MP cannot pay another 12 above the bowman's floor.
        assertEquals(session.time.get(), second.getLastUsedCashItem());
        assertStats(exhausted, second);
        assertSame(secondStack, resetStack(second));
        assertResetStack(second, 2_600);
        assertNoSaveWorkYet(halfway, 3_800);

        observeNextCommit(second, exhausted, 2_600, halfway, 3_800);
        secondClient.forceDisconnect();
        assertForcedLogout(secondClient, second, exhausted, 2_600);

        Character fresh = loadForGameplay(session.newClient(1));
        assertNotSame(first, fresh);
        assertNotSame(second, fresh);
        assertNotSame(secondStack, resetStack(fresh));
        assertStats(exhausted, fresh);
        assertResetStack(fresh, 2_600);
        assertEquals(30_000, fresh.getClientMaxHp());
        assertEquals(30_000, fresh.getCurrentMaxHp());
        assertEquals(1_665, fresh.getClientMaxMp());
        assertEquals(1_665, fresh.getCurrentMaxMp());

        // The first stale object still has only the halfway stats. Neither old closure may
        // reopen JDBC or overwrite the new generation after its real client was logged out.
        assertStats(halfway, first);
        jdbc.clearObservations();
        firstOutstanding.run();
        secondOutstanding.run();
        assertNoSaveWorkYet(exhausted, 2_600);
        assertStats(exhausted, fresh);
        assertResetStack(fresh, 2_600);
        verify(saves, times(3)).registerSaveCharacter(eq(CHARACTER_ID), any(Runnable.class));
    }

    @Test
    void failedCommitRollsBackStatAndInventoryRowsAfterActualHandlerConsumption() throws SQLException {
        Runnable deferred = queueSave(first);
        wash(session.client, 1);
        Stats attempted = new Stats(29_992, 30_000, 30_008, 30_453, 0, 1);
        assertStats(attempted, first);
        assertResetStack(first, 4_999);
        assertNoSaveWorkYet(INITIAL, 5_000);

        observeNextCommit(first, attempted, 4_999, INITIAL, 5_000);
        jdbc.failNextCommit();
        deferred.run(); // The full save catches/logs the injected SQLException and rolls back.

        assertEquals(1, jdbc.characterUpdates().size());
        assertEquals(1, jdbc.inventoryDeletes().size());
        assertEquals(1, jdbc.resetInserts().size());
        assertEquals(new Binding("setInt", 4_999), jdbc.resetInserts().getFirst().bindings().get(7));
        assertEquals(1, jdbc.commitAttempts());
        assertEquals(0, jdbc.commits());
        assertEquals(1, jdbc.rollbacks());
        assertEquals(INITIAL, jdbc.committedStats());
        assertCommittedReset(5_000);
        assertEquals(List.of("character.update", "inventory.delete", "inventory.insert", "rollback"), jdbc.events());

        Character fresh = loadForGameplay(session.newClient(1));
        assertNotSame(first, fresh);
        assertNotSame(resetStack(first), resetStack(fresh));
        assertStats(INITIAL, fresh);
        assertResetStack(fresh, 5_000);
        assertStats(attempted, first);
        assertResetStack(first, 4_999);
    }

    private Character loadForGameplay(Client client) throws SQLException {
        Character loaded = Character.loadCharFromDB(CHARACTER_ID, client, true);
        assertNotNull(loaded, "The real gameplay loader must finish, including ItemFactory.loadItems");
        assertTrue(loaded.isLoggedin());
        assertNotNull(loaded.getMonsterBook());
        assertSame(session.map, loaded.getMap());
        client.setPlayer(loaded);
        client.updateLoginState(Client.LOGIN_LOGGEDIN);
        loaded.setEnteredChannelWorld();
        session.worldPlayers.addPlayer(loaded);
        session.channelPlayers.addPlayer(loaded);
        assertTrue(client.isLoggedIn());
        assertFalse(loaded.isAwayFromWorld());
        assertLiveReferences(client, loaded);
        return loaded;
    }

    private Runnable queueSave(Character character) {
        int previousCount = queuedSaves.size();
        character.saveCharToDB();
        assertEquals(previousCount + 1, queuedSaves.size());
        return queuedSaves.getLast();
    }

    private void wash(Client client, int requests) {
        Character target = client.getPlayer();
        assertNotNull(target);
        assertLiveReferences(client, target);
        for (int i = 0; i < requests; i++) {
            session.time.addAndGet(4_000);
            session.packets(client).clear();
            handler.handlePacket(Packets.buildInPacket(packet -> {
                packet.writeShort(1);
                packet.writeInt(ItemId.AP_RESET);
                packet.writeInt(Stat.MAXHP.getValue());
                packet.writeInt(Stat.MAXMP.getValue());
            }), client);
            session.map.runCharacterStatUpdate();
        }
        assertLiveReferences(client, target);
    }

    private void observeNextCommit(Character character, Stats pendingStats, int pendingQuantity,
                                   Stats committedStats, int committedQuantity) {
        jdbc.beforeCommit((pending, quantity) -> {
            assertTrue(character.isLoggedin(), "The full save must commit before logOff closes its guard");
            assertEquals(pendingStats, pending);
            assertEquals(pendingQuantity, quantity.intValue());
            assertEquals(committedStats, jdbc.committedStats(), "Stat executeUpdate must stay uncommitted");
            assertCommittedReset(committedQuantity); // Neither ItemFactory DELETE nor INSERT publishes yet.
        });
    }

    private void assertFullSave(Stats expected, int resetQuantity) {
        assertEquals(1, jdbc.characterUpdates().size());
        assertEquals(1, jdbc.inventoryDeletes().size());
        assertEquals(1, jdbc.resetInserts().size());
        assertEquals(1, jdbc.commitAttempts());
        assertEquals(1, jdbc.commits());
        assertEquals(0, jdbc.rollbacks());
        Map<Integer, Binding> stats = jdbc.characterUpdates().getFirst().bindings();
        assertEquals(new Binding("setInt", expected.hp()), stats.get(9));
        assertEquals(new Binding("setInt", expected.mp()), stats.get(10));
        assertEquals(new Binding("setInt", expected.maxHp()), stats.get(11));
        assertEquals(new Binding("setInt", expected.maxMp()), stats.get(12));
        assertEquals(new Binding("setInt", expected.ap()), stats.get(14));
        assertEquals(new Binding("setInt", expected.hpMpUsed()), stats.get(23));
        Update insert = jdbc.resetInserts().getFirst();
        assertEquals(new Binding("setString", String.valueOf(CHARACTER_ID)), insert.bindings().get(2));
        assertEquals(new Binding("setString", null), insert.bindings().get(3));
        assertEquals(new Binding("setInt", ItemId.AP_RESET), insert.bindings().get(4));
        assertEquals(new Binding("setInt", (int) InventoryType.CASH.getType()), insert.bindings().get(5));
        assertEquals(new Binding("setInt", resetQuantity), insert.bindings().get(7));
        assertEquals(expected, jdbc.committedStats());
        assertCommittedReset(resetQuantity);
    }

    private void assertForcedLogout(Client client, Character character, Stats expected, int quantity) {
        assertFullSave(expected, quantity);
        assertEquals(1, jdbc.logoutUpdates().size());
        assertTrue(jdbc.committedLastLogoutTime() > 0);
        assertEquals(Client.LOGIN_NOTLOGGEDIN, jdbc.committedLoginState());
        assertEquals(List.of("character.update", "inventory.delete", "inventory.insert", "commit",
                "character.logout", "account.loginState"), jdbc.events());
        assertFalse(character.isLoggedin());
        assertFalse(client.isLoggedIn());
        assertNull(client.getPlayer());
        assertNull(session.worldPlayers.getCharacterById(CHARACTER_ID));
        assertNull(session.channelPlayers.getCharacterById(CHARACTER_ID));
        assertNull(session.worldPlayers.getCharacterByName(character.getName()));
        assertNull(session.channelPlayers.getCharacterByName(character.getName()));
        verify(session.world).removePlayer(character);
        verify(session.channel).removePlayer(character);
        verify(partySearch).unregisterPartyLeader(character); // Detect removal aborting before this cleanup.
        verify(coordinator).closeSession(client, false);
    }

    private void assertNoSaveWorkYet(Stats committedStats, int committedQuantity) {
        assertEquals(0, jdbc.connectionsOpened());
        assertEquals(0, jdbc.executedUpdates(), "Cached resets and queued closures do not save immediately");
        assertTrue(jdbc.characterUpdates().isEmpty());
        assertTrue(jdbc.inventoryDeletes().isEmpty());
        assertTrue(jdbc.resetInserts().isEmpty());
        assertTrue(jdbc.logoutUpdates().isEmpty());
        assertTrue(jdbc.events().isEmpty());
        assertEquals(0, jdbc.commitAttempts());
        assertEquals(0, jdbc.rollbacks());
        assertEquals(committedStats, jdbc.committedStats());
        assertCommittedReset(committedQuantity);
    }

    private void assertCommittedReset(int quantity) {
        Map<String, Object> row = jdbc.committedResetRow();
        assertFalse(row.isEmpty());
        assertEquals(1, row.get("type"));
        assertEquals(CHARACTER_ID, row.get("characterid"));
        assertNull(row.get("accountid"));
        assertEquals(ItemId.AP_RESET, row.get("itemid"));
        assertEquals((int) InventoryType.CASH.getType(), row.get("inventorytype"));
        assertEquals(1, row.get("position"));
        assertEquals(quantity, row.get("quantity"));
        assertEquals(quantity, jdbc.committedResetQuantity());
    }

    private void assertLiveReferences(Client client, Character character) {
        assertSame(character, client.getPlayer());
        assertSame(client, character.getClient());
        assertSame(character, session.worldPlayers.getCharacterById(CHARACTER_ID));
        assertSame(character, session.channelPlayers.getCharacterById(CHARACTER_ID));
    }

    private static Item resetStack(Character character) {
        Item reset = character.getInventory(InventoryType.CASH).getItem((short) 1);
        assertNotNull(reset);
        assertEquals(ItemId.AP_RESET, reset.getItemId());
        return reset;
    }

    private static void assertResetStack(Character character, int quantity) {
        assertEquals(quantity, resetStack(character).getQuantity());
        assertEquals(quantity, character.getInventory(InventoryType.CASH).countById(ItemId.AP_RESET));
        assertEquals(1, character.getInventory(InventoryType.CASH).list().size());
    }

    private static void assertStats(Stats expected, Character character) {
        assertEquals(expected, new Stats(character.getHp(), character.getMp(), character.getMaxHp(),
                character.getMaxMp(), character.getRemainingAp(), character.getHpMpApUsed()));
    }
}
