/*  Quest - Outstanding Citizen
    Quest ID : 29508
    Medal    : 1142081 (Outstanding Citizen Medal)

    Requirements (enforced here, since they are not expressible in WZ Check data):
      - Must be married
      - Must belong to a guild
      - Must have at least 1 registered family junior
*/

var status = -1;
var medal = 1142081;

function meetsRequirements() {
    var p = qm.getPlayer();
    var hasJunior = p.getFamilyEntry() != null && p.getFamilyEntry().getJuniorCount() >= 1;
    return p.isMarried() && p.getGuildId() > 0 && hasJunior;
}

function start(mode, type, selection) {
    qm.tryStartQuest();
    qm.dispose();
}

function end(mode, type, selection) {
    status++;
    if (mode != 1) {
        qm.dispose();
    } else {
        if (status == 0) {
            if (meetsRequirements()) {
                qm.sendNext("Congratulations! You have proven yourself a truly #bOutstanding Citizen#k by taking part in #bMarriage#k, a #bGuild#k, and the #bFamily#k system. Here is the #bOutstanding Citizen#k medal.");
            } else {
                qm.sendNext("To earn the #bOutstanding Citizen#k title, you must be #bmarried#k, belong to a #bguild#k, and have at least #b1 Junior#k in your family. Come back once you have done all three.");
            }
        } else if (status == 1) {
            if (meetsRequirements()) {
                if (qm.canHold(medal)) {
                    qm.gainItem(medal, 1);
                    qm.forceCompleteQuest();
                } else {
                    qm.sendNext("Please make room in your EQUIP inventory."); // NOT GMS LIKE
                }
            }
            qm.dispose();
        } else {
            qm.dispose();
        }
    }
}
