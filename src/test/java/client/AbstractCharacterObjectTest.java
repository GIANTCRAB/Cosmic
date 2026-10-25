package client;

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AbstractCharacterObjectTest {
    private Character chr;
    private boolean previousRatioUpdate;

    @BeforeEach
    void setUp() {
        previousRatioUpdate = YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE;
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = false;
        chr = Character.getDefault(mock(Client.class));
    }

    @AfterEach
    void restoreConfig() {
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = previousRatioUpdate;
    }

    @ParameterizedTest
    @ValueSource(ints = {30_000, 32_767, 32_768, 50_000, 65_536, 100_000})
    void maximumUpdatesPreserveTrueIntegerValues(int maximum) {
        chr.updateMaxHpMaxMp(maximum, maximum);
        chr.healHpMp();

        assertAll(
                () -> assertEquals(maximum, chr.getMaxHp()),
                () -> assertEquals(maximum, chr.getMaxMp()),
                () -> assertEquals(30_000, chr.getClientMaxHp()),
                () -> assertEquals(30_000, chr.getClientMaxMp()),
                () -> assertEquals(30_000, chr.getCurrentMaxHp()),
                () -> assertEquals(30_000, chr.getCurrentMaxMp()),
                () -> assertEquals(30_000, chr.getHp()),
                () -> assertEquals(30_000, chr.getMp())
        );
    }

    @Test
    void hpAssignmentDoesNotTruncateUnrelatedTrueMp() {
        chr.updateMaxHpMaxMp(5_000, 50_000);
        chr.gainAp(1, true);

        assertTrue(chr.assignHP(16, 1));

        assertAll(
                () -> assertEquals(5_016, chr.getMaxHp()),
                () -> assertEquals(50_000, chr.getMaxMp()),
                () -> assertEquals(0, chr.getRemainingAp()),
                () -> assertEquals(1, chr.getHpMpApUsed())
        );
    }

    @Test
    void mpAssignmentDoesNotTruncateUnrelatedTrueHp() {
        chr.updateMaxHpMaxMp(70_000, 50_000);
        chr.gainAp(1, true);

        assertTrue(chr.assignMP(10, 1));

        assertAll(
                () -> assertEquals(70_000, chr.getMaxHp()),
                () -> assertEquals(50_010, chr.getMaxMp()),
                () -> assertEquals(0, chr.getRemainingAp()),
                () -> assertEquals(1, chr.getHpMpApUsed())
        );
    }

    @Test
    void maximumGainsContinueAcrossBothLegacyBoundaries() {
        chr.updateMaxHpMaxMp(29_992, 32_765);
        chr.addMaxMPMaxHP(16, 10, true);

        assertEquals(30_008, chr.getMaxHp());
        assertEquals(32_775, chr.getMaxMp());

        chr.addMaxHP(40_000);
        chr.addMaxMP(40_000);
        chr.updateHpMp(10_000, 20_000);

        assertEquals(70_008, chr.getMaxHp());
        assertEquals(72_775, chr.getMaxMp());
    }

    @Test
    void failedAssignmentDoesNotChangeStatsOrCounters() {
        chr.updateMaxHpMaxMp(50_000, 60_000);

        assertFalse(chr.assignHP(16, 1));
        assertFalse(chr.assignMP(10, 1));

        assertEquals(50_000, chr.getMaxHp());
        assertEquals(60_000, chr.getMaxMp());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(0, chr.getHpMpApUsed());
    }

    @Test
    void assignmentOverflowFailsWithoutSpendingAp() {
        chr.updateMaxHpMaxMp(Integer.MAX_VALUE, Integer.MAX_VALUE);
        chr.gainAp(1, true);

        assertFalse(chr.assignHP(16, 1));
        assertFalse(chr.assignMP(10, 1));

        assertEquals(Integer.MAX_VALUE, chr.getMaxHp());
        assertEquals(Integer.MAX_VALUE, chr.getMaxMp());
        assertEquals(1, chr.getRemainingAp());
        assertEquals(0, chr.getHpMpApUsed());
    }

    @Test
    void removingAssignedPointsReducesTrueMaximumsAboveTheCap() {
        chr.updateMaxHpMaxMp(70_000, 50_000);
        chr.gainAp(2, true);
        assertTrue(chr.assignHP(16, 1));
        assertTrue(chr.assignMP(10, 1));

        assertTrue(chr.assignHP(-20, -1));
        assertTrue(chr.assignMP(-12, -1));

        assertEquals(69_996, chr.getMaxHp());
        assertEquals(49_998, chr.getMaxMp());
        assertEquals(2, chr.getRemainingAp());
        assertEquals(0, chr.getHpMpApUsed());
    }

    @Test
    void growthAtTheIntegerStorageLimitSaturatesRatherThanWrapping() {
        chr.updateMaxHpMaxMp(Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 2);

        chr.addMaxMPMaxHP(10, 10, true);

        assertEquals(Integer.MAX_VALUE, chr.getMaxHp());
        assertEquals(Integer.MAX_VALUE, chr.getMaxMp());
        assertEquals(30_000, chr.getCurrentMaxHp());
        assertEquals(30_000, chr.getCurrentMaxMp());
    }
}
