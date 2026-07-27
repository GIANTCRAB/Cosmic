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

/**
 * @author: Ronan
 * @npc: Billy
 * @map: 193000000 - Premium Road - Kerning City Internet Cafe
 * @func: Cafe PQ Reward Announcer
 */

var status;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && status == 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (status == 0) {
            var sendStr = "The #bInternet Cafe Party Quest#k rewards players with ticket-like #bfigure erasers#k, that can be used on the vending machine to retrieve prizes. By further increasing the stakes, one can get better prizes, separated by #rtiers#k.\r\n\r\nThe possible rewards for each tier are depicted here:\r\n\r\n#b";
            for (var i = 0; i < cm.vendingTierCount(); i++) {
                sendStr += "#L" + i + "#" + cm.vendingTierLabel(i) + "#l\r\n";
            }

            cm.sendSimple(sendStr);
        } else if (status == 1) {
            var entries = cm.vendingRewardEntries(selection);

            var sendStr = "The following items are being awarded at #b" + cm.vendingTierLabel(selection) + "#k:\r\n\r\n";
            for (var i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                sendStr += "  #L" + i + "# #i" + entry.itemId() + "#  #t" + entry.itemId() + "#";
                if (entry.quantity() > 1) {
                    sendStr += " (" + entry.quantity() + ")";
                }
                sendStr += "#l\r\n";
            }

            cm.sendPrev(sendStr);
        } else if (status == 2) {
            cm.dispose();
        }
    }
}
