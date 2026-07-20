/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.partyquest;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.TimerManager;
import server.maps.MapleMap;
import testutil.Mocks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the mid-run forfeit path ({@link Pyramid#abort}). The Duarte NPC
 * (2103013.js) invokes this when a participant talks to it inside a stage map. Previously the NPC
 * only warped the talker and nulled their own PQ reference -- leaving the Pyramid's gauge/stage/
 * respawn timers running and the talker still in {@code getParticipants()}, so a few seconds later
 * the stage timer re-warped everyone (including the "left" talker) back into a pyramid stage.
 *
 * <p>{@code abort} must: cancel all three run tasks, warp EVERY participant to the entrance, clear
 * each one's PQ reference (which also drops their Pharaoh buffs via {@code onParticipantDetach}),
 * dispose the disposable stage map exactly once, and be a no-op once {@code map} is already null.
 */
class PyramidAbortTest {

    private static final int PYRAMID_MAP = 926010100;
    private static final int ENTRANCE_MAP = 926010000;

    private Pyramid py;

    @BeforeAll
    static void startTimerManager() {
        // startEntry -> commenceStage schedules gauge/stage/respawn via TimerManager.
        TimerManager.getInstance().start();
    }

    @AfterEach
    void disposePyramid() {
        if (py != null) {
            py.dispose();   // belt-and-suspenders: abort already cancels everything
        }
    }

    @Test
    void abortWarpsAllParticipantsClearsRefsCancelsTasksAndDisposesMap() {
        Character a = participantOn(PYRAMID_MAP, 1);
        Character b = participantOn(PYRAMID_MAP, 2);
        py = newPyramid(List.of(a, b));
        py.startEntry(mockMap());   // seeds gaugeSchedule/timer/respawnTask + sets py.map

        MapleMap stageMap = py.map;
        py.abort(ENTRANCE_MAP);

        // Every participant is warped to the entrance and detached from the PQ.
        verify(a).changeMap(ENTRANCE_MAP, 0);
        verify(b).changeMap(ENTRANCE_MAP, 0);
        verify(a).setPartyQuest(null);
        verify(b).setPartyQuest(null);

        // All run tasks are cancelled (the dangling-timer bug this fixes).
        assertNull(py.gaugeSchedule);
        assertNull(py.timer);
        assertNull(py.respawnTask);

        // The disposable stage map is disposed exactly once.
        verify(stageMap, times(1)).dispose();
    }

    @Test
    void abortIsNoOpWhenMapAlreadyNull() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.startEntry(mockMap());

        py.abort(ENTRANCE_MAP);   // first abort tears everything down
        verify(a, times(1)).changeMap(ENTRANCE_MAP, 0);

        // Second abort must be a no-op: no extra warps, no dispose calls on a stale map.
        py.abort(ENTRANCE_MAP);
        verify(a, times(1)).changeMap(ENTRANCE_MAP, 0);
        verify(a, times(1)).setPartyQuest(null);
    }

    @Test
    void abortWithNoMapDoesNotWarpOrDispose() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        // py.map is never set (no startEntry) -> abort must bail out without touching anyone.
        py.abort(ENTRANCE_MAP);

        verify(a, never()).changeMap(anyInt(), anyInt());
        verify(a, never()).setPartyQuest(null);
    }

    private static Pyramid newPyramid(List<Character> participants) {
        return new Pyramid(participants, Pyramid.PyramidMode.EASY, PYRAMID_MAP);
    }

    private static Character participantOn(int mapId, int id) {
        Character chr = Mocks.chr();
        when(chr.getMapId()).thenReturn(mapId);
        when(chr.getId()).thenReturn(id);
        return chr;
    }

    private static MapleMap mockMap() {
        return mock(MapleMap.class);
    }
}
