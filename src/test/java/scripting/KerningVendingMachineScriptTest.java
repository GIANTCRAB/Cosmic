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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dialog-flow tests for the Kerning City Internet Cafe vending machine
 * (NPC 1052014, {@code npc/1052014.js}). The script is loaded through the real
 * GraalJS engine and driven with a mocked {@link NPCConversationManager} ("cm").
 *
 * <p>The reward math, weighted RNG, and reward tables now live in Java
 * ({@code server.reward.VendingMachineRewards}, covered by its own unit tests)
 * and are surfaced to the script as {@code cm.vendingTier} / {@code cm.vendingRoll}.
 * These tests therefore stub those helpers and assert only the script's own
 * responsibilities:
 * <ul>
 *   <li>Inserting the same eraser type more than once <b>accumulates</b> the staged
 *       total (the original bug overwrote it, so fewer erasers were consumed than
 *       staged).</li>
 *   <li>Dispensing hands the full staged total to the atomic
 *       {@code cm.exchangeItems} call together with the rolled reward.</li>
 *   <li>The "available" count decreases as items are staged, rejecting over-inserts.</li>
 *   <li>Retrieving with nothing staged prompts to insert and consumes nothing.</li>
 * </ul>
 */
class KerningVendingMachineScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    private static final String SCRIPT_PATH = "npc/1052014.js";
    private static final int STUMP_ERASER = 4001009;     // 4001009 + 0
    private static final int COIN_ID = 4001158;
    private static final int RETRIEVE_INDEX_NO_COIN = 6; // tickets.length when hasCoin == false

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private ScriptEngine load() {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine(SCRIPT_PATH);
        assertNotNull(engine, "Script " + SCRIPT_PATH + " failed to load/evaluate");
        return engine;
    }

    private NPCConversationManager mockCm() {
        return mock(NPCConversationManager.class);
    }

    private void next(Invocable iv) throws Exception {
        iv.invokeFunction("action", (byte) 1, (byte) 0, 0);
    }

    private void back(Invocable iv) throws Exception {
        iv.invokeFunction("action", (byte) 0, (byte) 0, -1);
    }

    private void select(Invocable iv, int selection) throws Exception {
        iv.invokeFunction("action", (byte) 1, (byte) 0, selection);
    }

    private void enterText(Invocable iv) throws Exception {
        iv.invokeFunction("action", (byte) 1, (byte) 0, -1);
    }

    @Test
    void accumulateAcrossMultipleInserts_thenHandsAllStagedErasersToExchange() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mockCm();
        when(cm.haveItem(COIN_ID)).thenReturn(false);
        when(cm.getItemQuantity(STUMP_ERASER)).thenReturn(20);   // total inventory held constant
        when(cm.getText()).thenReturn("10", "10");               // two batches of 10
        when(cm.vendingTier(any(int[].class), anyInt())).thenReturn(2);
        when(cm.vendingRoll(2)).thenReturn(new VendingMachineRewards.RewardEntry(1302058, 1, 1));
        when(cm.exchangeItems(any(int[].class), any(int[].class), any(int[].class), any(int[].class))).thenReturn(true);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");   // status 0
        next(iv);                     // -> menu (status 1)

        select(iv, 0);                // pick stump eraser
        enterText(iv);                // insert 10
        back(iv);                     // -> menu (tier recomputed)

        select(iv, 0);                // pick stump eraser again
        enterText(iv);                // insert 10 more -> total staged must be 20, not 10
        back(iv);                     // -> menu

        select(iv, RETRIEVE_INDEX_NO_COIN);   // retrieve

        ArgumentCaptor<int[]> rmIds = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> rmQtys = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> addIds = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> addQtys = ArgumentCaptor.forClass(int[].class);
        verify(cm).exchangeItems(rmIds.capture(), rmQtys.capture(), addIds.capture(), addQtys.capture());
        // ALL staged erasers (both batches of 10) handed off in one atomic exchange.
        assertArrayEquals(new int[]{STUMP_ERASER}, rmIds.getValue());
        assertArrayEquals(new int[]{20}, rmQtys.getValue());
        // The rolled reward from cm.vendingRoll is forwarded as the single add entry.
        assertArrayEquals(new int[]{1302058}, addIds.getValue());
        assertArrayEquals(new int[]{1}, addQtys.getValue());
        verify(cm).dispose();
    }

    @Test
    void insertRespectsAvailableAfterPartialStaging() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mockCm();
        when(cm.haveItem(COIN_ID)).thenReturn(false);
        when(cm.getItemQuantity(STUMP_ERASER)).thenReturn(20);
        when(cm.getText()).thenReturn("10", "15");   // first ok, second exceeds remaining (10)
        when(cm.vendingTier(any(int[].class), anyInt())).thenReturn(1);
        when(cm.vendingRoll(1)).thenReturn(new VendingMachineRewards.RewardEntry(1022073, 1, 1));
        when(cm.exchangeItems(any(int[].class), any(int[].class), any(int[].class), any(int[].class))).thenReturn(true);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        next(iv);

        select(iv, 0);     // available 20
        enterText(iv);     // insert 10 -> staged 10
        back(iv);

        select(iv, 0);     // available must now read 20 - 10 = 10
        enterText(iv);     // try to insert 15 -> rejected, staging unchanged
        verify(cm).sendPrev("You cannot insert the given amount of erasers (#r10#k available). Click '#rBack#k' to return to the main interface.");
        back(iv);

        select(iv, RETRIEVE_INDEX_NO_COIN);

        // Staging stayed at 10 (not 25): only 10 erasers handed to the exchange.
        ArgumentCaptor<int[]> rmIds = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> rmQtys = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> addIds = ArgumentCaptor.forClass(int[].class);
        ArgumentCaptor<int[]> addQtys = ArgumentCaptor.forClass(int[].class);
        verify(cm).exchangeItems(rmIds.capture(), rmQtys.capture(), addIds.capture(), addQtys.capture());
        assertArrayEquals(new int[]{STUMP_ERASER}, rmIds.getValue());
        assertArrayEquals(new int[]{10}, rmQtys.getValue());
        assertArrayEquals(new int[]{1022073}, addIds.getValue());
        assertArrayEquals(new int[]{1}, addQtys.getValue());
    }

    @Test
    void retrieveWithNothingStaged_promptsToInsertAndDoesNotConsume() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mockCm();
        when(cm.haveItem(COIN_ID)).thenReturn(false);
        when(cm.vendingTier(any(int[].class), anyInt())).thenReturn(-1);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        next(iv);

        select(iv, RETRIEVE_INDEX_NO_COIN);   // retrieve with zero staged

        verify(cm).sendPrev("You have set no erasers. Insert at least one to claim a prize.");
        verify(cm, never()).vendingRoll(anyInt());
        verify(cm, never()).exchangeItems(any(int[].class), any(int[].class), any(int[].class), any(int[].class));
    }

    @Test
    void dispenseAbortsWhenExchangeFails_withoutResettingStaging() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mockCm();
        when(cm.haveItem(COIN_ID)).thenReturn(false);
        when(cm.getItemQuantity(STUMP_ERASER)).thenReturn(20);
        when(cm.getText()).thenReturn("20");
        when(cm.vendingTier(any(int[].class), anyInt())).thenReturn(2);
        when(cm.vendingRoll(2)).thenReturn(new VendingMachineRewards.RewardEntry(1302058, 1, 1));
        when(cm.exchangeItems(any(int[].class), any(int[].class), any(int[].class), any(int[].class))).thenReturn(false);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        next(iv);
        select(iv, 0);
        enterText(iv);
        back(iv);
        select(iv, RETRIEVE_INDEX_NO_COIN);

        verify(cm).exchangeItems(any(int[].class), any(int[].class), any(int[].class), any(int[].class));
        verify(cm).sendOk("Check for an available space on your inventory before retrieving a prize.");
    }
}
