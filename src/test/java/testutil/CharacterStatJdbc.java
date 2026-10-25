package testutil;

import client.Client;
import client.inventory.InventoryType;
import constants.id.ItemId;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

/**
 * Recording JDBC fixture for one character's stat row and an optional CASH AP_RESET stack.
 * Runs the production save and gameplay loader, but does not implement a SQL engine or validate
 * MySQL's schema/driver. Only the explicitly listed statements are accepted. Other inventories
 * and ancillary tables must remain empty; the account's cash balances must remain zero.
 *
 * Character bindings become pending only at executeUpdate(), visible to subsequent loads only
 * at an explicit commit(), and are discarded by rollback(). The reset row follows ItemFactory's
 * transactional delete/insert path, with the same visibility rule. No ResultSet is populated
 * from the live Character or from uncommitted statement bindings. Logout/account-state writes
 * are explicitly supported autocommit statements, separate from full character updates.
 */
public final class CharacterStatJdbc {
    public record Stats(int hp, int mp, int maxHp, int maxMp, int ap, int hpMpUsed) {
    }

    public record Binding(String setter, Object value) {
    }

    public record Update(Map<Integer, Binding> bindings) {
    }

    // Independent of the production SQL: changing its column/parameter order must fail the test.
    private static final List<String> UPDATE_COLUMNS = List.of(
            "level", "fame", "str", "dex", "luk", "int", "exp", "gachaexp",
            "hp", "mp", "maxhp", "maxmp", "sp", "ap", "gm", "skincolor", "gender",
            "job", "hair", "face", "map", "meso", "hpMpUsed", "spawnpoint", "party",
            "buddyCapacity", "messengerid", "messengerposition", "mountlevel", "mountexp",
            "mounttiredness", "equipslots", "useslots", "setupslots", "etcslots",
            "monsterbookcover", "vanquisherStage", "dojoPoints", "lastDojoStage",
            "finishedDojoTutorial", "vanquisherKills", "matchcardwins", "matchcardlosses",
            "matchcardties", "omokwins", "omoklosses", "omokties", "dataString", "fquest",
            "jailexpire", "partnerId", "marriageItemId", "lastExpGainTime", "ariantPoints",
            "partySearch");
    private static final String CHARACTER_UPDATE = sql("UPDATE characters SET "
            + UPDATE_COLUMNS.stream().map(column -> column + " = ?").collect(Collectors.joining(", "))
            + " WHERE id = ?");
    private static final String CHARACTER_SELECT = sql("SELECT * FROM characters WHERE id = ?");
    private static final String ACCOUNT_SELECT = sql(
            "SELECT name, characterslots, language, highestLevelAchieved FROM accounts WHERE id = ?");
    private static final String CASH_SELECT = sql(
            "SELECT nxCredit, maplePoint, nxPrepaid FROM accounts WHERE id = ?");
    private static final String CASH_UPDATE = sql(
            "UPDATE accounts SET nxCredit = ?, maplePoint = ?, nxPrepaid = ? WHERE id = ?");
    private static final String INVENTORY_SELECT = sql(
            "SELECT * FROM inventoryitems LEFT JOIN inventoryequipment USING(inventoryitemid) WHERE type = ? AND characterid = ?");
    private static final String INVENTORY_DELETE = sql(
            "DELETE inventoryitems, inventoryequipment FROM inventoryitems LEFT JOIN inventoryequipment USING(inventoryitemid) WHERE type = ? AND characterid = ?");
    private static final String INVENTORY_INSERT = sql(
            "INSERT INTO inventoryitems VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    private static final String LOGOUT_UPDATE = sql("UPDATE characters SET lastLogoutTime=? WHERE id=?");
    private static final String LOGIN_STATE_UPDATE = sql(
            "UPDATE accounts SET loggedin = ?, lastlogin = ? WHERE id = ?");

    private static final Set<String> EMPTY_SELECTS = statements(
            "SELECT cardid, level FROM monsterbook WHERE charid = ? ORDER BY cardid ASC",
            "SELECT * FROM inventoryitems LEFT JOIN inventoryequipment USING(inventoryitemid) WHERE type = ? AND accountid = ?",
            "SELECT * FROM newyear WHERE senderid = ? OR receiverid = ?",
            "SELECT petid FROM inventoryitems WHERE characterid = ? AND petid > -1",
            "SELECT mapid,vip FROM trocklocations WHERE characterid = ? LIMIT 15",
            "SELECT area,info FROM area_info WHERE charid = ?",
            "SELECT name,info FROM eventstats WHERE characterid = ?",
            "SELECT sn FROM wishlists WHERE charid = ?",
            "SELECT name, level FROM characters WHERE accountid = ? AND id != ? ORDER BY level DESC limit 1",
            "SELECT * FROM queststatus WHERE characterid = ?",
            "SELECT * FROM questprogress WHERE characterid = ?",
            "SELECT * FROM medalmaps WHERE characterid = ?",
            "SELECT skillid,skilllevel,masterlevel,expiration FROM skills WHERE characterid = ?",
            "SELECT SkillID,StartTime,length FROM cooldowns WHERE charid = ?",
            "SELECT * FROM playerdiseases WHERE charid = ?",
            "SELECT * FROM skillmacros WHERE characterid = ?",
            "SELECT key,type,action FROM keymap WHERE characterid = ?",
            "SELECT locationtype,map,portal FROM savedlocations WHERE characterid = ?",
            "SELECT characterid_to,when FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),when) < 30",
            "SELECT b.buddyid, b.pending, b.group, c.name as buddyname FROM buddies as b, characters as c WHERE c.id = b.buddyid AND b.characterid = ?",
            "SELECT keymap FROM quickslotkeymapped WHERE accountid = ?;");

    private static final Set<String> EMPTY_DELETES = statements(
            "DELETE FROM cooldowns WHERE charid = ?",
            "DELETE FROM playerdiseases WHERE charid = ?",
            "DELETE FROM buddies WHERE pending = 1 AND characterid = ?",
            "DELETE FROM keymap WHERE characterid = ?",
            "DELETE FROM skillmacros WHERE characterid = ?",
            "DELETE inventoryitems, inventoryequipment FROM inventoryitems LEFT JOIN inventoryequipment USING(inventoryitemid) WHERE type = ? AND accountid = ?",
            "DELETE FROM savedlocations WHERE characterid = ?",
            "DELETE FROM trocklocations WHERE characterid = ?",
            "DELETE FROM buddies WHERE characterid = ? AND pending = 0",
            "DELETE FROM area_info WHERE charid = ?",
            "DELETE FROM eventstats WHERE characterid = ?",
            "DELETE FROM medalmaps WHERE characterid = ?",
            "DELETE FROM questprogress WHERE characterid = ?",
            "DELETE FROM queststatus WHERE characterid = ?",
            "DELETE FROM wishlists WHERE charid = ?");

    private static final Set<String> EMPTY_BATCHES = statements(
            "INSERT INTO monsterbook (charid, cardid, level) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE level = ?;",
            "INSERT INTO keymap (characterid, key, type, action) VALUES (?, ?, ?, ?)",
            "INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)",
            "REPLACE INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)",
            "INSERT INTO savedlocations (characterid, locationtype, map, portal) VALUES (?, ?, ?, ?)",
            "INSERT INTO trocklocations(characterid, mapid, vip) VALUES (?, ?, 0)",
            "INSERT INTO trocklocations(characterid, mapid, vip) VALUES (?, ?, 1)",
            "INSERT INTO buddies (characterid, buddyid, pending, group) VALUES (?, ?, 0, ?)",
            "INSERT INTO area_info (id, charid, area, info) VALUES (DEFAULT, ?, ?, ?)",
            "INSERT INTO eventstats (characterid, name, info) VALUES (?, ?, ?)",
            "INSERT INTO questprogress VALUES (DEFAULT, ?, ?, ?, ?)",
            "INSERT INTO medalmaps VALUES (DEFAULT, ?, ?, ?)");

    private static final Set<String> PREPARE_ONLY = statements(
            "INSERT INTO queststatus (queststatusid, characterid, quest, status, time, expires, forfeited, completed) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?, ?)",
            "INSERT INTO wishlists VALUES (DEFAULT, ?, ?)");

    private final int characterId;
    private final List<Update> characterUpdates = new ArrayList<>();
    private final List<Update> inventoryDeletes = new ArrayList<>();
    private final List<Update> resetInserts = new ArrayList<>();
    private final List<Update> logoutUpdates = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private Map<String, Object> committed;
    private Map<String, Object> committedReset;
    private int nextInventoryItemId = 1;
    private int committedLoginState = Client.LOGIN_NOTLOGGEDIN;
    private int connectionsOpened;
    private int openConnections;
    private int executedUpdates;
    private int commitAttempts;
    private int commits;
    private int rollbacks;
    private boolean failCommit;
    private BiConsumer<Stats, Integer> beforeCommit = (ignoredStats, ignoredQuantity) -> { };

    public CharacterStatJdbc(int characterId, int mapId, Stats initial) {
        this(characterId, mapId, initial, 0);
    }

    public CharacterStatJdbc(int characterId, int mapId, Stats initial, int resetQuantity) {
        require(resetQuantity >= 0 && resetQuantity <= Short.MAX_VALUE, "Reset stack must fit Item.quantity");
        this.characterId = characterId;
        Map<String, Object> row = new LinkedHashMap<>();
        UPDATE_COLUMNS.forEach(column -> row.put(column.toLowerCase(Locale.ROOT), 0));
        row.put("id", characterId);
        row.put("name", "Washer");
        row.put("accountid", 1);
        row.put("level", 120);
        row.put("job", 300);
        row.put("str", 12);
        row.put("dex", 5);
        row.put("int", 4);
        row.put("luk", 4);
        row.put("hp", initial.hp());
        row.put("mp", initial.mp());
        row.put("maxhp", initial.maxHp());
        row.put("maxmp", initial.maxMp());
        row.put("ap", initial.ap());
        row.put("hpmpused", initial.hpMpUsed());
        row.put("sp", "0,0,0,0,0,0,0,0,0,0");
        row.put("map", mapId);
        row.put("party", -1);
        row.put("buddycapacity", 20);
        row.put("messengerposition", 4);
        row.put("mountlevel", 1);
        for (String inventory : List.of("equipslots", "useslots", "setupslots", "etcslots")) {
            row.put(inventory, 24);
        }
        row.put("datastring", "");
        row.put("lastexpgaintime", new Timestamp(10_000));
        row.put("lastlogouttime", new Timestamp(0));
        row.put("partysearch", false);
        row.put("partnerid", -1);
        row.put("marriageitemid", -1);
        for (String column : List.of("hasmerchant", "merchantmesos", "world", "rank", "rankmove",
                "jobrank", "jobrankmove", "guildid", "guildrank", "alliancerank", "familyid")) {
            row.put(column, 0);
        }
        committed = Map.copyOf(row);
        if (resetQuantity == 0) {
            committedReset = Map.of();
        } else {
            Map<String, Object> reset = new LinkedHashMap<>();
            reset.put("inventoryitemid", nextInventoryItemId++);
            reset.put("type", 1); // ItemFactory.INVENTORY, not the account cash-shop locker.
            reset.put("characterid", characterId);
            reset.put("accountid", null);
            reset.put("itemid", ItemId.AP_RESET);
            reset.put("inventorytype", (int) InventoryType.CASH.getType());
            reset.put("position", 1);
            reset.put("quantity", resetQuantity);
            reset.put("owner", "");
            reset.put("petid", -1);
            reset.put("flag", 0);
            reset.put("expiration", -1L);
            reset.put("giftfrom", "");
            committedReset = Collections.unmodifiableMap(reset);
        }
    }

    public Connection openConnection() {
        connectionsOpened++;
        openConnections++;
        Transaction transaction = new Transaction();
        return mock(Connection.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("toString")) {
                return "CharacterStatJdbc connection";
            }
            require(!transaction.closed, "Connection already closed");
            return switch (method) {
                case "prepareStatement" -> transaction.prepare(invocation.getArgument(0));
                case "setAutoCommit" -> {
                    require(transaction.pending == null && transaction.pendingReset == null,
                            "Pending rows need an explicit commit or rollback");
                    transaction.autoCommit = invocation.getArgument(0);
                    yield null;
                }
                case "setTransactionIsolation" -> null;
                case "commit" -> {
                    transaction.commit();
                    yield null;
                }
                case "rollback" -> {
                    require(!transaction.autoCommit, "Rollback outside a transaction");
                    rollbacks++;
                    events.add("rollback");
                    transaction.pending = null;
                    transaction.pendingReset = null;
                    yield null;
                }
                case "close" -> {
                    require(transaction.pending == null && transaction.pendingReset == null,
                            "Connection closed with unfinished character/inventory updates");
                    transaction.closed = true;
                    openConnections--;
                    yield null;
                }
                default -> throw new AssertionError("Unexpected Connection method: " + method);
            };
        });
    }

    public Stats committedStats() {
        return stats(committed);
    }

    public List<Update> characterUpdates() {
        return List.copyOf(characterUpdates);
    }

    public Map<String, Object> committedResetRow() {
        return committedReset;
    }

    public int committedResetQuantity() {
        return quantity(committedReset);
    }

    public List<Update> inventoryDeletes() {
        return List.copyOf(inventoryDeletes);
    }

    public List<Update> resetInserts() {
        return List.copyOf(resetInserts);
    }

    public List<Update> logoutUpdates() {
        return List.copyOf(logoutUpdates);
    }

    public long committedLastLogoutTime() {
        return ((Timestamp) committed.get("lastlogouttime")).getTime();
    }

    public int committedLoginState() {
        return committedLoginState;
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public int connectionsOpened() {
        return connectionsOpened;
    }

    public int executedUpdates() {
        return executedUpdates;
    }

    public int commitAttempts() {
        return commitAttempts;
    }

    public int commits() {
        return commits;
    }

    public int rollbacks() {
        return rollbacks;
    }

    /** Exclude the gameplay loader's zero-row cooldown/disease/buddy cleanup statements. */
    public void clearObservations() {
        require(openConnections == 0, "Cannot reset observations with an open connection");
        characterUpdates.clear();
        inventoryDeletes.clear();
        resetInserts.clear();
        logoutUpdates.clear();
        events.clear();
        connectionsOpened = executedUpdates = commitAttempts = commits = rollbacks = 0;
    }

    public void beforeCommit(Consumer<Stats> observer) {
        beforeCommit = (stats, ignoredQuantity) -> observer.accept(stats);
    }

    public void beforeCommit(BiConsumer<Stats, Integer> observer) {
        beforeCommit = observer;
    }

    public void failNextCommit() {
        failCommit = true;
    }

    private final class Transaction {
        private boolean autoCommit = true;
        private boolean closed;
        private Map<String, Object> pending;
        // null means not touched; an empty map means the old item was transactionally deleted.
        private Map<String, Object> pendingReset;

        private PreparedStatement prepare(String query) {
            String statement = sql(query);
            require(statement.equals(CHARACTER_UPDATE) || statement.equals(CHARACTER_SELECT)
                            || statement.equals(ACCOUNT_SELECT) || statement.equals(CASH_SELECT)
                            || statement.equals(INVENTORY_SELECT) || statement.equals(INVENTORY_DELETE)
                            || statement.equals(INVENTORY_INSERT) || statement.equals(LOGOUT_UPDATE)
                            || statement.equals(LOGIN_STATE_UPDATE)
                            || statement.equals(CASH_UPDATE) || EMPTY_SELECTS.contains(statement)
                            || EMPTY_DELETES.contains(statement) || EMPTY_BATCHES.contains(statement)
                            || PREPARE_ONLY.contains(statement),
                    "Unexpected SQL: " + query);
            Map<Integer, Binding> bindings = new LinkedHashMap<>();
            return mock(PreparedStatement.class, invocation -> {
                String method = invocation.getMethod().getName();
                return switch (method) {
                    case "setInt", "setLong", "setString", "setTimestamp", "setBoolean" -> {
                        bindings.put(invocation.getArgument(0), new Binding(method, invocation.getArgument(1)));
                        yield null;
                    }
                    case "executeQuery" -> {
                        requireAllBindings(statement, bindings);
                        if (statement.equals(CHARACTER_SELECT)) {
                            requireInt(bindings, 1, characterId);
                            yield rows(List.of(committed));
                        }
                        if (statement.equals(INVENTORY_SELECT)) {
                            requireInt(bindings, 1, 1);
                            requireInt(bindings, 2, characterId);
                            yield rows(committedReset.isEmpty() ? List.of() : List.of(committedReset));
                        }
                        if (statement.equals(ACCOUNT_SELECT) || statement.equals(CASH_SELECT)) {
                            requireInt(bindings, 1, 1);
                            yield rows(List.of(Map.of("name", "hpmp-test", "characterslots", 3,
                                    "language", 0, "highestlevelachieved", 120,
                                    "nxcredit", 0, "maplepoint", 0, "nxprepaid", 0)));
                        }
                        require(EMPTY_SELECTS.contains(statement), "Not an allowed SELECT: " + query);
                        yield rows(List.of());
                    }
                    case "executeUpdate" -> {
                        requireAllBindings(statement, bindings);
                        executedUpdates++;
                        if (statement.equals(CHARACTER_UPDATE)) {
                            require(!autoCommit, "Character update must be transactional");
                            requireInt(bindings, 56, characterId);
                            characterUpdates.add(snapshot(bindings));
                            events.add("character.update");
                            pending = new LinkedHashMap<>(committed);
                            for (int i = 0; i < UPDATE_COLUMNS.size(); i++) {
                                pending.put(UPDATE_COLUMNS.get(i).toLowerCase(Locale.ROOT), bindings.get(i + 1).value());
                            }
                            yield 1;
                        }
                        if (statement.equals(INVENTORY_DELETE)) {
                            require(!autoCommit && pending != null, "Inventory delete must share the character save transaction");
                            requireInt(bindings, 1, 1);
                            requireInt(bindings, 2, characterId);
                            int deleted = (pendingReset == null ? committedReset : pendingReset).isEmpty() ? 0 : 1;
                            pendingReset = Map.of();
                            inventoryDeletes.add(snapshot(bindings));
                            events.add("inventory.delete");
                            yield deleted;
                        }
                        if (statement.equals(INVENTORY_INSERT)) {
                            require(!autoCommit && pending != null && pendingReset != null && pendingReset.isEmpty(),
                                    "Only one reset stack, inserted after ItemFactory's transactional delete, is supported");
                            pendingReset = resetRow(bindings);
                            resetInserts.add(snapshot(bindings));
                            events.add("inventory.insert");
                            yield 1;
                        }
                        if (statement.equals(LOGOUT_UPDATE)) {
                            require(autoCommit, "logOff's timestamp update is an autocommit statement");
                            requireInt(bindings, 2, characterId);
                            require(bindings.get(1).setter().equals("setTimestamp")
                                            && bindings.get(1).value() instanceof Timestamp,
                                    "lastLogoutTime must use setTimestamp");
                            Map<String, Object> loggedOut = new LinkedHashMap<>(committed);
                            loggedOut.put("lastlogouttime", bindings.get(1).value());
                            committed = Map.copyOf(loggedOut);
                            logoutUpdates.add(snapshot(bindings));
                            events.add("character.logout");
                            yield 1;
                        }
                        if (statement.equals(LOGIN_STATE_UPDATE)) {
                            require(autoCommit, "Client login state uses autocommit");
                            requireInt(bindings, 3, 1);
                            int state = integer(bindings, 1);
                            require(state == Client.LOGIN_LOGGEDIN || state == Client.LOGIN_NOTLOGGEDIN,
                                    "Only gameplay login and final logout are supported");
                            require(bindings.get(2).setter().equals("setTimestamp")
                                            && bindings.get(2).value() instanceof Timestamp,
                                    "lastlogin must use setTimestamp");
                            committedLoginState = state;
                            events.add("account.loginState");
                            yield 1;
                        }
                        if (statement.equals(CASH_UPDATE)) {
                            for (int i = 1; i <= 3; i++) {
                                requireInt(bindings, i, 0);
                            }
                            requireInt(bindings, 4, 1);
                            yield 1;
                        }
                        require(EMPTY_DELETES.contains(statement), "Ancillary tables must stay empty: " + query);
                        yield 0;
                    }
                    case "executeBatch" -> {
                        require(EMPTY_BATCHES.contains(statement), "Not an allowed empty batch: " + query);
                        yield new int[0];
                    }
                    case "addBatch" -> throw new AssertionError("Ancillary tables must stay empty: " + query);
                    case "close" -> null;
                    case "toString" -> query;
                    default -> throw new AssertionError("Unexpected PreparedStatement method: " + method + " for " + query);
                };
            });
        }

        private void commit() throws SQLException {
            require(!autoCommit && pending != null, "Commit requires a pending character update");
            require(pendingReset != null, "Full saves must snapshot inventory in the same transaction");
            commitAttempts++;
            beforeCommit.accept(stats(pending), quantity(pendingReset));
            if (failCommit) {
                failCommit = false;
                throw new SQLException("Injected character-save commit failure");
            }
            committed = Map.copyOf(pending);
            committedReset = pendingReset;
            pending = null;
            pendingReset = null;
            commits++;
            events.add("commit");
        }
    }

    private Map<String, Object> resetRow(Map<Integer, Binding> bindings) {
        requireInt(bindings, 1, 1);
        require(new Binding("setString", String.valueOf(characterId)).equals(bindings.get(2)),
                "Reset inventory must belong to the saved character");
        require(new Binding("setString", null).equals(bindings.get(3)), "Reset is not an account-locker item");
        requireInt(bindings, 4, ItemId.AP_RESET);
        requireInt(bindings, 5, InventoryType.CASH.getType());
        requireInt(bindings, 6, 1);
        int quantity = integer(bindings, 7);
        require(quantity > 0 && quantity <= Short.MAX_VALUE, "Invalid reset quantity: " + quantity);
        require(new Binding("setString", "").equals(bindings.get(8)), "Only unowned resets are supported");
        requireInt(bindings, 9, -1);
        requireInt(bindings, 10, 0);
        require(new Binding("setLong", -1L).equals(bindings.get(11)), "Only permanent resets are supported");
        require(new Binding("setString", "").equals(bindings.get(12)), "Only ungifted resets are supported");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inventoryitemid", nextInventoryItemId++);
        row.put("type", bindings.get(1).value());
        row.put("characterid", Integer.parseInt((String) bindings.get(2).value()));
        row.put("accountid", bindings.get(3).value());
        List<String> columns = List.of("itemid", "inventorytype", "position", "quantity", "owner",
                "petid", "flag", "expiration", "giftfrom");
        for (int i = 0; i < columns.size(); i++) {
            row.put(columns.get(i), bindings.get(i + 4).value());
        }
        return Collections.unmodifiableMap(row);
    }

    private static ResultSet rows(List<Map<String, Object>> data) {
        int[] cursor = {-1};
        return mock(ResultSet.class, invocation -> {
            String method = invocation.getMethod().getName();
            return switch (method) {
                case "next" -> ++cursor[0] < data.size();
                case "close" -> null;
                case "toString" -> "CharacterStatJdbc rows";
                case "wasNull" -> false; // The supported reset's petid is explicitly -1, never SQL NULL.
                case "getInt", "getLong", "getByte", "getString", "getBoolean", "getTimestamp" -> {
                    require(cursor[0] >= 0 && cursor[0] < data.size(), "ResultSet cursor is not on a row");
                    String column = ((String) invocation.getArgument(0)).toLowerCase(Locale.ROOT);
                    Map<String, Object> row = data.get(cursor[0]);
                    require(row.containsKey(column), "Unexpected ResultSet column: " + column);
                    Object value = row.get(column);
                    yield switch (method) {
                        case "getInt" -> ((Number) value).intValue();
                        case "getLong" -> ((Number) value).longValue();
                        case "getByte" -> ((Number) value).byteValue();
                        default -> value;
                    };
                }
                default -> throw new AssertionError("Unexpected ResultSet method: " + method);
            };
        });
    }

    private static Stats stats(Map<String, Object> row) {
        return new Stats((int) row.get("hp"), (int) row.get("mp"), (int) row.get("maxhp"),
                (int) row.get("maxmp"), (int) row.get("ap"), (int) row.get("hpmpused"));
    }

    private static int quantity(Map<String, Object> row) {
        return row.isEmpty() ? 0 : (int) row.get("quantity");
    }

    private static Update snapshot(Map<Integer, Binding> bindings) {
        return new Update(Collections.unmodifiableMap(new LinkedHashMap<>(bindings)));
    }

    private static int integer(Map<Integer, Binding> bindings, int index) {
        Binding binding = bindings.get(index);
        require(binding.setter().equals("setInt") && binding.value() instanceof Integer,
                "Expected setInt for parameter " + index + ": " + bindings);
        return (int) binding.value();
    }

    private static void requireAllBindings(String statement, Map<Integer, Binding> bindings) {
        int count = (int) statement.chars().filter(character -> character == '?').count();
        require(bindings.size() == count, "Wrong parameter count for " + statement + ": " + bindings);
        for (int i = 1; i <= count; i++) {
            require(bindings.containsKey(i), "Missing parameter " + i + " for " + statement);
        }
    }

    private static void requireInt(Map<Integer, Binding> bindings, int index, int expected) {
        require(new Binding("setInt", expected).equals(bindings.get(index)), "Unexpected integer binding " + index + ": " + bindings);
    }

    private static Set<String> statements(String... queries) {
        return Arrays.stream(queries).map(CharacterStatJdbc::sql).collect(Collectors.toUnmodifiableSet());
    }

    private static String sql(String query) {
        return query.replace("`", "").replaceAll("\\s+", " ").replaceAll("\\s*=\\s*", "=")
                .strip().toLowerCase(Locale.ROOT);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
