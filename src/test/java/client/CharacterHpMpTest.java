package client;

import client.inventory.Equip;
import client.inventory.InventoryType;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.StatEffect;
import server.maps.MapleMap;
import tools.Pair;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterHpMpTest {
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

    private void equipBonus(int hp, int mp) {
        Equip equip = mock(Equip.class);
        when(equip.getPosition()).thenReturn((short) -1);
        when(equip.getHp()).thenReturn((short) hp);
        when(equip.getMp()).thenReturn((short) mp);
        chr.getInventory(InventoryType.EQUIPPED).addItemFromDB(equip);
        chr.setMap(mock(MapleMap.class));
        chr.equipChanged();
    }

    private void applyHyperBody(int percent) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.isActive(chr)).thenReturn(true);
        when(effect.getStatups()).thenReturn(List.of(
                new Pair<>(BuffStat.HYPERBODYHP, percent),
                new Pair<>(BuffStat.HYPERBODYMP, percent)));
        chr.registerEffect(effect, 0, Long.MAX_VALUE, true);
    }

    @Test
    void equipmentAndBuffsAffectCombatMaximumsWithoutChangingTrueOrClientBaseStats() {
        chr.updateMaxHpMaxMp(10_000, 15_000);
        equipBonus(2_000, 3_000);
        applyHyperBody(60);
        chr.healHpMp();

        assertEquals(10_000, chr.getMaxHp());
        assertEquals(15_000, chr.getMaxMp());
        assertEquals(10_000, chr.getClientMaxHp());
        assertEquals(15_000, chr.getClientMaxMp());
        assertEquals(19_200, chr.getCurrentMaxHp());
        assertEquals(28_800, chr.getCurrentMaxMp());
        assertEquals(19_200, chr.getHp());
        assertEquals(28_800, chr.getMp());
    }

    @Test
    void bonusesCapCombatStatsWithoutOverwritingTrueMaximums() {
        chr.updateMaxHpMaxMp(70_000, 50_000);
        equipBonus(5_000, 5_000);
        applyHyperBody(60);
        chr.healHpMp();

        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(30_000, chr.getCurrentMaxHp());
        assertEquals(30_000, chr.getCurrentMaxMp());
        assertEquals(30_000, chr.getHp());
        assertEquals(30_000, chr.getMp());
    }

    @Test
    void equipmentAdditionDoesNotOverflowLargeTrueMaximumsBeforeCombatCap() {
        chr.updateMaxHpMaxMp(Integer.MAX_VALUE, Integer.MAX_VALUE);
        equipBonus(5_000, 5_000);
        applyHyperBody(60);
        chr.healHpMp();

        assertEquals(Integer.MAX_VALUE, chr.getMaxHp());
        assertEquals(Integer.MAX_VALUE, chr.getMaxMp());
        assertEquals(30_000, chr.getCurrentMaxHp());
        assertEquals(30_000, chr.getCurrentMaxMp());
        assertEquals(30_000, chr.getHp());
        assertEquals(30_000, chr.getMp());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void currentPoolUpdatesAndHealingPreserveTrueExcessMaximums(boolean ratioUpdate) {
        chr.updateMaxHpMaxMp(70_000, 50_000);
        chr.healHpMp();
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;

        chr.addMPHP(-1_000, -2_000);
        assertEquals(29_000, chr.getHp());
        assertEquals(28_000, chr.getMp());
        chr.updateHpMp(100_000, 100_000);

        assertEquals(30_000, chr.getHp());
        assertEquals(30_000, chr.getMp());
        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void combinedCurrentAndMaximumUpdateUsesTheNewCombatMaximum(boolean ratioUpdate) {
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;
        chr.updateHpMaxHp(10_000, 10_000);
        chr.updateMpMaxMp(20_000, 20_000);

        assertEquals(10_000, chr.getHp());
        assertEquals(20_000, chr.getMp());
        assertEquals(10_000, chr.getCurrentMaxHp());
        assertEquals(20_000, chr.getCurrentMaxMp());
    }

    @Test
    void characterEntryLoadAndCopyRetainTrueMaximumsAsIntegers() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("name")).thenReturn("Washer");
        when(row.getString("sp")).thenReturn("0");
        when(row.getInt("level")).thenReturn(120);
        when(row.getInt("str")).thenReturn(12);
        when(row.getInt("dex")).thenReturn(5);
        when(row.getInt("int")).thenReturn(4);
        when(row.getInt("luk")).thenReturn(4);
        when(row.getInt("hp")).thenReturn(10_000);
        when(row.getInt("mp")).thenReturn(20_000);
        when(row.getInt("maxhp")).thenReturn(70_000);
        when(row.getInt("maxmp")).thenReturn(50_000);

        Character loaded = Character.loadCharacterEntryFromDB(row, List.of());
        Character copied = loaded.generateCharacterEntry();

        assertEquals(70_000, loaded.getMaxHp());
        assertEquals(50_000, loaded.getMaxMp());
        assertEquals(70_000, copied.getMaxHp());
        assertEquals(50_000, copied.getMaxMp());
        assertEquals(30_000, copied.getClientMaxHp());
        assertEquals(30_000, copied.getClientMaxMp());
    }

    @Test
    void resetAnnouncesOnlyTheFinalCommittedStatAndCounterState() {
        chr.updateMaxHpMaxMp(5_000, 50_000);
        chr.hpMpApUsed = 1;
        chr.setListener(new AbstractCharacterListener() {
            @Override
            public void onHpChanged(int oldHp) {
            }

            @Override
            public void onHpmpPoolUpdate() {
                assertEquals(5_016, chr.getMaxHp());
                assertEquals(49_988, chr.getMaxMp());
                assertEquals(0, chr.getRemainingAp());
                assertEquals(1, chr.getHpMpApUsed());
            }

            @Override
            public void onStatUpdate() {
            }

            @Override
            public void onAnnounceStatPoolUpdate() {
                assertEquals(5_016, chr.statUpdates.get(Stat.MAXHP));
                assertEquals(30_000, chr.statUpdates.get(Stat.MAXMP));
            }
        });

        assertTrue(chr.applyApReset(Stat.MAXMP, Stat.MAXHP, 12, 16, 50, 5));
    }
}
