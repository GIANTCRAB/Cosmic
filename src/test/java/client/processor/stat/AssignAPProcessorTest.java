package client.processor.stat;

import client.Character;
import client.Client;
import client.Job;
import client.Stat;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignAPProcessorTest {
    private Character chr;
    private Client client;
    private boolean previousRatioUpdate;
    private boolean previousRandomizeGain;
    private boolean previousEnforceSwap;

    @BeforeEach
    void setUpCharacter() {
        previousRatioUpdate = YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE;
        previousRandomizeGain = YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN;
        previousEnforceSwap = YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP;
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = false;
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = false;
        YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP = false;
        client = mock(Client.class);
        chr = Character.getDefault(client);
        when(client.getPlayer()).thenReturn(chr);
        chr.setJob(Job.BOWMAN);
        chr.setLevel(120);
    }

    @AfterEach
    void restoreConfig() {
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = previousRatioUpdate;
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = previousRandomizeGain;
        YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP = previousEnforceSwap;
    }

    private void preparePools(int hp, int mp) {
        chr.gainAp(1, true);
        assertTrue(chr.assignHP(1, 1));
        chr.updateMaxHpMaxMp(hp, mp);
        chr.healHpMp();
    }

    private boolean reset(Stat from, Stat to) {
        return AssignAPProcessor.APResetAction(client, from.getValue(), to.getValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void washesExcessMpWithoutSpareApOrDrainingCurrentMp(boolean ratioUpdate) {
        preparePools(5_000, 50_000);
        chr.updateHpMp(2_500, 15_000);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertAll(
                () -> assertEquals(49_988, chr.getMaxMp()),
                () -> assertEquals(30_000, chr.getClientMaxMp()),
                () -> assertEquals(30_000, chr.getCurrentMaxMp()),
                () -> assertEquals(15_000, chr.getMp()),
                () -> assertEquals(5_016, chr.getMaxHp()),
                () -> assertEquals(ratioUpdate ? 2_508 : 2_500, chr.getHp()),
                () -> assertEquals(0, chr.getRemainingAp()),
                () -> assertEquals(1, chr.getHpMpApUsed())
        );
    }

    @ParameterizedTest
    @CsvSource({"30000,29988", "30005,29993", "30012,30000", "30013,30001"})
    void washesAtAndAcrossTheMpCap(int initialMp, int expectedMp) {
        preparePools(5_000, initialMp);

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(expectedMp, chr.getMaxMp());
        assertEquals(Math.min(30_000, expectedMp), chr.getClientMaxMp());
        assertEquals(Math.min(30_000, expectedMp), chr.getCurrentMaxMp());
        assertEquals(5_016, chr.getMaxHp());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void crossingTheCapAdjustsCurrentMpUsingOnlyEffectiveMaximums(boolean ratioUpdate) {
        preparePools(5_000, 30_005);
        chr.updateMp(15_000);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(29_993, chr.getCurrentMaxMp());
        assertEquals(ratioUpdate ? 14_997 : 14_993, chr.getMp());
    }

    @Test
    void washingRetainsTheFullHpGainAboveTheCap() {
        preparePools(29_992, 50_000);

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(30_008, chr.getMaxHp());
        assertEquals(30_000, chr.getClientMaxHp());
        assertEquals(30_000, chr.getCurrentMaxHp());
        assertEquals(49_988, chr.getMaxMp());
    }

    @ParameterizedTest
    @CsvSource({"WARRIOR,4,20", "MAGICIAN,31,6", "BOWMAN,12,16", "THIEF,12,16", "PIRATE,16,18", "BEGINNER,8,8"})
    void washingUsesJobSpecificLossesAndGains(Job job, int mpLoss, int hpGain) {
        chr.setJob(job);
        preparePools(10_000, 50_000);

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(50_000 - mpLoss, chr.getMaxMp());
        assertEquals(10_000 + hpGain, chr.getMaxHp());
    }

    @Test
    void randomizedGainSettingRetainsFixedBowmanWashingGains() {
        preparePools(5_000, 50_000);
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = true;

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(5_016, chr.getMaxHp());
        assertEquals(49_988, chr.getMaxMp());
    }

    @Test
    void freshApAssignmentCanIncreaseBothTrueMaximumsAboveTheCap() {
        chr.updateMaxHpMaxMp(50_000, 70_000);
        chr.gainAp(2, true);

        AssignAPProcessor.APAssignAction(client, Stat.MAXHP.getValue());
        AssignAPProcessor.APAssignAction(client, Stat.MAXMP.getValue());

        assertEquals(50_016, chr.getMaxHp());
        assertEquals(70_010, chr.getMaxMp());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(2, chr.getHpMpApUsed());
    }

    @Test
    void reverseWashingSpendsTrueHpWithoutDrainingCappedCurrentHp() {
        preparePools(70_000, 50_000);

        assertTrue(reset(Stat.MAXHP, Stat.MAXMP));

        assertEquals(69_980, chr.getMaxHp());
        assertEquals(50_010, chr.getMaxMp());
        assertEquals(30_000, chr.getHp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @Test
    void primaryStatToPoolResetIncreasesPoolCounterWithoutSpendingAvailableAp() {
        chr.updateMaxHpMaxMp(50_000, 50_000);
        chr.gainAp(2, true);

        assertTrue(reset(Stat.STR, Stat.MAXHP));

        assertEquals(11, chr.getStr());
        assertEquals(50_016, chr.getMaxHp());
        assertEquals(2, chr.getRemainingAp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @Test
    void poolToPrimaryStatResetDecreasesPoolCounter() {
        preparePools(70_000, 50_000);

        assertTrue(reset(Stat.MAXMP, Stat.STR));

        assertEquals(13, chr.getStr());
        assertEquals(49_988, chr.getMaxMp());
        assertEquals(70_000, chr.getMaxHp());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(0, chr.getHpMpApUsed());
    }

    @Test
    void primaryStatResetPreservesExcessPoolsAndPoolCounter() {
        preparePools(70_000, 50_000);

        assertTrue(reset(Stat.STR, Stat.DEX));

        assertEquals(11, chr.getStr());
        assertEquals(6, chr.getDex());
        assertEquals(70_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void destinationPrimaryStatLimitDoesNotPartiallyRemoveMp() {
        preparePools(5_000, 50_000);
        chr.updateStrDexIntLuk(YamlConfig.config.server.MAX_AP);

        assertFalse(reset(Stat.MAXMP, Stat.STR));

        assertEquals(50_000, chr.getMaxMp());
        assertEquals(5_000, chr.getMaxHp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void destinationMaximumOverflowDoesNotPartiallyRemoveMp() {
        preparePools(Integer.MAX_VALUE, 50_000);

        assertFalse(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(Integer.MAX_VALUE, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void trueExcessMpStillRequiresAnAssignedHpMpPoint() {
        chr.updateMaxHpMaxMp(5_000, 50_000);
        chr.gainAp(1, true);

        assertFalse(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(5_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(1, chr.getRemainingAp());
        assertEquals(0, chr.getHpMpApUsed());
    }

    @Test
    void exactJobMinimumIsAllowedButFurtherWashingFails() {
        int minimum = AssignAPProcessor.getMinMp(chr.getJob(), chr.getLevel());
        preparePools(5_000, minimum + 12);

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));
        assertFalse(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(minimum, chr.getMaxMp());
        assertEquals(5_016, chr.getMaxHp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void enforcedPoolSwapRejectsPrimaryDestinationWithoutMutation() {
        preparePools(5_000, 50_000);
        YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP = true;

        assertFalse(reset(Stat.MAXMP, Stat.STR));
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(12, chr.getStr());
        assertEquals(1, chr.getHpMpApUsed());

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 12345, 1024, 4096, 32768})
    void invalidDestinationDoesNotPartiallyRemoveSource(int destination) {
        preparePools(5_000, 50_000);

        assertFalse(AssignAPProcessor.APResetAction(client, Stat.MAXMP.getValue(), destination));

        assertEquals(50_000, chr.getMaxMp());
        assertEquals(5_000, chr.getMaxHp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void ordinaryBelowCapWashingRetainsExpectedStatsAndAccounting() {
        preparePools(5_000, 3_000);
        chr.updateHpMp(2_500, 1_500);

        assertTrue(reset(Stat.MAXMP, Stat.MAXHP));

        assertEquals(5_016, chr.getMaxHp());
        assertEquals(2_988, chr.getMaxMp());
        assertEquals(1_488, chr.getMp());
        assertEquals(2_500, chr.getHp());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @Test
    void randomizedFreshMpAssignmentIncludesIntWhilePreservingExcessMaximum() {
        chr.updateMaxHpMaxMp(5_000, 50_000);
        chr.updateStrDexIntLuk(100);
        chr.gainAp(1, true);
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = true;

        AssignAPProcessor.APAssignAction(client, Stat.MAXMP.getValue());

        assertTrue(chr.getMaxMp() >= 50_016 && chr.getMaxMp() <= 50_018);
        assertEquals(30_000, chr.getCurrentMaxMp());
        assertEquals(0, chr.getRemainingAp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reducingHpMaximumDoesNotKillTheCharacter(boolean ratioUpdate) {
        preparePools(30_005, 50_000);
        chr.updateHp(1);
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratioUpdate;

        assertTrue(reset(Stat.MAXHP, Stat.MAXMP));

        assertEquals(29_985, chr.getMaxHp());
        assertEquals(1, chr.getHp());
    }

    @Test
    void exactHpJobMinimumIsAllowedButFurtherReductionFails() {
        int minimum = AssignAPProcessor.getMinHp(chr.getJob(), chr.getLevel());
        preparePools(minimum + 20, 50_000);

        assertTrue(reset(Stat.MAXHP, Stat.MAXMP));
        assertFalse(reset(Stat.MAXHP, Stat.MAXMP));

        assertEquals(minimum, chr.getMaxHp());
        assertEquals(50_010, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 12345, 1024, 4096, 32768})
    void invalidSourceCannotGrantTheDestinationStat(int source) {
        preparePools(5_000, 50_000);

        assertFalse(AssignAPProcessor.APResetAction(client, source, Stat.MAXHP.getValue()));

        assertEquals(5_000, chr.getMaxHp());
        assertEquals(50_000, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void insufficientPrimaryStatLeavesDestinationAndAccountingIntact() {
        preparePools(5_000, 50_000);
        assertTrue(chr.assignStr(-8));

        assertFalse(reset(Stat.STR, Stat.MAXHP));

        assertEquals(4, chr.getStr());
        assertEquals(5_000, chr.getMaxHp());
        assertEquals(8, chr.getRemainingAp());
        assertEquals(1, chr.getHpMpApUsed());
    }

    @Test
    void sameStatResetFailsWithoutSpendingTheSource() {
        preparePools(5_000, 50_000);

        assertFalse(reset(Stat.MAXMP, Stat.MAXMP));

        assertEquals(50_000, chr.getMaxMp());
        assertEquals(1, chr.getHpMpApUsed());
        assertEquals(0, chr.getRemainingAp());
    }

    @Test
    void getMinHp() {
        int max_level = 200;
        int cygnus_max_level = 120;

        BiFunction<Job,Integer,Integer> f = AssignAPProcessor::getMinHp;

        assertAll(
                // Beginners
                () -> assertEquals(2438, f.apply(Job.BEGINNER, max_level)),
                () -> assertEquals(1478, f.apply(Job.NOBLESSE, cygnus_max_level)),

                // Warrior (Explorer)
                () -> assertEquals(4918, f.apply(Job.WARRIOR, max_level)),

                () -> assertEquals(5218, f.apply(Job.FIGHTER, max_level)),
                () -> assertEquals(5218, f.apply(Job.CRUSADER, max_level)),
                () -> assertEquals(5218, f.apply(Job.HERO, max_level)),

                () -> assertEquals(4918, f.apply(Job.PAGE, max_level)),
                () -> assertEquals(4918, f.apply(Job.WHITEKNIGHT, max_level)),
                () -> assertEquals(4918, f.apply(Job.PALADIN, max_level)),

                () -> assertEquals(4918, f.apply(Job.SPEARMAN, max_level)),
                () -> assertEquals(4918, f.apply(Job.DRAGONKNIGHT, max_level)),
                () -> assertEquals(4918, f.apply(Job.DARKKNIGHT, max_level)),

                // Warrior (Cygnus)
                () -> assertEquals(2998, f.apply(Job.DAWNWARRIOR1, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR2, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR3, cygnus_max_level)),
                () -> assertEquals(3298, f.apply(Job.DAWNWARRIOR4, cygnus_max_level)),

                // Warrior (Aran)
                () -> assertEquals(4918, f.apply(Job.ARAN1, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN2, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN3, max_level)),
                () -> assertEquals(5218, f.apply(Job.ARAN4, max_level)),

                // Magician (Explorer)
                () -> assertEquals(2054, f.apply(Job.MAGICIAN, max_level)),

                () -> assertEquals(2054, f.apply(Job.FP_WIZARD, max_level)),
                () -> assertEquals(2054, f.apply(Job.FP_MAGE, max_level)),
                () -> assertEquals(2054, f.apply(Job.FP_ARCHMAGE, max_level)),

                () -> assertEquals(2054, f.apply(Job.IL_WIZARD, max_level)),
                () -> assertEquals(2054, f.apply(Job.IL_MAGE, max_level)),
                () -> assertEquals(2054, f.apply(Job.IL_ARCHMAGE, max_level)),

                () -> assertEquals(2054, f.apply(Job.CLERIC, max_level)),
                () -> assertEquals(2054, f.apply(Job.PRIEST, max_level)),
                () -> assertEquals(2054, f.apply(Job.BISHOP, max_level)),

                // Magician (Cygnus)
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD1, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD2, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD3, cygnus_max_level)),
                () -> assertEquals(1254, f.apply(Job.BLAZEWIZARD4, cygnus_max_level)),

                // Bowman (Explorer)
                () -> assertEquals(4058, f.apply(Job.BOWMAN, max_level)),

                () -> assertEquals(4358, f.apply(Job.HUNTER, max_level)),
                () -> assertEquals(4358, f.apply(Job.RANGER, max_level)),
                () -> assertEquals(4358, f.apply(Job.BOWMASTER, max_level)),

                () -> assertEquals(4358, f.apply(Job.CROSSBOWMAN, max_level)),
                () -> assertEquals(4358, f.apply(Job.SNIPER, max_level)),
                () -> assertEquals(4358, f.apply(Job.MARKSMAN, max_level)),

                // Bowman (Cygnus)
                () -> assertEquals(2458, f.apply(Job.WINDARCHER1, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER2, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER3, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.WINDARCHER4, cygnus_max_level)),

                // Thief (Explorer)
                () -> assertEquals(4058, f.apply(Job.THIEF, max_level)),

                () -> assertEquals(4358, f.apply(Job.ASSASSIN, max_level)),
                () -> assertEquals(4358, f.apply(Job.HERMIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.NIGHTLORD, max_level)),

                () -> assertEquals(4358, f.apply(Job.BANDIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.CHIEFBANDIT, max_level)),
                () -> assertEquals(4358, f.apply(Job.SHADOWER, max_level)),

                // Thief (Cygnus)
                () -> assertEquals(2458, f.apply(Job.NIGHTWALKER1, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER2, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER3, cygnus_max_level)),
                () -> assertEquals(2758, f.apply(Job.NIGHTWALKER4, cygnus_max_level)),

                // Pirate (Explorer)
                () -> assertEquals(4438, f.apply(Job.PIRATE, max_level)),

                () -> assertEquals(4738, f.apply(Job.BRAWLER, max_level)),
                () -> assertEquals(4738, f.apply(Job.MARAUDER, max_level)),
                () -> assertEquals(4738, f.apply(Job.BUCCANEER, max_level)),

                () -> assertEquals(4738, f.apply(Job.GUNSLINGER, max_level)),
                () -> assertEquals(4738, f.apply(Job.OUTLAW, max_level)),
                () -> assertEquals(4738, f.apply(Job.CORSAIR, max_level)),

                // Pirate (Cygnus)
                () -> assertEquals(2678, f.apply(Job.THUNDERBREAKER1, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER2, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER3, cygnus_max_level)),
                () -> assertEquals(2978, f.apply(Job.THUNDERBREAKER4, cygnus_max_level))
        );
    }

    @Test
    void getMinMp() {
        int max_level = 200;
        int cygnus_max_level = 120;

        BiFunction<Job,Integer,Integer> f = AssignAPProcessor::getMinMp;

        assertAll(
                // Beginners
                () -> assertEquals(1995, f.apply(Job.BEGINNER, max_level)),
                () -> assertEquals(1195, f.apply(Job.NOBLESSE, cygnus_max_level)),

                // Warrior (Explorer)
                () -> assertEquals(855, f.apply(Job.WARRIOR, max_level)),

                () -> assertEquals(855, f.apply(Job.FIGHTER, max_level)),
                () -> assertEquals(855, f.apply(Job.CRUSADER, max_level)),
                () -> assertEquals(855, f.apply(Job.HERO, max_level)),

                () -> assertEquals(955, f.apply(Job.PAGE, max_level)),
                () -> assertEquals(955, f.apply(Job.WHITEKNIGHT, max_level)),
                () -> assertEquals(955, f.apply(Job.PALADIN, max_level)),

                () -> assertEquals(955, f.apply(Job.SPEARMAN, max_level)),
                () -> assertEquals(955, f.apply(Job.DRAGONKNIGHT, max_level)),
                () -> assertEquals(955, f.apply(Job.DARKKNIGHT, max_level)),

                // Warrior (Cygnus)
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR1, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR2, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR3, cygnus_max_level)),
                () -> assertEquals(535, f.apply(Job.DAWNWARRIOR4, cygnus_max_level)),

                // Warrior (Aran)
                () -> assertEquals(855, f.apply(Job.ARAN1, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN2, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN3, max_level)),
                () -> assertEquals(855, f.apply(Job.ARAN4, max_level)),

                // Magician (Explorer)
                () -> assertEquals(4399, f.apply(Job.MAGICIAN, max_level)),

                () -> assertEquals(4849, f.apply(Job.FP_WIZARD, max_level)),
                () -> assertEquals(4849, f.apply(Job.FP_MAGE, max_level)),
                () -> assertEquals(4849, f.apply(Job.FP_ARCHMAGE, max_level)),

                () -> assertEquals(4849, f.apply(Job.IL_WIZARD, max_level)),
                () -> assertEquals(4849, f.apply(Job.IL_MAGE, max_level)),
                () -> assertEquals(4849, f.apply(Job.IL_ARCHMAGE, max_level)),

                () -> assertEquals(4849, f.apply(Job.CLERIC, max_level)),
                () -> assertEquals(4849, f.apply(Job.PRIEST, max_level)),
                () -> assertEquals(4849, f.apply(Job.BISHOP, max_level)),

                // Magician (Cygnus)
                () -> assertEquals(2639, f.apply(Job.BLAZEWIZARD1, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD2, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD3, cygnus_max_level)),
                () -> assertEquals(3089, f.apply(Job.BLAZEWIZARD4, cygnus_max_level)),

                // Bowman (Explorer)
                () -> assertEquals(2785, f.apply(Job.BOWMAN, max_level)),

                () -> assertEquals(2935, f.apply(Job.HUNTER, max_level)),
                () -> assertEquals(2935, f.apply(Job.RANGER, max_level)),
                () -> assertEquals(2935, f.apply(Job.BOWMASTER, max_level)),

                () -> assertEquals(2935, f.apply(Job.CROSSBOWMAN, max_level)),
                () -> assertEquals(2935, f.apply(Job.SNIPER, max_level)),
                () -> assertEquals(2935, f.apply(Job.MARKSMAN, max_level)),

                // Bowman (Cygnus)
                () -> assertEquals(1665, f.apply(Job.WINDARCHER1, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER2, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER3, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.WINDARCHER4, cygnus_max_level)),

                // Thief (Explorer)
                () -> assertEquals(2785, f.apply(Job.THIEF, max_level)),

                () -> assertEquals(2935, f.apply(Job.ASSASSIN, max_level)),
                () -> assertEquals(2935, f.apply(Job.HERMIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.NIGHTLORD, max_level)),

                () -> assertEquals(2935, f.apply(Job.BANDIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.CHIEFBANDIT, max_level)),
                () -> assertEquals(2935, f.apply(Job.SHADOWER, max_level)),

                // Thief (Cygnus)
                () -> assertEquals(1665, f.apply(Job.NIGHTWALKER1, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER2, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER3, cygnus_max_level)),
                () -> assertEquals(1815, f.apply(Job.NIGHTWALKER4, cygnus_max_level)),

                // Pirate (Explorer)
                () -> assertEquals(3545, f.apply(Job.PIRATE, max_level)),

                () -> assertEquals(3695, f.apply(Job.BRAWLER, max_level)),
                () -> assertEquals(3695, f.apply(Job.MARAUDER, max_level)),
                () -> assertEquals(3695, f.apply(Job.BUCCANEER, max_level)),

                () -> assertEquals(3695, f.apply(Job.GUNSLINGER, max_level)),
                () -> assertEquals(3695, f.apply(Job.OUTLAW, max_level)),
                () -> assertEquals(3695, f.apply(Job.CORSAIR, max_level)),

                // Pirate (Cygnus)
                () -> assertEquals(2105, f.apply(Job.THUNDERBREAKER1, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER2, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER3, cygnus_max_level)),
                () -> assertEquals(2255, f.apply(Job.THUNDERBREAKER4, cygnus_max_level))
        );
    }
}
