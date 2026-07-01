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
 * Pure-logic tests for the "Protector of Pharaoh" medal counter accumulation, extracted from
 * {@link Pyramid#updateMedalProgress(client.Character)} into the testable static helper
 * {@link Pyramid#applyMedalRun(String, int, int)}.
 *
 * <p>Each run contributes {@code kill + cool} to the counter stored in info quest 7760's progress
 * slot; the helper mirrors the canonical GMS post-run handler (null/blank starts at 0) and caps the
 * running total at the 50,000-kill goal.
 */
class PyramidMedalProgressTest {

    @Test
    void nullCounterStartsAtZero() {
        assertEquals("120", Pyramid.applyMedalRun(null, 100, 20));
    }

    @Test
    void blankCounterStartsAtZero() {
        // The progress slot returns "" for an unset counter (QuestStatus.getProgress default).
        assertEquals("120", Pyramid.applyMedalRun("", 100, 20));
    }

    @Test
    void accumulatesKillAndCoolOnTopOfCurrent() {
        assertEquals("1550", Pyramid.applyMedalRun("1000", 500, 50));
    }

    @Test
    void bothKillAndCoolContribute() {
        assertEquals("1200", Pyramid.applyMedalRun("0", 1000, 200));
    }

    @Test
    void zeroContributionLeavesCounterUnchanged() {
        assertEquals("12345", Pyramid.applyMedalRun("12345", 0, 0));
    }

    @Test
    void capsAtKillGoal() {
        // 49990 + 20 would be 50010, but the counter clamps at the 50,000 goal.
        assertEquals("50000", Pyramid.applyMedalRun("49990", 20, 0));
        assertEquals("50000", Pyramid.applyMedalRun("49990", 5, 5));
    }

    @Test
    void counterAtGoalStaysCapped() {
        assertEquals("50000", Pyramid.applyMedalRun("50000", 100, 0));
    }

    @Test
    void overshotCounterIsClampedDownToGoal() {
        // A corrupt/oversized stored value should not let the counter exceed the goal.
        assertEquals("50000", Pyramid.applyMedalRun("60000", 100, 0));
    }

    @Test
    void unparseableDataFallsBackToZero() {
        assertEquals("10", Pyramid.applyMedalRun("garbage", 5, 5));
    }

    @Test
    void accumulationOverMultipleRuns() {
        // Simulate three consecutive runs applied to the same counter.
        String counter = Pyramid.applyMedalRun(null, 100, 20);   // 120
        counter = Pyramid.applyMedalRun(counter, 200, 30);        // 350
        counter = Pyramid.applyMedalRun(counter, 500, 50);        // 900
        assertEquals("900", counter);
    }

    @Test
    void multiRunAccumulationCapsAtGoal() {
        String counter = Pyramid.applyMedalRun(null, 40000, 0);  // 40000
        counter = Pyramid.applyMedalRun(counter, 9000, 0);        // 49000
        counter = Pyramid.applyMedalRun(counter, 2000, 0);        // 51000 -> capped 50000
        assertEquals("50000", counter);
    }
}
