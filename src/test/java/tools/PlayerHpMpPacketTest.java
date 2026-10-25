package tools;

import client.Character;
import client.Client;
import client.Job;
import client.MonsterBook;
import client.Stat;
import client.processor.stat.AssignAPProcessor;
import config.YamlConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import server.ItemInformationProvider;
import testutil.ItemInformationProviderMocks;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerHpMpPacketTest {
    private Client client;
    private Character chr;
    private boolean previousRatioUpdate;

    @BeforeEach
    void setUp() {
        previousRatioUpdate = YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE;
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = false;
        client = mock(Client.class);
        chr = Character.getDefault(client);
        when(client.getPlayer()).thenReturn(chr);
        chr.setName("Washer");
        chr.updateMaxHpMaxMp(70_000, 50_000);
        chr.healHpMp();
    }

    @AfterEach
    void restoreConfig() {
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = previousRatioUpdate;
    }

    private static ByteBuf body(Packet packet) {
        ByteBuf body = Unpooled.wrappedBuffer(packet.getBytes());
        body.skipBytes(2);
        return body;
    }

    private static Map<Stat, Integer> hpMpUpdate(Packet packet) {
        ByteBuf body = body(packet);
        try {
            assertEquals(1, body.readByte());
            int mask = body.readIntLE();
            Map<Stat, Integer> values = new EnumMap<>(Stat.class);
            for (Stat stat : List.of(Stat.HP, Stat.MAXHP, Stat.MP, Stat.MAXMP, Stat.AVAILABLEAP)) {
                if ((mask & stat.getValue()) != 0) {
                    values.put(stat, (int) body.readShortLE());
                }
            }
            assertEquals(0, body.readableBytes());
            return values;
        } finally {
            body.release();
        }
    }

    private static void assertCharHpMp(ByteBuf body) {
        // id, fixed name, appearance, three pet IDs, level/job, and four primary stats.
        body.skipBytes(4 + 13 + 1 + 1 + 4 + 4 + 3 * 8 + 1 + 2 + 4 * 2);
        assertEquals(30_000, body.readShortLE());
        assertEquals(30_000, body.readShortLE());
        assertEquals(30_000, body.readShortLE());
        assertEquals(30_000, body.readShortLE());
    }

    @ParameterizedTest
    @ValueSource(ints = {29_999, 30_000, 30_001, 32_768, 65_536, Integer.MAX_VALUE})
    void maximumUpdatesCapValuesBeforeWritingShorts(int rawMaximum) {
        Packet packet = PacketCreator.updatePlayerStats(new ArrayList<>(List.of(
                new Pair<>(Stat.MAXHP, rawMaximum), new Pair<>(Stat.MAXMP, rawMaximum))), true, chr);

        Map<Stat, Integer> values = hpMpUpdate(packet);
        assertEquals(Math.min(30_000, rawMaximum), values.get(Stat.MAXHP));
        assertEquals(Math.min(30_000, rawMaximum), values.get(Stat.MAXMP));
        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
    }

    @ParameterizedTest
    @CsvSource({"50000,30000", "-10,0", "12345,12345"})
    void currentUpdatesAreBoundedWithoutChangingTrueMaximums(int rawCurrent, int expected) {
        Packet packet = PacketCreator.updatePlayerStats(new ArrayList<>(List.of(
                new Pair<>(Stat.HP, rawCurrent), new Pair<>(Stat.MP, rawCurrent))), true, chr);

        Map<Stat, Integer> values = hpMpUpdate(packet);
        assertEquals(expected, values.get(Stat.HP));
        assertEquals(expected, values.get(Stat.MP));
        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
    }

    @Test
    void characterListSendsCappedPoolsAndRetainsTrueMaximums() {
        when(client.loadCharacters(0)).thenReturn(List.of(chr));
        ItemInformationProvider items = ItemInformationProviderMocks.mockProvider();
        when(items.canWearEquipment(any(), any())).thenReturn(List.of());

        try (var provider = mockStatic(ItemInformationProvider.class)) {
            provider.when(ItemInformationProvider::getInstance).thenReturn(items);
            ByteBuf body = body(PacketCreator.getCharList(client, 0, 0));
            try {
                assertEquals(0, body.readByte());
                assertEquals(1, body.readByte());
                assertCharHpMp(body);
            } finally {
                body.release();
            }
        }

        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
    }

    @Test
    void fullCharacterInfoSendsCappedPools() {
        // The default-character factory omits the DB-loaded monster book.
        Character serialized = spy(chr);
        doReturn(mock(MonsterBook.class)).when(serialized).getMonsterBook();

        ByteBuf body = body(PacketCreator.getCharInfo(serialized));
        try {
            body.skipBytes(4 + 1 + 1 + 2 + 3 * 4 + 8 + 1);
            assertCharHpMp(body);
        } finally {
            body.release();
        }

        assertEquals(70_000, serialized.getMaxHp());
        assertEquals(50_000, serialized.getMaxMp());
    }

    @Test
    void washEmitsOnePacketContainingBothFinalMaximums() {
        chr.setJob(Job.BOWMAN);
        chr.setLevel(120);
        chr.updateMaxHpMaxMp(5_000, 50_000);
        chr.gainAp(1, true);
        assertTrue(chr.assignHP(1, 1));
        chr.updateMaxHp(5_000);
        clearInvocations(client);

        assertTrue(AssignAPProcessor.APResetAction(client, Stat.MAXMP.getValue(), Stat.MAXHP.getValue()));

        ArgumentCaptor<Packet> packet = ArgumentCaptor.forClass(Packet.class);
        verify(client).sendPacket(packet.capture());
        Map<Stat, Integer> values = hpMpUpdate(packet.getValue());
        assertEquals(5_016, values.get(Stat.MAXHP));
        assertEquals(30_000, values.get(Stat.MAXMP));
        assertEquals(0, values.get(Stat.AVAILABLEAP));
        assertEquals(49_988, chr.getMaxMp());
    }
}
