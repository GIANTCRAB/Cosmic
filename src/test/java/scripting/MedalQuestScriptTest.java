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
import client.FamilyEntry;
import client.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import scripting.quest.QuestActionManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for the medal-quest scripts. Loads the actual .js files via the GraalJS engine
 * and drives them with a mocked QuestActionManager ("qm") + Character, asserting that medals are
 * only granted when their requirements are satisfied.
 *
 * <p>These tests cover:
 * <ul>
 *   <li>Outstanding Citizen (29508) — must be married + in a guild + have a family junior.</li>
 *   <li>Aran medals (29924) — the former getJob()/100 enum-division bug is fixed via getJob().getId().</li>
 * </ul>
 */
class MedalQuestScriptTest {
    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private ScriptEngine load(String path) {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine(path);
        assertNotNull(engine, "Script " + path + " failed to load/evaluate");
        return engine;
    }

    private void invokeEnd(ScriptEngine engine, QuestActionManager qm) throws Exception {
        engine.put("qm", qm);
        Invocable iv = (Invocable) engine;
        iv.invokeFunction("end", (byte) 1, (byte) 0, 0);   // status -> 0
        iv.invokeFunction("end", (byte) 1, (byte) 0, 0);   // status -> 1 (completion branch)
    }

    @Test
    void outstandingCitizen_notAwardedWhenRequirementsUnmet() throws Exception {
        ScriptEngine engine = load("quest/29508.js");
        QuestActionManager qm = mock(QuestActionManager.class);
        Character chr = mock(Character.class);
        when(qm.getPlayer()).thenReturn(chr);
        when(chr.isMarried()).thenReturn(false);   // not married -> requirements unmet

        invokeEnd(engine, qm);

        verify(qm, never()).forceCompleteQuest();
    }

    @Test
    void outstandingCitizen_awardedWhenRequirementsMet() throws Exception {
        ScriptEngine engine = load("quest/29508.js");
        QuestActionManager qm = mock(QuestActionManager.class);
        Character chr = mock(Character.class);
        FamilyEntry familyEntry = mock(FamilyEntry.class);

        when(qm.getPlayer()).thenReturn(chr);
        when(chr.isMarried()).thenReturn(true);
        when(chr.getGuildId()).thenReturn(5);
        when(chr.getFamilyEntry()).thenReturn(familyEntry);
        when(familyEntry.getJuniorCount()).thenReturn(1);
        when(qm.canHold(1142081)).thenReturn(true);

        invokeEnd(engine, qm);

        verify(qm).forceCompleteQuest();
    }

    @Test
    void aranMedal_awardedForAranJob() throws Exception {
        ScriptEngine engine = load("quest/29924.js");
        QuestActionManager qm = mock(QuestActionManager.class);
        Character chr = mock(Character.class);
        when(qm.getPlayer()).thenReturn(chr);
        when(chr.getLevel()).thenReturn(30);
        when(chr.getJob()).thenReturn(Job.ARAN1);   // id 2100 -> getId()/100 == 21
        when(qm.haveItem(1142129)).thenReturn(false);
        when(qm.canHold(1142129)).thenReturn(true);

        engine.put("qm", qm);
        ((Invocable) engine).invokeFunction("start", (byte) 1, (byte) 0, 0);

        verify(qm).forceCompleteQuest();
    }

    @Test
    void aranMedal_notAwardedForNonAranJob() throws Exception {
        ScriptEngine engine = load("quest/29924.js");
        QuestActionManager qm = mock(QuestActionManager.class);
        Character chr = mock(Character.class);
        when(qm.getPlayer()).thenReturn(chr);
        when(chr.getLevel()).thenReturn(30);
        when(chr.getJob()).thenReturn(Job.BEGINNER);   // id 0 -> getId()/100 == 0 != 21

        engine.put("qm", qm);
        ((Invocable) engine).invokeFunction("start", (byte) 1, (byte) 0, 0);

        verify(qm, never()).forceCompleteQuest();
    }
}
