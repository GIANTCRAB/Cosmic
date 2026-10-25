package client;

import net.server.services.task.world.CharacterSaveService;
import net.server.services.type.WorldServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import testutil.CharacterStatJdbc;
import testutil.CharacterStatJdbc.Binding;
import testutil.CharacterStatJdbc.Stats;
import testutil.CharacterStatJdbc.Update;
import testutil.HpMpSession;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Production deferred-save closures and gameplay reloads against a recording JDBC fixture. */
class CharacterHpMpPersistenceTest {
    private static final int CHARACTER_ID = 741_001;
    private static final Stats INITIAL = new Stats(12_001, 9_002, 70_000, 50_000, 80_007, 90_009);

    private HpMpSession session;
    private CharacterStatJdbc jdbc;
    private MockedStatic<DatabaseConnection> database;
    private CharacterSaveService saves;
    private Character live;

    @BeforeEach
    void setUp() throws SQLException {
        // HpMpSession initializes ItemInformationProviderMocks before this DatabaseConnection mock.
        session = new HpMpSession();
        jdbc = new CharacterStatJdbc(CHARACTER_ID, session.map.getId(), INITIAL);
        database = mockStatic(DatabaseConnection.class);
        database.when(DatabaseConnection::getConnection).thenAnswer(invocation -> jdbc.openConnection());
        saves = mock(CharacterSaveService.class);
        when(session.world.getServiceAccess(WorldServices.SAVE_CHARACTER)).thenReturn(saves);

        // getDefault() is not logged in and has no monster book. Use the actual gameplay loader
        // to make the full save reachable instead of spying on Character or patching private state.
        live = loadForGameplay(session.client);
        session.worldPlayers.removePlayer(session.chr.getId());
        session.channelPlayers.removePlayer(session.chr.getId());
        installAsCurrentPlayer(live);
        assertStats(INITIAL, live);
        jdbc.clearObservations();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
        if (session != null) {
            session.close();
        }
        MetaProgressionToggle.clear(CHARACTER_ID);
    }

    @ParameterizedTest
    @CsvSource({"70000, 50000", "2147483579, 2147483647"})
    void deferredSaveReadsOriginalLiveCharacterAtExecutionAndGameplayReloadsCommittedIntegers(
            int startingMaxHp, int startingMaxMp) throws SQLException {
        // Load a different, fully initialized object with the SAME id before observing saves.
        // Replacing session/storage lookups below must not redirect the captured closure to it.
        Character replacement = loadForGameplay(session.newClient(1));
        replacement.updateMaxHpMaxMp(88_888, 77_777);
        replacement.updateHpMp(1_003, 2_005);
        jdbc.clearObservations();

        live.updateMaxHpMaxMp(startingMaxHp, startingMaxMp);
        live.updateHpMp(12_345, 23_456);
        assertTrue(live.applyApReset(Stat.MAXMP, Stat.MAXHP, 12, 16, 50, 5));
        Stats atRegistration = new Stats(12_345, 23_456, startingMaxHp + 16, startingMaxMp - 12,
                80_007, 90_009);
        assertStats(atRegistration, live);

        Runnable deferred = queueRealSave();
        assertNoSaveWorkYet();

        assertTrue(live.applyApReset(Stat.STR, Stat.MAXMP, 1, 9, 50, 5));
        assertTrue(live.assignHP(36, 2));
        installAsCurrentPlayer(replacement);
        assertSame(replacement, session.client.getPlayer());
        assertSame(replacement, session.worldPlayers.getCharacterById(CHARACTER_ID));
        assertSame(replacement, session.channelPlayers.getCharacterById(CHARACTER_ID));
        assertTrue(live.applyApReset(Stat.MAXMP, Stat.MAXHP, 12, 16, 50, 5));
        live.updateHpMp(21_011, 17_013);
        Stats latest = new Stats(21_011, 17_013, startingMaxHp + 68, startingMaxMp - 15,
                80_005, 90_012);
        assertStats(latest, live);
        assertNoSaveWorkYet();
        verifyNoMoreInteractions(saves);

        jdbc.beforeCommit(pending -> {
            assertEquals(INITIAL, jdbc.committedStats(), "executeUpdate must not publish uncommitted stats");
            assertEquals(latest, pending, "The staged row must contain the last HP/MP pair and AP counters");
        });
        deferred.run(); // The Runnable registered by Character.saveCharToDB(), not a test-made save.

        assertEquals(1, jdbc.connectionsOpened());
        assertEquals(1, jdbc.characterUpdates().size());
        assertEquals(1, jdbc.commitAttempts());
        assertEquals(1, jdbc.commits());
        assertEquals(0, jdbc.rollbacks());
        assertSavedBindings(latest, jdbc.characterUpdates().getFirst());
        assertEquals(11, jdbc.characterUpdates().getFirst().bindings().get(3).value());
        assertEquals(latest, jdbc.committedStats());
        verify(session.server).updateCharacterEntry(same(live));
        verify(session.server, never()).updateCharacterEntry(same(replacement));

        // A later mutation must not feed the reload. It reads a new ResultSet of committed bindings.
        live.updateMaxHpMaxMp(123_456, 654_321);
        live.updateHpMp(3_007, 4_009);
        Character reloaded = loadForGameplay(session.newClient(1));
        assertNotSame(live, reloaded);
        assertNotSame(replacement, reloaded);
        assertStats(latest, reloaded);
        assertEquals(30_000, reloaded.getClientMaxHp());
        assertEquals(30_000, reloaded.getClientMaxMp());
        assertEquals(30_000, reloaded.getCurrentMaxHp());
        assertEquals(30_000, reloaded.getCurrentMaxMp());
        assertEquals(11, reloaded.getStr());
        assertEquals(latest, jdbc.committedStats());
    }

    @Test
    void failedDeferredCommitRollsBackStagedStatsAndGameplayReloadSeesPreviousRow() throws SQLException {
        live.updateMaxHpMaxMp(90_000, 80_000);
        live.updateHpMp(19_007, 13_009);
        assertTrue(live.assignMP(9, 1));
        Stats attempted = new Stats(19_007, 13_009, 90_000, 80_009, 80_006, 90_010);
        assertStats(attempted, live);
        Runnable deferred = queueRealSave();
        assertNoSaveWorkYet();

        jdbc.beforeCommit(pending -> {
            assertEquals(attempted, pending);
            assertEquals(INITIAL, jdbc.committedStats());
        });
        jdbc.failNextCommit();
        deferred.run(); // Character catches/logs the SQLException, so check the transaction effects.

        assertEquals(1, jdbc.characterUpdates().size());
        assertSavedBindings(attempted, jdbc.characterUpdates().getFirst());
        assertEquals(1, jdbc.commitAttempts());
        assertEquals(0, jdbc.commits());
        assertEquals(1, jdbc.rollbacks());
        assertEquals(INITIAL, jdbc.committedStats());
        assertStats(attempted, live);

        Character reloaded = loadForGameplay(session.newClient(1));
        assertNotSame(live, reloaded);
        assertStats(INITIAL, reloaded);
    }

    private Character loadForGameplay(Client client) throws SQLException {
        Character loaded = Character.loadCharFromDB(CHARACTER_ID, client, true);
        assertNotNull(loaded, "The real channel/gameplay loader must finish successfully");
        assertTrue(loaded.isLoggedin(), "Do not silently skip the full save's loggedIn guard");
        assertSame(session.map, loaded.getMap());
        assertNotNull(loaded.getMonsterBook(), "The full save needs the loader-initialized monster book");
        client.setPlayer(loaded);
        return loaded;
    }

    private void installAsCurrentPlayer(Character character) {
        session.client.setPlayer(character);
        session.worldPlayers.addPlayer(character);
        session.channelPlayers.addPlayer(character);
    }

    private Runnable queueRealSave() {
        live.saveCharToDB();
        ArgumentCaptor<Runnable> action = ArgumentCaptor.forClass(Runnable.class);
        verify(saves).registerSaveCharacter(eq(CHARACTER_ID), action.capture());
        return action.getValue();
    }

    private void assertNoSaveWorkYet() {
        assertEquals(0, jdbc.connectionsOpened(), "Mutations and deferred registration must not open JDBC");
        assertEquals(0, jdbc.executedUpdates(), "An unchanged row before the queued save runs is expected");
        assertTrue(jdbc.characterUpdates().isEmpty());
        assertEquals(0, jdbc.commitAttempts());
        assertEquals(0, jdbc.rollbacks());
        assertEquals(INITIAL, jdbc.committedStats());
    }

    private static void assertStats(Stats expected, Character character) {
        assertEquals(expected, new Stats(character.getHp(), character.getMp(), character.getMaxHp(),
                character.getMaxMp(), character.getRemainingAp(), character.getHpMpApUsed()));
    }

    private static void assertSavedBindings(Stats expected, Update update) {
        assertIntegerBinding(update, 9, expected.hp());
        assertIntegerBinding(update, 10, expected.mp());
        assertIntegerBinding(update, 11, expected.maxHp());
        assertIntegerBinding(update, 12, expected.maxMp());
        assertIntegerBinding(update, 14, expected.ap());
        assertIntegerBinding(update, 23, expected.hpMpUsed());
        assertIntegerBinding(update, 56, CHARACTER_ID);
    }

    private static void assertIntegerBinding(Update update, int index, int expected) {
        Binding binding = update.bindings().get(index);
        assertNotNull(binding, "Missing SQL parameter " + index);
        assertEquals("setInt", binding.setter(), "JDBC type for SQL parameter " + index);
        assertEquals(expected, assertInstanceOf(Integer.class, binding.value()), "SQL parameter " + index);
    }
}
