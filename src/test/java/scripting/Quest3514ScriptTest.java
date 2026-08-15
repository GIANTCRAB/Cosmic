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
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scripting.quest.QuestActionManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Quest3514ScriptTest {
    private static final int HARDCORE_COMPLETION_COST = 2_000_000_000;
    private static final int POTION_COST = 1_000_000;
    private static final int SORCERERS_POTION = 2_022_337;
    private static final int QUEST_EXP = 891_500;

    private final AbstractScriptManager scriptManager = new AbstractScriptManager() {};
    private boolean previousHardcoreMode;

    @BeforeAll
    static void muteGraal() {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    @BeforeEach
    void rememberHardcoreMode() {
        previousHardcoreMode = YamlConfig.config.server.USE_HARDCORE_MODE;
    }

    @AfterEach
    void restoreHardcoreMode() {
        YamlConfig.config.server.USE_HARDCORE_MODE = previousHardcoreMode;
    }

    private Invocable load(QuestActionManager qm) {
        ScriptEngine engine = scriptManager.getInvocableScriptEngine("quest/3514.js");
        assertNotNull(engine, "Script quest/3514.js failed to load/evaluate");
        engine.put("qm", qm);
        return (Invocable) engine;
    }

    private void invokeStart(Invocable script, int mode) throws Exception {
        script.invokeFunction("start", (byte) mode, (byte) 0, 0);
    }

    @Test
    void normalModeRetainsPotionPurchase() throws Exception {
        YamlConfig.config.server.USE_HARDCORE_MODE = false;
        QuestActionManager qm = mock(QuestActionManager.class);
        Character chr = mock(Character.class);
        when(qm.getPlayer()).thenReturn(chr);
        when(chr.getMeso()).thenReturn(POTION_COST);
        when(qm.canHold(SORCERERS_POTION, 1)).thenReturn(true);

        invokeStart(load(qm), 1);

        verify(qm).gainItem(SORCERERS_POTION, (short) 1);
        verify(qm).gainMeso(-POTION_COST);
        verify(qm).startQuest((short) 3514);
        verify(qm, never()).forceCompleteQuest();
    }

    @Test
    void hardcoreModePromptsBeforeMutatingQuestOrMesos() throws Exception {
        YamlConfig.config.server.USE_HARDCORE_MODE = true;
        QuestActionManager qm = mock(QuestActionManager.class);

        invokeStart(load(qm), 1);

        verify(qm).sendYesNo(contains("2,000,000,000 mesos"));
        verify(qm, never()).getMeso();
        verify(qm, never()).gainMeso(anyInt());
        verify(qm, never()).forceStartQuest();
        verify(qm, never()).forceCompleteQuest();
    }

    @Test
    void decliningHardcorePaymentDoesNothing() throws Exception {
        YamlConfig.config.server.USE_HARDCORE_MODE = true;
        QuestActionManager qm = mock(QuestActionManager.class);
        Invocable script = load(qm);

        invokeStart(script, 1);
        invokeStart(script, 0);

        verify(qm).dispose();
        verify(qm, never()).getMeso();
        verify(qm, never()).gainMeso(anyInt());
        verify(qm, never()).forceStartQuest();
        verify(qm, never()).forceCompleteQuest();
    }

    @Test
    void insufficientHardcoreBalanceDoesNotStartOrCharge() throws Exception {
        YamlConfig.config.server.USE_HARDCORE_MODE = true;
        QuestActionManager qm = mock(QuestActionManager.class);
        when(qm.getMeso()).thenReturn(HARDCORE_COMPLETION_COST - 1);
        Invocable script = load(qm);

        invokeStart(script, 1);
        invokeStart(script, 1);

        verify(qm).sendOk(contains("2,000,000,000 mesos"));
        verify(qm, never()).gainMeso(anyInt());
        verify(qm, never()).forceStartQuest();
        verify(qm, never()).forceCompleteQuest();
    }

    @Test
    void exactHardcorePaymentCompletesWithoutGrantingPotion() throws Exception {
        YamlConfig.config.server.USE_HARDCORE_MODE = true;
        QuestActionManager qm = mock(QuestActionManager.class);
        when(qm.getMeso()).thenReturn(HARDCORE_COMPLETION_COST);
        Invocable script = load(qm);

        invokeStart(script, 1);
        invokeStart(script, 1);
        invokeStart(script, 1);

        verify(qm).gainMeso(-HARDCORE_COMPLETION_COST);
        verify(qm).forceStartQuest();
        verify(qm).gainExp(QUEST_EXP);
        verify(qm).forceCompleteQuest();
        verify(qm, never()).gainItem(anyInt(), anyShort());
        verify(qm, times(1)).gainMeso(-HARDCORE_COMPLETION_COST);
        verify(qm, times(1)).forceCompleteQuest();
    }
}
