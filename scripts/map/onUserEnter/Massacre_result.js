function start(ms) {
    var py = ms.getPyramid();
    if (py != null) {
        py.sendScore(ms.getPlayer());
    }
    // Drop the Pyramid reference now that the run is over. Done per-player (each member clears
    // their own) so the shared instance is released once everyone has seen their score.
    ms.getPlayer().setPartyQuest(null);
}