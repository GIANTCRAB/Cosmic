/**
 * Generic fallback for medal quests (any quest with a viewMedalItem) that have no
 * dedicated quest/<id>.js (or WZ startscript/endscript) implementation.
 *
 * Unlike the previous stub, this does NOT force-complete the quest. It runs the
 * normal requirement-checked start/complete path, so a medal is only granted when
 * the player actually satisfies the quest's requirements (level, job, NPC, quest
 * prerequisites, etc.).
 *
 * @author Arnah, Ronan
 */

function start(mode, type, selection) {
    if (!qm.tryStartQuest()) {
        qm.sendOk("You do not meet the requirements for this title yet.");
    }
    qm.dispose();
}

function end(mode, type, selection) {
    if (!qm.tryCompleteQuest()) {
        qm.sendOk("You do not meet the requirements for this title yet.");
    }
    qm.dispose();
}
