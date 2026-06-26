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

import client.BuffStat;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the long-lived Pharaoh's Blessing buffs (40-minute duration, carried on the
 * shared BERSERK/BOOSTER buff stats). They are granted during a run by {@link Pyramid#checkBuffs}
 * and must be dropped when the run ends -- via {@link Pyramid#warpOut} / {@link Pyramid#dispose} and
 * via {@link Pyramid#onParticipantDetach} (fired from {@code setPartyQuest(null)}, which is the path
 * the NPC-forfeit and result scripts take) -- but kept while advancing between stages.
 *
 * <p>Removal is asserted on {@link Character#cancelBuffStats(BuffStat)}: the sourceid-keyed
 * {@code cancelEffect(itemId)} path does not reliably clear this shared-stat buff at runtime, so the
 * stats are cancelled directly.
 */
class PyramidBuffCleanupTest {

    private static final int PYRAMID_MAP = 926010100;

    private Pyramid py;

    @BeforeAll
    static void startTimerManager() {
        // advanceStage -> commenceStage schedules gauge/stage/respawn via TimerManager.
        TimerManager.getInstance().start();
    }

    @AfterEach
    void disposePyramid() {
        if (py != null) {
            py.dispose();   // cancels anything commenceStage scheduled so it cannot fire between tests
        }
    }

    @Test
    void warpOutDropsPyramidBuffStats() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.map = mockMap();

        py.warpOut();

        verify(a).cancelBuffStats(BuffStat.BERSERK);
        verify(a).cancelBuffStats(BuffStat.BOOSTER);
    }

    @Test
    void disposeDropsPyramidBuffStatsForEveryParticipant() {
        Character a = participantOn(PYRAMID_MAP, 1);
        Character b = participantOn(PYRAMID_MAP, 2);
        py = newPyramid(List.of(a, b));
        py.map = mockMap();

        py.dispose();

        verify(a).cancelBuffStats(BuffStat.BERSERK);
        verify(a).cancelBuffStats(BuffStat.BOOSTER);
        verify(b).cancelBuffStats(BuffStat.BERSERK);
        verify(b).cancelBuffStats(BuffStat.BOOSTER);
    }

    @Test
    void onParticipantDetachDropsPyramidBuffStats() {
        // The NPC-forfeit (2103013.js) and result (Massacre_result.js) scripts finalize the run with
        // setPartyQuest(null), which fires this hook -- so the buff is cancelled on those paths too.
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));

        py.onParticipantDetach(a);

        verify(a).cancelBuffStats(BuffStat.BERSERK);
        verify(a).cancelBuffStats(BuffStat.BOOSTER);
    }

    @Test
    void advanceStageKeepsPyramidBuffsAcrossStages() {
        Character a = participantOn(PYRAMID_MAP, 1);
        py = newPyramid(List.of(a));
        py.cs = mockChannelWithDisposableMap();
        py.startEntry(mockMap());    // stage 0 commenced

        py.stage = 1;                // simulate the stage timer having advanced the stage
        py.advanceStage(PYRAMID_MAP + 100);

        // Buffs are progress that must carry over to the next stage -- they are not cancelled here.
        verify(a, never()).cancelBuffStats(any(BuffStat.class));
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
        when(mf.getDisposableMap(any(Integer.class))).thenReturn(mockMap());
        return cs;
    }
}
