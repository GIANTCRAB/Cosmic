package net.server.channel.handlers;

import client.Character;
import client.Job;
import client.Stat;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.processor.stat.AssignAPProcessor;
import config.YamlConfig;
import constants.id.ItemId;
import net.opcodes.SendOpcode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import testutil.HpMpSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UseCashItemHandlerTest {
    private HpMpSession session;
    private Character chr;

    @BeforeEach
    void setUp() {
        session = new HpMpSession();
        chr = session.chr;
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            try {
                session.map.runCharacterStatUpdate();
            } finally {
                session.close();
            }
        }
    }

    @Test
    void successfulWashingConsumesExactlyOneReset() {
        preparePools(5_000, 50_000, 2);
        PrimaryStats primary = primaryStats();

        useReset(Stat.MAXMP, Stat.MAXHP);

        assertEquals(5_016, chr.getMaxHp());
        assertEquals(49_988, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(primary, primaryStats());
        assertCashStack(1);
        assertLiveCharacter();
        assertSuccessfulResetPackets(5_000, 5_016, 30_000, 30_000, 1);
    }

    @ParameterizedTest
    @EnumSource(value = Stat.class, names = {"STR", "DEX", "INT", "LUK"})
    void destinationFailureRetainsResetAndSourceStats(Stat destination) {
        preparePools(5_000, 50_000, 2);
        chr.updateStrDexIntLuk(YamlConfig.config.server.MAX_AP);

        assertRejectedReset(Stat.MAXMP, destination);
    }

    @Test
    void invalidDestinationRetainsResetAndSourceStats() {
        preparePools(5_000, 50_000, 2);

        assertRejectedReset(Stat.MAXMP.getValue(), 12345);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedWashingExhaustsTheFiniteRawMpBudgetOnTheSameLiveCharacter(boolean ratioUpdate) {
        preparePools(29_992, 30_465, 5_000);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        Item originalStack = session.resets;
        PrimaryStats primary = primaryStats();

        assertEquals(Job.BOWMAN, chr.getJob());
        assertEquals(120, chr.getLevel());
        assertEquals(1_665, AssignAPProcessor.getMinMp(chr.getJob(), chr.getLevel()));
        assertEquals(29_992, chr.getMaxHp());
        assertEquals(30_465, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
        assertCashStack(5_000);

        for (int successes = 1; successes <= 2_400; successes++) {
            int previousHp = chr.getMaxHp();
            int previousMp = chr.getMaxMp();

            useReset(Stat.MAXMP, Stat.MAXHP);

            String step = "successful reset " + successes + ", ratio=" + ratioUpdate;
            int expectedHp = 29_992 + successes * 16;
            int expectedMp = 30_465 - successes * 12;
            assertEquals(16, chr.getMaxHp() - previousHp, step);
            assertEquals(12, previousMp - chr.getMaxMp(), step);
            assertEquals(expectedHp, chr.getMaxHp(), step);
            assertEquals(expectedMp, chr.getMaxMp(), step);
            assertEquals(30_000, chr.getClientMaxHp(), step);
            assertEquals(Math.min(30_000, expectedMp), chr.getClientMaxMp(), step);
            assertEquals(30_000, chr.getCurrentMaxHp(), step);
            assertEquals(Math.min(30_000, expectedMp), chr.getCurrentMaxMp(), step);
            assertEquals(ratioUpdate ? 30_000 : 29_992, chr.getHp(), step);
            assertEquals(Math.min(30_000, expectedMp), chr.getMp(), step);
            assertEquals(primary, primaryStats(), step);
            assertEquals(1, chr.getHpMpApUsed(), step);
            assertEquals(0, chr.getRemainingAp(), step);
            assertSame(originalStack, session.resets, step);
            assertCashStack(5_000 - successes);
            assertLiveCharacter();
            assertSuccessfulResetPackets(ratioUpdate ? 30_000 : 29_992, 30_000,
                    Math.min(30_000, expectedMp), Math.min(30_000, expectedMp), 5_000 - successes);
        }

        assertEquals(68_392, chr.getMaxHp());
        assertEquals(1_665, chr.getMaxMp());
        assertCashStack(2_600);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertRejectedReset(Stat.MAXMP, Stat.MAXHP);
            assertSame(originalStack, session.resets);
            assertEquals(68_392, chr.getMaxHp());
            assertEquals(1_665, chr.getMaxMp());
            assertCashStack(2_600);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void partialMpChangesOnlyWhenRepeatedWashingCrossesTheEffectiveCap(boolean ratioUpdate) {
        preparePools(29_992, 30_024, 8);
        chr.updateMp(15_000);
        session.map.runCharacterStatUpdate();
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        PrimaryStats primary = primaryStats();
        Item originalStack = session.resets;
        int[] expectedMaximums = {30_000, 30_000, 29_988, 29_976};
        int[] expectedCurrent = ratioUpdate
                ? new int[]{15_000, 15_000, 14_994, 14_988}
                : new int[]{15_000, 15_000, 14_988, 14_976};

        for (int reset = 0; reset < expectedMaximums.length; reset++) {
            int previousHp = chr.getMaxHp();
            int previousMp = chr.getMaxMp();

            useReset(Stat.MAXMP, Stat.MAXHP);

            assertEquals(16, chr.getMaxHp() - previousHp);
            assertEquals(12, previousMp - chr.getMaxMp());
            assertEquals(29_992 + (reset + 1) * 16, chr.getMaxHp());
            assertEquals(30_024 - (reset + 1) * 12, chr.getMaxMp());
            assertEquals(expectedMaximums[reset], chr.getCurrentMaxMp());
            assertEquals(expectedMaximums[reset], chr.getClientMaxMp());
            assertEquals(expectedCurrent[reset], chr.getMp());
            assertEquals(primary, primaryStats());
            assertEquals(1, chr.getHpMpApUsed());
            assertEquals(0, chr.getRemainingAp());
            assertSame(originalStack, session.resets);
            assertCashStack(7 - reset);
            assertLiveCharacter();
            assertSuccessfulResetPackets(ratioUpdate ? 30_000 : 29_992, 30_000,
                    expectedCurrent[reset], expectedMaximums[reset], 7 - reset);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cooldownRejectsImmediateRetriesAndAllowsTheExactTimedRetry(boolean ratioUpdate) {
        preparePools(5_000, 50_000, 3);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        PrimaryStats primary = primaryStats();

        useReset(Stat.MAXMP, Stat.MAXHP);
        assertEquals(5_016, chr.getMaxHp());
        assertEquals(49_988, chr.getMaxMp());
        assertCashStack(2);
        assertSuccessfulResetPackets(ratioUpdate ? 5_016 : 5_000, 5_016, 30_000, 30_000, 2);
        CharacterState afterFirst = state();
        long firstUse = chr.getLastUsedCashItem();

        useResetNow(Stat.MAXMP.getValue(), Stat.MAXHP.getValue());
        assertEquals(afterFirst, state());
        assertEquals(firstUse, chr.getLastUsedCashItem());
        assertCashStack(2);
        assertLiveCharacter();
        assertRejectionPackets();

        session.time.addAndGet(2_999);
        useResetNow(Stat.MAXMP.getValue(), Stat.MAXHP.getValue());
        assertEquals(afterFirst, state());
        assertEquals(firstUse, chr.getLastUsedCashItem());
        assertCashStack(2);
        assertLiveCharacter();
        assertRejectionPackets();

        session.time.incrementAndGet();
        useResetNow(Stat.MAXMP.getValue(), Stat.MAXHP.getValue());
        assertEquals(firstUse + 3_000, chr.getLastUsedCashItem());
        assertEquals(5_032, chr.getMaxHp());
        assertEquals(49_976, chr.getMaxMp());
        assertEquals(primary, primaryStats());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
        assertCashStack(1);
        assertLiveCharacter();
        assertSuccessfulResetPackets(ratioUpdate ? 5_032 : 5_000, 5_032, 30_000, 30_000, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lastResetIsConsumedExactlyOnceAndLaterEligibleAttemptsCannotWash(boolean ratioUpdate) {
        preparePools(5_000, 50_000, 1);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        PrimaryStats primary = primaryStats();
        Item originalStack = session.resets;

        useReset(Stat.MAXMP, Stat.MAXHP);

        assertEquals(5_016, chr.getMaxHp());
        assertEquals(49_988, chr.getMaxMp());
        assertEquals(primary, primaryStats());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
        assertCashStack(0);
        assertSame(originalStack, session.resets);
        assertLiveCharacter();
        assertSuccessfulResetPackets(ratioUpdate ? 5_016 : 5_000, 5_016, 30_000, 30_000, 0);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertRejectedReset(Stat.MAXMP, Stat.MAXHP);
            assertCashStack(0);
            assertSame(originalStack, session.resets);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 12345})
    void invalidSourceRetainsResetAndDestinationStats(int source) {
        preparePools(5_000, 50_000, 2);

        assertRejectedReset(source, Stat.MAXHP.getValue());
    }

    @ParameterizedTest
    @CsvSource({
            "HP, MAXHP", "MP, MAXHP", "AVAILABLEAP, MAXHP",
            "MAXMP, HP", "MAXMP, MP", "MAXMP, AVAILABLEAP",
            "MAXMP, MAXMP", "MAXHP, MAXHP"
    })
    void nonAssignableAndEqualStatsRetainResetAndAllStats(Stat source, Stat destination) {
        preparePools(5_000, 50_000, 2);

        assertRejectedReset(source, destination);
    }

    @ParameterizedTest
    @EnumSource(value = Stat.class, names = {"STR", "DEX", "INT", "LUK"})
    void primaryStatSourceAtItsFloorRetainsResetAndDestinationStats(Stat source) {
        preparePools(5_000, 50_000, 2);
        chr.updateStrDexIntLuk(4);

        assertRejectedReset(source, Stat.MAXHP);
    }

    @Test
    void emptyAssignedPoolCannotSpendRawMpEvenWithSpareAp() {
        preparePools(5_000, 50_000, 2);
        assertTrue(chr.assignHP(0, -1));
        assertEquals(0, chr.getHpMpApUsed());
        assertEquals(1, chr.getRemainingAp());

        assertRejectedReset(Stat.MAXMP, Stat.MAXHP);
    }

    @Test
    void assignedPoolCountOverflowRetainsResetAndPrimarySource() {
        preparePools(5_000, 50_000, 2);
        // Establish the counter boundary through the public stat mutation API.
        chr.gainAp(Integer.MAX_VALUE, true);
        assertTrue(chr.assignHP(0, Integer.MAX_VALUE - 1));
        chr.changeRemainingAp(0, true);
        assertEquals(Integer.MAX_VALUE, chr.getHpMpApUsed());

        assertRejectedReset(Stat.STR, Stat.MAXHP);
    }

    @ParameterizedTest
    @CsvSource({
            "2458, 50000, MAXHP, MAXMP", "2477, 50000, MAXHP, MAXMP",
            "5000, 1665, MAXMP, MAXHP", "5000, 1676, MAXMP, MAXHP"
    })
    void sourcePoolMustCoverTheEntireLossAboveItsJobMinimum(int hp, int mp,
                                                            Stat source, Stat destination) {
        preparePools(hp, mp, 2);

        assertRejectedReset(source, destination);
    }

    @ParameterizedTest
    @CsvSource({
            "2147483640, 50000, MAXMP, MAXHP",
            "5000, 2147483640, MAXHP, MAXMP"
    })
    void destinationMaximumOverflowRetainsResetAndSourcePool(int hp, int mp,
                                                            Stat source, Stat destination) {
        preparePools(hp, mp, 2);

        assertRejectedReset(source, destination);
    }

    @ParameterizedTest
    @CsvSource({
            "2478, 50000, MAXHP, MAXMP, 2458, 50010, 2458, 30000",
            "5000, 1677, MAXMP, MAXHP, 5016, 1665, 5000, 1665"
    })
    void sourcePoolCanBeSpentExactlyDownToItsJobMinimum(int hp, int mp, Stat source, Stat destination,
                                                       int expectedHp, int expectedMp,
                                                       int expectedCurrentHp, int expectedCurrentMp) {
        preparePools(hp, mp, 2);
        PrimaryStats primary = primaryStats();

        useReset(source, destination);

        assertEquals(expectedHp, chr.getMaxHp());
        assertEquals(expectedMp, chr.getMaxMp());
        assertEquals(expectedCurrentHp, chr.getHp());
        assertEquals(expectedCurrentMp, chr.getMp());
        assertEquals(primary, primaryStats());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
        assertCashStack(1);
        assertLiveCharacter();
        assertSuccessfulResetPackets(expectedCurrentHp, Math.min(30_000, expectedHp),
                expectedCurrentMp, Math.min(30_000, expectedMp), 1);

        assertRejectedReset(source, destination);
    }

    @Test
    void emptyResetStackCannotMutateStats() {
        preparePools(5_000, 50_000, 0);

        assertRejectedReset(Stat.MAXMP, Stat.MAXHP);
    }

    private void preparePools(int hp, int mp, int quantity) {
        session.preparePools(hp, mp, quantity);
        session.map.runCharacterStatUpdate();
        session.packets(session.client).clear();
    }

    private void useReset(Stat source, Stat destination) {
        session.packets(session.client).clear();
        session.useReset(source, destination);
        session.map.runCharacterStatUpdate();
    }

    private void useResetNow(int source, int destination) {
        session.packets(session.client).clear();
        session.useResetNow(source, destination);
        session.map.runCharacterStatUpdate();
    }

    private Inventory cashInventory() {
        return chr.getInventory(InventoryType.CASH);
    }

    private void assertCashStack(int quantity) {
        assertEquals(quantity, session.resets.getQuantity());
        assertEquals(quantity, cashInventory().countById(ItemId.AP_RESET));
        if (quantity == 0) {
            assertNull(cashInventory().getItem((short) 1));
            assertNull(cashInventory().findById(ItemId.AP_RESET));
            assertTrue(cashInventory().list().isEmpty());
        } else {
            assertSame(session.resets, cashInventory().getItem((short) 1));
            assertSame(session.resets, cashInventory().findById(ItemId.AP_RESET));
            assertEquals(1, cashInventory().list().size());
        }
    }

    private void assertLiveCharacter() {
        assertSame(chr, session.client.getPlayer());
        assertSame(session.client, chr.getClient());
        assertSame(session.map, chr.getMap());
        assertSame(chr, session.client.getWorldServer().getPlayerStorage().getCharacterById(chr.getId()));
        assertSame(chr, session.client.getWorldServer().getPlayerStorage().getCharacterByName(chr.getName()));
        assertSame(chr, session.client.getChannelServer().getPlayerStorage().getCharacterById(chr.getId()));
        assertSame(chr, session.client.getChannelServer().getPlayerStorage().getCharacterByName(chr.getName()));
    }

    private void assertRejectedReset(Stat source, Stat destination) {
        assertRejectedReset(source.getValue(), destination.getValue());
    }

    private void assertRejectedReset(int source, int destination) {
        session.map.runCharacterStatUpdate();
        CharacterState before = state();
        Item originalItem = cashInventory().getItem((short) 1);
        session.time.addAndGet(4_000);
        assertTrue(session.time.get() - chr.getLastUsedCashItem() >= 3_000);

        useResetNow(source, destination);

        assertEquals(session.time.get(), chr.getLastUsedCashItem());
        assertEquals(before, state());
        assertSame(originalItem, cashInventory().getItem((short) 1));
        assertLiveCharacter();
        assertRejectionPackets();
    }

    private PrimaryStats primaryStats() {
        return new PrimaryStats(chr.getStr(), chr.getDex(), chr.getInt(), chr.getLuk());
    }

    private CharacterState state() {
        return new CharacterState(chr.getMaxHp(), chr.getMaxMp(), chr.getClientMaxHp(), chr.getClientMaxMp(),
                chr.getCurrentMaxHp(), chr.getCurrentMaxMp(), chr.getHp(), chr.getMp(), primaryStats(),
                chr.getHpMpApUsed(), chr.getRemainingAp(), session.resets.getQuantity(),
                cashInventory().countById(ItemId.AP_RESET), cashInventory().list().size());
    }

    private void assertSuccessfulResetPackets(int hp, int maxHp, int mp, int maxMp, int remainingQuantity) {
        List<byte[]> packets = session.packets(session.client);
        assertEquals(2, packets.size());

        // Decode the transport bytes, independently of Character's true maximum getters.
        // The committed stat update must precede the inventory decrement/removal announcement.
        ByteBuffer stats = packetBody(packets.get(0), SendOpcode.STAT_CHANGED);
        assertEquals(1, stats.get());
        assertEquals(0x7c00, stats.getInt()); // HP, MAXHP, MP, MAXMP, AVAILABLEAP, in wire order.
        assertEquals(hp, stats.getShort());
        assertEquals(maxHp, stats.getShort());
        assertEquals(mp, stats.getShort());
        assertEquals(maxMp, stats.getShort());
        assertEquals(0, stats.getShort());
        assertFalse(stats.hasRemaining());

        ByteBuffer inventory = packetBody(packets.get(1), SendOpcode.INVENTORY_OPERATION);
        assertEquals(1, inventory.get());
        assertEquals(1, inventory.get());
        assertEquals(remainingQuantity == 0 ? 3 : 1, inventory.get());
        assertEquals(5, inventory.get()); // CASH inventory.
        assertEquals(1, inventory.getShort());
        if (remainingQuantity > 0) {
            assertEquals(remainingQuantity, inventory.getShort());
        }
        assertFalse(inventory.hasRemaining());
    }

    private void assertRejectionPackets() {
        List<byte[]> packets = session.packets(session.client);
        List<byte[]> statPackets = packets.stream()
                .filter(packet -> opcode(packet) == SendOpcode.STAT_CHANGED.getValue())
                .toList();
        assertEquals(1, statPackets.size());
        ByteBuffer stats = packetBody(statPackets.getFirst(), SendOpcode.STAT_CHANGED);
        assertEquals(1, stats.get());
        assertEquals(0, stats.getInt());
        assertFalse(stats.hasRemaining());
        assertTrue(packets.stream().noneMatch(packet -> opcode(packet) == SendOpcode.INVENTORY_OPERATION.getValue()));
    }

    private static ByteBuffer packetBody(byte[] packet, SendOpcode expectedOpcode) {
        ByteBuffer body = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(expectedOpcode.getValue(), Short.toUnsignedInt(body.getShort()));
        return body;
    }

    private static int opcode(byte[] packet) {
        return Short.toUnsignedInt(ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private record PrimaryStats(int str, int dex, int intelligence, int luk) {
    }

    private record CharacterState(int trueMaxHp, int trueMaxMp, int clientMaxHp, int clientMaxMp,
                                  int effectiveMaxHp, int effectiveMaxMp, int hp, int mp,
                                  PrimaryStats primary, int poolAp, int spareAp, int stackQuantity,
                                  int totalResetQuantity, int cashSlots) {
    }
}
