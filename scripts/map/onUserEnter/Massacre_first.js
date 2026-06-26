/*
    Nett's Pyramid - onUserEnter for the battle stage maps.
    Re-syncs the massacre UI (hit/miss/cool/skill/party/laststage counters and the Act Gauge)
    to each player as they enter. Late-arriving party members rely on this to see the live score.
*/
function start(ms) {
    var py = ms.getPyramid();
    if (py != null) {
        py.sendInfo(ms.getPlayer());
    }
}
