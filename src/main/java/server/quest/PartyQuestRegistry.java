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

import provider.Data;
import provider.DataProvider;

import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which quest IDs are party-quest *entry* quests, as declared by {@code wz/Quest.wz/PQuest.img}.
 *
 * <p>The {@link Quest} engine itself does not distinguish party quests from normal quests, so this
 * registry is the single source of truth used to keep party-quest level caps intact while allowing
 * the max-level cap to be lifted for ordinary quests (see
 * {@code MaxLevelRequirement} + {@code config.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT}).</p>
 *
 * <p>The extraction logic is split into a pure static helper ({@link #extractPartyQuestIds(Data)})
 * so it can be unit-tested without touching the filesystem; {@link #load(DataProvider)} performs the
 * one-time WZ read at server startup.</p>
 */
public final class PartyQuestRegistry {
    private static volatile Set<Integer> partyQuestIds = Set.of();

    private PartyQuestRegistry() {
    }

    /**
     * Pure extraction: reads the top-level child node names of {@code PQuest.img} and collects those
     * that parse as integer quest IDs. Non-numeric children (defensive: there are none in the shipped
     * data) are silently skipped.
     */
    public static Set<Integer> extractPartyQuestIds(Data pQuestData) {
        Set<Integer> ids = new HashSet<>();
        for (Data child : pQuestData.getChildren()) {
            try {
                ids.add(Integer.parseInt(child.getName()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Set.copyOf(ids);
    }

    /**
     * One-time startup load. Reads {@code PQuest.img} from the given quest data provider and stores
     * the resulting ID set for {@link #isPartyQuest(int)} lookups.
     */
    public static void load(DataProvider questData) {
        Data pQuest = questData.getData("PQuest.img");
        partyQuestIds = (pQuest == null) ? Set.of() : extractPartyQuestIds(pQuest);
    }

    public static boolean isPartyQuest(int questId) {
        return partyQuestIds.contains(questId);
    }
}
