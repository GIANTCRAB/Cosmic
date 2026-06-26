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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-logic tests for Nett's Pyramid yeti-spawn count. The in-class {@code stage} field is
 * 0-indexed (0..4 -> displayed stages 1..5), so yetis appear on the final two stages (field 3 and 4).
 */
class PyramidYetiSpawnTest {

    @Test
    void noYetisOnFirstThreeStages() {
        for (byte stage = 0; stage <= 2; stage++) {
            for (Pyramid.PyramidMode mode : Pyramid.PyramidMode.values()) {
                assertEquals(0, Pyramid.yetisForStage(stage, mode),
                        "stage " + stage + " (" + mode + ") spawns no yetis");
            }
        }
    }

    @Test
    void penultimateStageScalesWithMode() {
        // field 3 (displayed stage 4): EASY=0, NORMAL=1, HARD=2, HELL=3
        assertEquals(0, Pyramid.yetisForStage((byte) 3, Pyramid.PyramidMode.EASY));
        assertEquals(1, Pyramid.yetisForStage((byte) 3, Pyramid.PyramidMode.NORMAL));
        assertEquals(2, Pyramid.yetisForStage((byte) 3, Pyramid.PyramidMode.HARD));
        assertEquals(3, Pyramid.yetisForStage((byte) 3, Pyramid.PyramidMode.HELL));
    }

    @Test
    void finaleSpawnsOneMoreThanPenultimate() {
        // field 4 (displayed stage 5): EASY=1, NORMAL=2, HARD=3, HELL=4
        assertEquals(1, Pyramid.yetisForStage((byte) 4, Pyramid.PyramidMode.EASY));
        assertEquals(2, Pyramid.yetisForStage((byte) 4, Pyramid.PyramidMode.NORMAL));
        assertEquals(3, Pyramid.yetisForStage((byte) 4, Pyramid.PyramidMode.HARD));
        assertEquals(4, Pyramid.yetisForStage((byte) 4, Pyramid.PyramidMode.HELL));
    }

    @Test
    void harderModesAlwaysSpawnAtLeastAsMany() {
        for (byte stage = 0; stage <= 4; stage++) {
            int easy = Pyramid.yetisForStage(stage, Pyramid.PyramidMode.EASY);
            int normal = Pyramid.yetisForStage(stage, Pyramid.PyramidMode.NORMAL);
            int hard = Pyramid.yetisForStage(stage, Pyramid.PyramidMode.HARD);
            int hell = Pyramid.yetisForStage(stage, Pyramid.PyramidMode.HELL);
            assertEquals(easy, Math.min(easy, normal), "NORMAL >= EASY at stage " + stage);
            assertEquals(normal, Math.min(normal, hard), "HARD >= NORMAL at stage " + stage);
            assertEquals(hard, Math.min(hard, hell), "HELL >= HARD at stage " + stage);
        }
    }
}
