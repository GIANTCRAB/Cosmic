function start(ms) {
    var py = ms.getPyramid();
    if (py != null) {
        py.sendScore(ms.getPlayer());
    }
    // Intentionally do NOT clear the Pyramid reference here. It must survive so the Duarte NPC
    // (2103013) on this result map can read the mode and hand out the matching gem reward, and
    // so isCleared() can distinguish a pass (route to 926020001) from a fail (route to the
    // entrance). The NPC clears the reference per-player once the gem has been given. Pharaoh's
    // Blessing buffs were already stripped by warpOut() before the player landed here.
}