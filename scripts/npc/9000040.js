/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

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
/* Dalair
	Medal NPC.
 */

var status = 0;
var selectedType = -1;
var recoverableMedals = null;
var totalCost = 0;

var MEDAL_RECOVERY_FEE_PER_LEVEL = 3000000;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode < 1) {
        cm.dispose();
        return;
    }
    status++;

    if (status == 0) {
        cm.sendSimple("I am Dalair, representative of the God of Honor. How may I assist you today?#b\r\n#L0#I want to see the medal rankings.#l\r\n#L1#I'd like to recover medals I have lost.#l#k");
    } else if (status == 1) {
        selectedType = selection;
        if (selectedType == 0) {
            cm.sendOk("The medal ranking system is currently unavailable...");
            cm.dispose();
        } else if (selectedType == 1) {
            recoverableMedals = getRecoverableMedals();
            if (recoverableMedals.length == 0) {
                cm.sendOk("You do not have any missing medals to recover at this time.");
                cm.dispose();
                return;
            }
            totalCost = MEDAL_RECOVERY_FEE_PER_LEVEL * cm.getLevel();
            var msg = "The following medals have been recovered into my records, but you are no longer carrying them. I can restore them to you for a service fee of #e3,000,000 mesos per character level#n.#b\r\n\r\n";
            for (var i = 0; i < recoverableMedals.length; i++) {
                msg += "#v" + recoverableMedals[i] + "# #z" + recoverableMedals[i] + "#\r\n";
            }
            msg += "\r\n#kTotal medals: #e" + recoverableMedals.length + "#n\r\n";
            msg += "Total cost: #e" + totalCost + "#n mesos\r\n\r\n";
            msg += "Would you like to proceed with the recovery?";
            cm.sendYesNo(msg);
        }
    } else if (status == 2) {
        if (cm.getMeso() < totalCost) {
            cm.sendOk("You do not have enough mesos. You need at least #e" + totalCost + "#n mesos to recover your lost medals.");
            cm.dispose();
            return;
        }
        for (var i = 0; i < recoverableMedals.length; i++) {
            if (!cm.canHold(recoverableMedals[i])) {
                cm.sendOk("Please make sure you have enough available #bEQUIP#k inventory space to hold all of your medals, then speak with me again.");
                cm.dispose();
                return;
            }
        }

        cm.gainMeso(-totalCost);
        for (var i = 0; i < recoverableMedals.length; i++) {
            cm.gainItem(recoverableMedals[i], 1);
        }
        cm.sendOk("Your lost medals have been successfully recovered! #e" + totalCost + "#n mesos have been deducted from your account.");
        cm.dispose();
    }
}

function getRecoverableMedals() {
    var medals = [];
    var completed = cm.getPlayer().getCompletedQuests();
    for (var i = 0; i < completed.size(); i++) {
        var questId = completed.get(i).getQuestID();
        var medalId = cm.getMedalItemForQuest(questId);
        if (medalId != -1 && medals.indexOf(medalId) == -1 && !cm.haveItemWithId(medalId, true)) {
            medals.push(medalId);
        }
    }
    return medals;
}
