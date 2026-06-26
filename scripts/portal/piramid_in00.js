/*
    Nett's Pyramid entrance (Pyramid Dunes, map 926010000) - entry portal.
    The Pyramid is started through Duarte (NPC 2103013), who warps the party into the first
    stage directly. Block accidental walk-in entry and point players to the NPC.
*/
function enter(pi) {
    pi.playerMessage(5, "Speak with Duarte to enter Nett's Pyramid.");
    return false;
}
