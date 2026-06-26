/*
    Nett's Pyramid - onUserEnter for the battle stage maps.
    Intentionally a no-op. Per-player massacre UI init is NOT sent here: sending it synchronously
    during map entry reaches the client mid-fade and crashes it (the massacre UI is only valid once
    the fieldType=23 field has loaded). Init is instead sent on each participant's first ground-walk
    (Pyramid.onPlayerMove, hooked from MovePlayerHandler).
*/
function start(ms) {
}
