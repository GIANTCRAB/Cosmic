/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.quest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import provider.Data;
import provider.DataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PartyQuestRegistry}. The WZ-extraction logic is covered via the pure
 * {@link PartyQuestRegistry#extractPartyQuestIds(Data)} helper (mocked {@code Data} tree, no
 * filesystem), and {@link PartyQuestRegistry#load(DataProvider)} / {@link PartyQuestRegistry#isPartyQuest(int)}
 * are exercised through a mocked {@link DataProvider}.
 */
class PartyQuestRegistryTest {

    @AfterEach
    void resetRegistry() {
        // Restore the default empty registry so this test cannot leak state into sibling tests.
        PartyQuestRegistry.load(providerWithChildren(List.of()));
    }

    @Test
    void extractPartyQuestIds_collectsNumericChildNames() {
        Data pQuest = mock(Data.class);
        List<Data> children = nodes("1200", "1201", "1302");
        when(pQuest.getChildren()).thenReturn(children);

        assertEquals(Set.of(1200, 1201, 1302), PartyQuestRegistry.extractPartyQuestIds(pQuest));
    }

    @Test
    void extractPartyQuestIds_skipsNonNumericChildren() {
        Data pQuest = mock(Data.class);
        List<Data> children = nodes("1200", "notanumber", "rank", "1301", "");
        when(pQuest.getChildren()).thenReturn(children);

        assertEquals(Set.of(1200, 1301), PartyQuestRegistry.extractPartyQuestIds(pQuest));
    }

    @Test
    void extractPartyQuestIds_emptyWhenNoChildren() {
        Data pQuest = mock(Data.class);
        when(pQuest.getChildren()).thenReturn(List.of());

        assertTrue(PartyQuestRegistry.extractPartyQuestIds(pQuest).isEmpty());
    }

    @Test
    void load_populatesRegistryForIsPartyQuestLookups() {
        PartyQuestRegistry.load(providerWithChildren(nodes("1201", "1300")));

        assertTrue(PartyQuestRegistry.isPartyQuest(1201), "known PQ entry quest");
        assertTrue(PartyQuestRegistry.isPartyQuest(1300), "known PQ entry quest");
        assertFalse(PartyQuestRegistry.isPartyQuest(10010), "ordinary quest must not be flagged as a PQ");
    }

    @Test
    void load_toleratesMissingPQuestImg() {
        DataProvider dp = mock(DataProvider.class);
        when(dp.getData("PQuest.img")).thenReturn(null);

        PartyQuestRegistry.load(dp);

        assertFalse(PartyQuestRegistry.isPartyQuest(1200));
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
