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
import net.server.channel.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.TimerManager;
import server.maps.MapManager;
import server.maps.MapleMap;
import testutil.Mocks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the entry-driven Nett's Pyramid lifecycle. {@link Pyramid#startEntry} force-warps into
 * a fresh disposable map and then {@code commenceStage}s it: per participant a countdown clock +
 * "killing/first" start effects + the massacre UI sync, then the Act Gauge drain, the stage timer,
 * and the mob respawn task all start. Stage advance re-commences; dispose cancels everything.
 */
class PyramidLifecycleTest {

    private static final int PYRAMID_MAP = 926010100;

    private Pyramid py;

    @BeforeAll
    static void startTimerManager() {
        // commenceStage schedules gauge/stage/respawn via TimerManager; the executor must exist.
        TimerManager.getInstance().start();
    }

    @AfterEach
    void disposePyramid() {
        if (py != null) {
            py.dispose();   // cancels anything commenceStage scheduled so it cannot fire between tests
        }
    }

    @Test
    void startEntryCommencesStageWithIntroPacketsAndRun() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));

        py.startEntry(mockMap());

        // 1 clock + 3 killing/first effects + 7 sendInfo packets = 11, pinning the v143-style sequence.
        verify(a, times(11)).sendPacket(any());
        assertNotNull(py.gaugeSchedule);
        assertNotNull(py.timer);
        assertNotNull(py.respawnTask);
    }

    @Test
    void advanceStageRecommencesNextStage() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.cs = mockChannelWithDisposableMap();
        py.startEntry(mockMap());    // stage 0 commenced

        py.stage = 1;                // simulate the stage timer having advanced the stage
        clearInvocations(a);
        py.advanceStage(PYRAMID_MAP + 100);

        verify(a, atLeastOnce()).sendPacket(any());   // next stage's intro + UI sync
        assertNotNull(py.gaugeSchedule);               // run tasks restarted
    }

    @Test
    void disposeCancelsRunTasks() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.startEntry(mockMap());
        assertNotNull(py.gaugeSchedule);

        py.dispose();

        assertNull(py.gaugeSchedule);
        assertNull(py.timer);
        assertNull(py.respawnTask);
    }

    @Test
    void onPlayerMoveIsAnEntryDrivenNoOp() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.map = mockMap();

        py.onPlayerMove(a);   // lifecycle is entry-driven now; movement must not start anything

        verify(a, never()).sendPacket(any());
        assertNull(py.gaugeSchedule);
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

    private static Channel mockChannelWithDisposableMap() {
        Channel cs = mock(Channel.class);
        MapManager mf = mock(MapManager.class);
        when(cs.getMapFactory()).thenReturn(mf);
        when(mf.getDisposableMap(anyInt())).thenReturn(mockMap());
        return cs;
    }
}
