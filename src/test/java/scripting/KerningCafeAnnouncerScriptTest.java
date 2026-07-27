/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package scripting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import scripting.npc.NPCConversationManager;
import server.reward.VendingMachineRewards;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dialog test for the Cafe PQ reward announcer (NPC 1052015, {@code npc/1052015.js}).
 * Beyond the flow, this is the direct verification that GraalJS can consume the
 * Java record/list model exposed by {@code cm.vendingTierCount},
 * {@code cm.vendingTierLabel} and {@code cm.vendingRewardEntries} -- i.e. that
 * record accessors ({@code itemId()}, {@code quantity()}) and {@code List}
 * access ({@code size()}, {@code get(i)}) resolve correctly across the
 * GraalJS&lt;-&gt;Java boundary.
 */
class KerningCafeAnnouncerScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    private static final String SCRIPT_PATH = "npc/1052015.js";

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @Test
    void listsTierRewardsByReadingEntriesFromJava() throws Exception {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine(SCRIPT_PATH);
        assertNotNull(engine, "Script " + SCRIPT_PATH + " failed to load/evaluate");
        NPCConversationManager cm = mock(NPCConversationManager.class);
        when(cm.vendingTierCount()).thenReturn(6);
        when(cm.vendingTierLabel(anyInt())).thenReturn("Tier X");
        List<VendingMachineRewards.RewardEntry> entries = List.of(
                new VendingMachineRewards.RewardEntry(1302021, 1, 1),
                new VendingMachineRewards.RewardEntry(2022053, 20, 8));
        when(cm.vendingRewardEntries(0)).thenReturn(entries);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        ArgumentCaptor<String> menu = ArgumentCaptor.forClass(String.class);
        iv.invokeFunction("start");
        verify(cm).sendSimple(menu.capture());
        assertTrue(menu.getValue().contains("Tier X"));
        verify(cm, times(6)).vendingTierLabel(anyInt());

        ArgumentCaptor<String> listing = ArgumentCaptor.forClass(String.class);
        iv.invokeFunction("action", (byte) 1, (byte) 0, 0);   // pick tier 0
        verify(cm).sendPrev(listing.capture());
        String text = listing.getValue();
        assertTrue(text.contains("#i1302021#"), "equip entry.itemId() must render");
        assertTrue(text.contains("#i2022053#"), "potion entry.itemId() must render");
        assertTrue(text.contains("(20)"), "entry.quantity() must render for qty>1");
        verify(cm).vendingRewardEntries(0);
    }
}
