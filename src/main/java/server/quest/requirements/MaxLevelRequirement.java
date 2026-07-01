/*
 This file is part of the OdinMS Maple Story Server
 Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 Matthias Butz <matze@odinms.de>
 Jan Christian Meyer <vimes@odinms.de>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation version 3 as published by
 the Free Software Foundation. You may not use, modify or distribute
 this program under any other version of the GNU Affero General Public
 License.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package server.quest.requirements;

import client.Character;
import config.YamlConfig;
import provider.Data;
import provider.DataTool;
import server.quest.PartyQuestRegistry;
import server.quest.Quest;
import server.quest.QuestRequirementType;

/**
 * @author Tyler (Twdtwd)
 */
public class MaxLevelRequirement extends AbstractQuestRequirement {
    private int maxLevel;
    private final int questID;


    public MaxLevelRequirement(Quest quest, Data data) {
        super(QuestRequirementType.MAX_LEVEL);
        this.questID = quest.getId();
        processData(data);
    }

    /**
     * @param data
     */
    @Override
    public void processData(Data data) {
        maxLevel = DataTool.getInt(data);
    }


    @Override
    public boolean check(Character chr, Integer npcid) {
        if (shouldBypassMaxLevel(questID)) {
            return true;
        }
        return maxLevel >= chr.getLevel();
    }

    /**
     * Whether the max-level cap should be ignored for the given quest. Returns {@code true} only when
     * the {@code USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT} config flag is enabled AND the quest is not a
     * party-quest entry quest (party quests keep their cap here; their event scripts gate levels too).
     *
     * <p>Extracted as a package-private static helper so the gating logic is unit-testable without
     * instantiating {@link Quest} (whose class initialisation reads WZ data).</p>
     */
    static boolean shouldBypassMaxLevel(int questId) {
        return YamlConfig.config.server.USE_IGNORE_QUEST_MAXLEVEL_REQUIREMENT
                && !PartyQuestRegistry.isPartyQuest(questId);
    }
}
