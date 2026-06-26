/*
    Nett's Pyramid - onFirstUserEnter for the battle ("killing") stage maps.
    Starts the stage: schedules the stage timer and the Act Gauge drain.
*/
function start(ms) {
    var py = ms.getPyramid();
    if (py != null) {
        py.timer();
    }
}
