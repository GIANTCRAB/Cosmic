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
 * Behavioral tests for Duarte's (NPC 2103013) "Protector of Pharaoh" medal flow. Loads the actual
 * {@code npc/2103013.js} via the GraalJS engine and drives the medal menu option (selection 5) with
 * a mocked {@link NPCConversationManager} ("cm"), asserting that:
 * <ul>
 *   <li>Selecting the medal option accepts the quest (starts 29932) if it hasn't been started -- the
 *       counter only accumulates while 29932 is STARTED, so the quest must be accepted first.</li>
 *   <li>Below 50,000 kills only the running progress is shown -- no item/quest mutation beyond the
 *       initial start.</li>
 *   <li>At 50,000+ kills (with inventory room and no existing medal) the medal is granted and quest
 *       29932 is completed, making it recoverable via Dalair.</li>
 *   <li>An already-held medal is never re-granted; a full EQUIP inventory blocks the grant.</li>
 *   <li>An already-completed quest points the player to Dalair for recovery.</li>
 * </ul>
 *
 * <p>The medal counter is read from info quest {@value #MEDAL_INFO_QUEST}'s progress slot (rendered
 * client-side as {@code #R7760#}); the grant mirrors Mr. Lim's Pyramid Subway flow (1052115.js).
 *
 * <p>Note: GraalJS dispatches {@code cm.startQuest(29932)}/{@code cm.completeQuest(29932)} to the
 * {@code short} overloads, so verifications use {@code (short)} casts to match.
 */
class DuarteMedalScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    private static final int DUARTE_ENTRANCE_MAP = 926010000;
    private static final int MEDAL_QUEST = 29932;
    private static final int MEDAL_INFO_QUEST = 7760;
    private static final int MEDAL_PROTECTOR_OF_PHARAOH = 1142142;
    private static final int MEDAL_KILL_GOAL = 50000;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private ScriptEngine load() {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/2103013.js");
        assertNotNull(engine, "Script npc/2103013.js failed to load/evaluate");
        return engine;
    }

    private void runMedalOption(NPCConversationManager cm) throws Exception {
        ScriptEngine engine = load();
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;
        iv.invokeFunction("start");
        iv.invokeFunction("action", (byte) 1, (byte) 0, 5);   // selection 5 = medal menu
    }

    /** Defaults: quest not started, not completed, given progress. Per-test stubs override as needed. */
    private NPCConversationManager baseCm(int progress) {
        NPCConversationManager cm = mock(NPCConversationManager.class);
        when(cm.getMapId()).thenReturn(DUARTE_ENTRANCE_MAP);
        when(cm.getQuestProgressInt(MEDAL_INFO_QUEST)).thenReturn(progress);
        when(cm.isQuestCompleted(MEDAL_QUEST)).thenReturn(false);
        when(cm.isQuestStarted(MEDAL_QUEST)).thenReturn(false);
        return cm;
    }

    @Test
    void notStarted_belowGoal_startsQuestAndShowsProgress() throws Exception {
        NPCConversationManager cm = baseCm(0);

        runMedalOption(cm);

        verify(cm).startQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("The <Protector of Pharaoh> Medal is bestowed only upon those who defeat 50,000 monsters inside Nett's Pyramid. Your current count: #b0#k / 50000.");
        verify(cm, never()).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), anyShort());
        verify(cm, never()).completeQuest((short) MEDAL_QUEST);
    }

    @Test
    void started_belowGoal_showsProgressWithoutRestarting() throws Exception {
        NPCConversationManager cm = baseCm(12345);
        when(cm.isQuestStarted(MEDAL_QUEST)).thenReturn(true);

        runMedalOption(cm);

        verify(cm, never()).startQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("The <Protector of Pharaoh> Medal is bestowed only upon those who defeat 50,000 monsters inside Nett's Pyramid. Your current count: #b12345#k / 50000.");
        verify(cm, never()).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), anyShort());
        verify(cm, never()).completeQuest((short) MEDAL_QUEST);
    }

    @Test
    void started_atGoal_grantsMedalAndCompletesQuest() throws Exception {
        NPCConversationManager cm = baseCm(MEDAL_KILL_GOAL);
        when(cm.isQuestStarted(MEDAL_QUEST)).thenReturn(true);
        when(cm.canHold(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(true);
        when(cm.haveItem(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(false);

        runMedalOption(cm);

        verify(cm, never()).startQuest((short) MEDAL_QUEST);   // already started
        verify(cm).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), eq((short) 1));
        verify(cm).completeQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("You have proven yourself a true Protector of Pharaoh. Nett acknowledges your strength and bestows the medal upon you.");
    }

    @Test
    void started_atGoal_whenAlreadyHeld_doesNotRegrant() throws Exception {
        NPCConversationManager cm = baseCm(MEDAL_KILL_GOAL);
        when(cm.isQuestStarted(MEDAL_QUEST)).thenReturn(true);
        when(cm.canHold(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(true);
        when(cm.haveItem(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(true);

        runMedalOption(cm);

        verify(cm, never()).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), anyShort());
        verify(cm, never()).completeQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("Please make room in your EQUIP inventory to receive the <Protector of Pharaoh> Medal.");
    }

    @Test
    void started_atGoal_whenInventoryFull_doesNotGrant() throws Exception {
        NPCConversationManager cm = baseCm(MEDAL_KILL_GOAL);
        when(cm.isQuestStarted(MEDAL_QUEST)).thenReturn(true);
        when(cm.canHold(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(false);
        when(cm.haveItem(MEDAL_PROTECTOR_OF_PHARAOH)).thenReturn(false);

        runMedalOption(cm);

        verify(cm, never()).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), anyShort());
        verify(cm, never()).completeQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("Please make room in your EQUIP inventory to receive the <Protector of Pharaoh> Medal.");
    }

    @Test
    void completed_pointsToDalairForRecovery() throws Exception {
        NPCConversationManager cm = baseCm(MEDAL_KILL_GOAL);
        when(cm.isQuestCompleted(MEDAL_QUEST)).thenReturn(true);

        runMedalOption(cm);

        verify(cm, never()).startQuest((short) MEDAL_QUEST);
        verify(cm, never()).gainItem(eq(MEDAL_PROTECTOR_OF_PHARAOH), anyShort());
        verify(cm, never()).completeQuest((short) MEDAL_QUEST);
        verify(cm).sendOk("You have already earned the <Protector of Pharaoh> Medal. If you have lost it, Dalair can recover it for you.");
    }
}
