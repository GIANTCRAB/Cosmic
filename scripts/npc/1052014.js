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
 * @npc: Vending Machine
 * @map: 193000000 - Premium Road - Kerning City Internet Cafe
 * @func: Cafe PQ Rewarder
 */

var tickets = [0, 0, 0, 0, 0, 0];
var coinId = 4001158;
var coins = 0;

var hasCoin = false;
var currentTier;
var curItemQty;
var curItemSel;
var advance = true;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && type > 0) {
            cm.dispose();
            return;
        }
        if (mode == 1 && advance) {
            status++;
        } else {
            status--;
        }

        advance = true;

        if (status == 0) {
            hasCoin = cm.haveItem(coinId);
            cm.sendNext("This is the vending machine of the Internet Cafe. Place your erasers or #t" + coinId + "# earned throughout the quests to redeem a prize. You can place #bany amount of erasers#k, however take note that placing #rdifferent erasers#k and #rbigger shots of any of them#k will improve the reward possibilities!");
        } else if (status == 1) {
            var sendStr;
            currentTier = cm.vendingTier(tickets, coins);

            if (currentTier >= 0) {
                sendStr = "With the items you have currently placed, you can retrieve a #r" + cm.vendingTierLabel(currentTier) + "#k prize. Place erasers:";
            } else {
                sendStr = "You have placed no erasers yet. Place erasers:";
            }

            var listStr = "";
            for (var i = 0; i < tickets.length; i++) {
                listStr += "#b#L" + i + "##t" + (4001009 + i) + "##k";
                if (tickets[i] > 0) {
                    listStr += " - " + tickets[i] + " erasers";
                }
                listStr += "#l\r\n";
            }
            if (hasCoin) {
                listStr += "#b#L" + tickets.length + "##t" + coinId + "##k";
                if (coins > 0) {
                    listStr += " - " + coins + " feathers";
                }
                listStr += "#l\r\n";
            }

            cm.sendSimple(sendStr + "\r\n\r\n" + listStr + "#r#L" + getRewardIndex(hasCoin) + "#Retrieve a prize!#l#k\r\n");

        } else if (status == 2) {
            if (selection == getRewardIndex(hasCoin)) {
                if (currentTier < 0) {
                    cm.sendPrev("You have set no erasers. Insert at least one to claim a prize.");
                    advance = false;
                } else {
                    givePrize();
                    cm.dispose();
                }
            } else {
                var tickSel;
                if (selection < tickets.length) {
                    tickSel = 4001009 + selection;
                } else {
                    tickSel = coinId;
                }

                var staged = (selection < tickets.length) ? tickets[selection] : coins;
                curItemQty = cm.getItemQuantity(tickSel) - staged;
                curItemSel = selection;

                if (curItemQty > 0) {
                    cm.sendGetText("How many of #b#t" + tickSel + "##k do you want to insert on the machine? (#r" + curItemQty + "#k available)#k");
                } else {
                    cm.sendPrev("You have got #rnone#k of #b#t" + tickSel + "##k to insert on the machine. Click '#rBack#k' to return to the main interface.");
                    advance = false;
                }
            }
        } else if (status == 3) {
            var text = cm.getText();

            try {
                var placedQty = parseInt(text);
                if (isNaN(placedQty) || placedQty <= 0) {
                    throw true;
                }

                if (placedQty > curItemQty) {
                    cm.sendPrev("You cannot insert the given amount of erasers (#r" + curItemQty + "#k available). Click '#rBack#k' to return to the main interface.");
                    advance = false;
                } else {
                    if (curItemSel < tickets.length) {
                        tickets[curItemSel] += placedQty;
                    } else {
                        coins += placedQty;
                    }

                    cm.sendPrev("Operation succeeded. Click '#rBack#k' to return to the main interface.");
                    advance = false;
                }
            } catch (err) {
                cm.sendPrev("You must enter a positive number of erasers to insert. Click '#rBack#k' to return to the main interface.");
                advance = false;
            }

            status = 2;
        } else {
            cm.dispose();
        }
    }
}

function getRewardIndex(hasCoinFlag) {
    return (!hasCoinFlag) ? tickets.length : tickets.length + 1;
}

function givePrize() {
    var prize = cm.vendingRoll(currentTier);

    var rmIds = [];
    var rmQtys = [];
    for (var i = 0; i < tickets.length; i++) {
        if (tickets[i] > 0) {
            rmIds.push(4001009 + i);
            rmQtys.push(tickets[i]);
        }
    }
    if (coins > 0) {
        rmIds.push(coinId);
        rmQtys.push(coins);
    }

    if (cm.exchangeItems(rmIds, rmQtys, [prize.itemId()], [prize.quantity()])) {
        for (var i = 0; i < tickets.length; i++) {
            tickets[i] = 0;
        }
        coins = 0;
    } else {
        cm.sendOk("Check for an available space on your inventory before retrieving a prize.");
    }
}
