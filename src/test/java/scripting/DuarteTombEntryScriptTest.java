/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package scripting;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import scripting.npc.NPCConversationManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for Duarte's (NPC 2103013) "Enter Pharaoh Yeti's Tomb" flow -- the path that the
 * original bug surfaced through (the player spent a gem and landed in an empty shared room). Loads
 * the actual {@code npc/2103013.js} via the GraalJS engine and drives menu option 3 (tomb entry) then
 * a gem pick, asserting on a mocked {@link NPCConversationManager} ("cm") that:
 * <ul>
 *   <li>The spent gem is consumed ({@code gainItem(gem, -1)}).</li>
 *   <li>The gem's ordinal (0..3 = EASY..HELL) is forwarded to {@code cm.enterPharaohTomb}, which
 *       mints a fresh per-player instance and seeds the mode-matched Jr. Yeti (covered by
 *       {@code PharaohTombInstanceTest}). Previously the script called {@code cm.warp} to a shared,
 *       empty map -- this test locks that the bridge is used instead.</li>
 *   <li>A player without the gem is told so and neither the gem nor the entry is consumed.</li>
 * </ul>
 *
 * <p>GraalJS dispatches {@code cm.gainItem(id, count)} to the {@code (int, short)} overload, so the
 * verifications cast {@code -1} to {@code short} to match (same convention as
 * {@code DuarteMedalScriptTest}).
 */
class DuarteTombEntryScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    private static final int DUARTE_ENTRANCE_MAP = 926010000;

    // GEMS = [Sapphire, Ruby, Emerald, Topaz] ordered to match PyramidMode ordinals EASY..HELL.
    private static final int[] GEMS = {4001322, 4001323, 4001324, 4001325};

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private ScriptEngine load() {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/2103013.js");
        assertNotNull(engine, "Script npc/2103013.js failed to load/evaluate");
        return engine;
    }

    /**
     * Drives menu option 3 ("Enter Pharaoh Yeti's Tomb") then selects the gem at {@code gemOrdinal}.
     */
    private void runTombEntry(NPCConversationManager cm, int gemOrdinal) throws Exception {
        ScriptEngine engine = load();
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;
        iv.invokeFunction("start");
        iv.invokeFunction("action", (byte) 1, (byte) 0, 3);              // pick "Enter Pharaoh Yeti's Tomb"
        iv.invokeFunction("action", (byte) 1, (byte) 0, gemOrdinal);     // pick the gem
    }

    private NPCConversationManager cmWithGem(int gemId) {
        NPCConversationManager cm = mock(NPCConversationManager.class);
        when(cm.getMapId()).thenReturn(DUARTE_ENTRANCE_MAP);
        when(cm.haveItem(gemId)).thenReturn(true);
        return cm;
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void spendingAGemConsumesItAndEntersModeMatchedTombInstance(int gemOrdinal) throws Exception {
        int gem = GEMS[gemOrdinal];
        NPCConversationManager cm = cmWithGem(gem);

        runTombEntry(cm, gemOrdinal);

        verify(cm).gainItem(eq(gem), eq((short) -1));
        // The ordinal IS the mode (EASY=0..HELL=3), so it drives which chest the tomb Jr. Yeti drops.
        verify(cm).enterPharaohTomb(gemOrdinal);
    }

    @Test
    void hellGemForwardsHellOrdinal() throws Exception {
        // Explicit HELL case: the Topaz gem (earned only by clearing HELL) must forward ordinal 3,
        // which is what makes the tomb Jr. Yeti drop the HELL chest (sole source of the Immortal Belt).
        NPCConversationManager cm = cmWithGem(GEMS[3]);

        runTombEntry(cm, 3);

        verify(cm).gainItem(eq(GEMS[3]), eq((short) -1));
        verify(cm).enterPharaohTomb(3);
    }

    @Test
    void missingGemShowsMessageAndConsumesNothing() throws Exception {
        NPCConversationManager cm = mock(NPCConversationManager.class);
        when(cm.getMapId()).thenReturn(DUARTE_ENTRANCE_MAP);
        when(cm.haveItem(GEMS[0])).thenReturn(false);   // no Sapphire gem

        runTombEntry(cm, 0);

        verify(cm).sendOk("You'll need a Pharaoh Yeti's Gem to enter Pharaoh Yeti's Tomb. Are you sure you have one?");
        verify(cm, never()).gainItem(eq(GEMS[0]), anyShort());
        verify(cm, never()).enterPharaohTomb(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void tombEntryDoesNotWarpToSharedMap() throws Exception {
        // Regression lock for the original bug: the script must NOT call cm.warp (which resolves the
        // shared cached tomb map). It must go through cm.enterPharaohTomb (the per-instance bridge).
        NPCConversationManager cm = cmWithGem(GEMS[1]);

        runTombEntry(cm, 1);

        verify(cm, never()).warp(org.mockito.ArgumentMatchers.anyInt());
        verify(cm, never()).warp(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
