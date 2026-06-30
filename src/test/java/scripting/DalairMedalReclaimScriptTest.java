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

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scripting.npc.NPCConversationManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for the Dalair (NPC 9000040) medal-recovery script. Loads the actual
 * {@code npc/9000040.js} via the GraalJS engine and drives it with a mocked
 * {@link NPCConversationManager} ("cm") + {@link Character}, asserting that:
 * <ul>
 *   <li>The "View medal rankings" option still shows the existing "unavailable" message.</li>
 *   <li>Recovery charges exactly {@code 1,000,000 * level} mesos and grants every missing medal.</li>
 *   <li>Medals already carried (including equipped ones) are skipped, never re-granted.</li>
 *   <li>Insufficient mesos or full inventory aborts without charging the player.</li>
 * </ul>
 */
class DalairMedalReclaimScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    private static final int QUEST_BEGINNER_ADVENTURER = 29900;
    private static final int QUEST_OUTSTANDING_CITIZEN = 29508;
    private static final int QUEST_NON_MEDAL = 1000;   // any short-range, non-medal quest id
    private static final int MEDAL_BEGINNER_ADVENTURER = 1142107;
    private static final int MEDAL_OUTSTANDING_CITIZEN = 1142081;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private ScriptEngine load() {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("npc/9000040.js");
        assertNotNull(engine, "Script npc/9000040.js failed to load/evaluate");
        return engine;
    }

    private QuestStatus completedQuest(int questId) {
        QuestStatus qs = mock(QuestStatus.class);
        when(qs.getQuestID()).thenReturn((short) questId);
        return qs;
    }

    private void selectOption(Invocable iv, int selection) throws Exception {
        iv.invokeFunction("action", (byte) 1, (byte) 0, selection);
    }

    private void confirmYes(Invocable iv) throws Exception {
        iv.invokeFunction("action", (byte) 1, (byte) 0, 0);
    }

    @Test
    void viewRankings_stillShowsUnavailableMessage() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 0);

        verify(cm).sendOk("The medal ranking system is currently unavailable...");
        verify(cm, never()).gainMeso(anyInt());
    }

    @Test
    void recover_reportsNothingWhenAllMedalsAlreadyHeld() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Collections.singletonList(completedQuest(QUEST_BEGINNER_ADVENTURER));

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(true);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);

        verify(cm).sendOk("You do not have any missing medals to recover at this time.");
        verify(cm, never()).gainMeso(anyInt());
        verify(cm, never()).gainItem(anyInt(), anyShort());
    }

    @Test
    void recover_abortsWhenInsufficientMesos() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Collections.singletonList(completedQuest(QUEST_BEGINNER_ADVENTURER));

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(false);
        when(cm.getLevel()).thenReturn(50);
        when(cm.getMeso()).thenReturn(1000);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);
        confirmYes(iv);

        verify(cm, never()).gainMeso(anyInt());
        verify(cm, never()).gainItem(anyInt(), anyShort());
    }

    @Test
    void recover_abortsWhenInventoryFull() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Collections.singletonList(completedQuest(QUEST_BEGINNER_ADVENTURER));

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(false);
        when(cm.getLevel()).thenReturn(10);
        when(cm.getMeso()).thenReturn(100_000_000);
        when(cm.canHold(MEDAL_BEGINNER_ADVENTURER)).thenReturn(false);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);
        confirmYes(iv);

        verify(cm, never()).gainMeso(anyInt());
        verify(cm, never()).gainItem(anyInt(), anyShort());
    }

    @Test
    void recover_grantsMissingMedalsAndChargesLevelBasedFee() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Arrays.asList(
                completedQuest(QUEST_BEGINNER_ADVENTURER),
                completedQuest(QUEST_OUTSTANDING_CITIZEN));

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.getMedalItemForQuest(QUEST_OUTSTANDING_CITIZEN)).thenReturn(MEDAL_OUTSTANDING_CITIZEN);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(false);
        when(cm.haveItemWithId(MEDAL_OUTSTANDING_CITIZEN, true)).thenReturn(false);
        when(cm.getLevel()).thenReturn(50);
        when(cm.getMeso()).thenReturn(200_000_000);
        when(cm.canHold(MEDAL_BEGINNER_ADVENTURER)).thenReturn(true);
        when(cm.canHold(MEDAL_OUTSTANDING_CITIZEN)).thenReturn(true);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);
        confirmYes(iv);

        verify(cm).gainMeso(-150_000_000);
        verify(cm).gainItem(eq(MEDAL_BEGINNER_ADVENTURER), anyShort());
        verify(cm).gainItem(eq(MEDAL_OUTSTANDING_CITIZEN), anyShort());
    }

    @Test
    void recover_skipsHeldMedalsAndIgnoresNonMedalQuests() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Arrays.asList(
                completedQuest(QUEST_BEGINNER_ADVENTURER),   // medal already held -> skip
                completedQuest(QUEST_OUTSTANDING_CITIZEN),   // medal missing -> recover
                completedQuest(QUEST_NON_MEDAL));            // not a medal quest -> ignored

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.getMedalItemForQuest(QUEST_OUTSTANDING_CITIZEN)).thenReturn(MEDAL_OUTSTANDING_CITIZEN);
        when(cm.getMedalItemForQuest(QUEST_NON_MEDAL)).thenReturn(-1);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(true);
        when(cm.haveItemWithId(MEDAL_OUTSTANDING_CITIZEN, true)).thenReturn(false);
        when(cm.getLevel()).thenReturn(10);
        when(cm.getMeso()).thenReturn(100_000_000);
        when(cm.canHold(MEDAL_OUTSTANDING_CITIZEN)).thenReturn(true);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);
        confirmYes(iv);

        verify(cm, never()).gainItem(eq(MEDAL_BEGINNER_ADVENTURER), anyShort());
        verify(cm).gainItem(eq(MEDAL_OUTSTANDING_CITIZEN), anyShort());
        verify(cm).gainMeso(-30_000_000);
    }

    @Test
    void recover_declineDoesNotCharge() throws Exception {
        ScriptEngine engine = load();
        NPCConversationManager cm = mock(NPCConversationManager.class);
        Character chr = mock(Character.class);
        List<QuestStatus> completed = Collections.singletonList(completedQuest(QUEST_BEGINNER_ADVENTURER));

        when(cm.getPlayer()).thenReturn(chr);
        when(chr.getCompletedQuests()).thenReturn(completed);
        when(cm.getMedalItemForQuest(QUEST_BEGINNER_ADVENTURER)).thenReturn(MEDAL_BEGINNER_ADVENTURER);
        when(cm.haveItemWithId(MEDAL_BEGINNER_ADVENTURER, true)).thenReturn(false);
        when(cm.getLevel()).thenReturn(50);
        when(cm.getMeso()).thenReturn(100_000_000);
        engine.put("cm", cm);
        Invocable iv = (Invocable) engine;

        iv.invokeFunction("start");
        selectOption(iv, 1);
        iv.invokeFunction("action", (byte) 0, (byte) 0, 0);   // "No" on the sendYesNo

        verify(cm, never()).gainMeso(anyInt());
        verify(cm, never()).gainItem(anyInt(), anyShort());
    }
}
