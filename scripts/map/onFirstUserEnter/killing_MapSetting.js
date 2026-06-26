/*
    Nett's Pyramid - onFirstUserEnter for the battle ("killing") stage maps.
    Intentionally a no-op. The stage timer and Act Gauge drain are NOT started here: starting them
    synchronously during map entry dispatches massacre/gauge packets while the client is still
    fading into the fieldType=23 field, which hard-crashes the v83 client. The run is instead
    started on the first participant ground-walk (Pyramid.onPlayerMove, hooked from
    MovePlayerHandler).
*/
function start(ms) {
}
