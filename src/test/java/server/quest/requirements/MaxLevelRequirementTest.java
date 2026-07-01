/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.quest.requirements;

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;
import server.quest.PartyQuestRegistry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the max-level bypass gating logic extracted into
 * {@link MaxLevelRequirement#shouldBypassMaxLevel(int)}. The gating decision is the only new
 * behaviour; the legacy {@code maxLevel >= level} comparison is left in {@code check()} untouched.
 *
 * <p>Tests drive the {@code USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT} flag (saved/restored) and the
 * {@link PartyQuestRegistry} static state (loaded/reset with a mocked {@link DataProvider}), mirroring
 * the {@code HardcoreProcessorTest} config-flag pattern.</p>
 */
class MaxLevelRequirementTest {

    private boolean previousFlag;

    @BeforeEach
    void saveFlag() {
        previousFlag = YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT;
    }

    @AfterEach
    void restoreFlagAndRegistry() {
        YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT = previousFlag;
        PartyQuestRegistry.load(providerWithChildren(List.of()));   // reset to empty
    }

    @Test
    void shouldBypass_flagOff_neverBypasses() {
        YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT = false;
        PartyQuestRegistry.load(providerWithChildren(nodes("1201")));

        assertFalse(MaxLevelRequirement.shouldBypassMaxLevel(10010), "flag off: ordinary quest stays capped");
        assertFalse(MaxLevelRequirement.shouldBypassMaxLevel(1201), "flag off: PQ stays capped");
    }

    @Test
    void shouldBypass_flagOn_bypassesOrdinaryQuestsOnly() {
        YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT = true;
        PartyQuestRegistry.load(providerWithChildren(nodes("1201", "1300")));

        assertTrue(MaxLevelRequirement.shouldBypassMaxLevel(10010), "flag on: ordinary quest bypassed");
        assertTrue(MaxLevelRequirement.shouldBypassMaxLevel(9999), "flag on: any non-PQ id bypassed");
        assertFalse(MaxLevelRequirement.shouldBypassMaxLevel(1201), "flag on: PQ entry quest stays capped");
        assertFalse(MaxLevelRequirement.shouldBypassMaxLevel(1300), "flag on: PQ entry quest stays capped");
    }

    @Test
    void shouldBypass_flagOn_andNoPqDataLoaded_bypassesEverything() {
        // If PQuest.img is absent/empty, nothing is registered as a PQ, so every quest bypasses.
        YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT = true;
        PartyQuestRegistry.load(providerWithChildren(List.of()));

        assertTrue(MaxLevelRequirement.shouldBypassMaxLevel(10010));
        assertTrue(MaxLevelRequirement.shouldBypassMaxLevel(1201));   // not registered -> treated as ordinary
    }

    // --- helpers ---

    private static List<Data> nodes(String... names) {
        List<Data> list = new ArrayList<>();
        for (String name : names) {
            Data d = mock(Data.class);
            when(d.getName()).thenReturn(name);
            list.add(d);
        }
        return list;
    }

    private static DataProvider providerWithChildren(List<Data> children) {
        Data pQuest = mock(Data.class);
        when(pQuest.getChildren()).thenReturn(children);
        DataProvider dp = mock(DataProvider.class);
        when(dp.getData("PQuest.img")).thenReturn(pQuest);
        return dp;
    }
}
