/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package scripting.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pure script-path precedence logic in {@link QuestScriptManager}, without
 * touching the filesystem or loading any WZ data.
 */
class QuestScriptManagerTest {

    @Test
    void resolvesWzScriptNameFirst() {
        List<String> candidates = QuestScriptManager.resolveQuestScriptCandidates("q29508e", (short) 29508, true);

        assertEquals(List.of("quest/q29508e.js", "quest/29508.js", "quest/medalQuest.js"), candidates);
    }

    @Test
    void fallsBackToQuestIdScriptWhenNoWzName() {
        List<String> candidates = QuestScriptManager.resolveQuestScriptCandidates("", (short) 29900, true);

        assertEquals(List.of("quest/29900.js", "quest/medalQuest.js"), candidates);
    }

    @Test
    void nullWzNameHandled() {
        List<String> candidates = QuestScriptManager.resolveQuestScriptCandidates(null, (short) 29508, true);

        assertEquals(List.of("quest/29508.js", "quest/medalQuest.js"), candidates);
    }

    @Test
    void noMedalFallbackForNonMedalQuest() {
        List<String> candidates = QuestScriptManager.resolveQuestScriptCandidates(null, (short) 1000, false);

        assertEquals(List.of("quest/1000.js"), candidates);
    }
}
