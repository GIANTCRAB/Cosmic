/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package server.life;

import client.Character;
import client.Disease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the player-side stun mechanic triggered by monster attacks.
 *
 * <p>These tests exercise {@link MobSkill} directly (built via the package-private
 * {@link MobSkill.Builder}) so they are hermetic and deterministic, independent of WZ
 * data and the {@link client.Client}/map wiring. They prove the stun mechanics that
 * run once a monster attack has reached the disease path.
 *
 * <p>The upstream gate — that a monster attack only applies its disease when
 * {@code damage > 0} — lives inline in {@code TakeDamageHandler} and is intentionally
 * not asserted here. What these tests do prove is that stun application is
 * magnitude-independent (any positive damage stuns the same way) and that the chance
 * and duration vary by skill level.
 */
class StunMobSkillTest {

    @Test
    @DisplayName("Case 1: a landed hit applies stun regardless of damage magnitude (no threshold beyond damage > 0)")
    void applyEffect_onHit_appliesStun() {
        Character player = mock(Character.class);
        Monster monster = mock(Monster.class);
        MobSkill stun = new MobSkill.Builder(MobSkillType.STUN, 1).prop(1.0f).build();

        stun.applyEffect(player, monster, false, Collections.emptyList());

        verify(player).giveDebuff(eq(Disease.STUN), same(stun));
    }

    @Test
    @DisplayName("Case 2: a hit that does not take effect does not stun (avoid/miss is gated upstream at damage > 0)")
    void applyEffect_whenChanceFails_doesNotStun() {
        Character player = mock(Character.class);
        Monster monster = mock(Monster.class);
        MobSkill stun = new MobSkill.Builder(MobSkillType.STUN, 1).prop(0.0f).build();

        stun.applyEffect(player, monster, false, Collections.emptyList());

        verify(player, never()).giveDebuff(any(Disease.class), any(MobSkill.class));
    }

    @Test
    @DisplayName("Case 3: higher stun levels roll a higher stun rate than lower levels")
    void makeChanceResult_ratesDifferByLevel() {
        MobSkill lowLevelStun = new MobSkill.Builder(MobSkillType.STUN, 1).prop(0.5f).build();
        MobSkill highLevelStun = new MobSkill.Builder(MobSkillType.STUN, 3).prop(1.0f).build();

        int trials = 2000;
        int lowHits = 0;
        int highHits = 0;
        for (int i = 0; i < trials; i++) {
            if (lowLevelStun.makeChanceResult()) {
                lowHits++;
            }
            if (highLevelStun.makeChanceResult()) {
                highHits++;
            }
        }

        double lowRate = (double) lowHits / trials;

        assertEquals(trials, highHits, "A 100% prop should stun on every trial");
        assertTrue(lowHits > 0 && lowHits < trials, "A 50% prop should stun sometimes but not always");
        assertTrue(lowHits < highHits, "A lower level should stun less often than a higher level");
        assertTrue(lowRate > 0.35 && lowRate < 0.65,
                "A 50% prop should land near 0.5 over many trials (was " + lowRate + ")");
    }

    @Test
    @DisplayName("Case 3 anchor: a 0% prop never stuns and a 100% prop always stuns")
    void makeChanceResult_extremesAreDeterministic() {
        MobSkill neverStun = new MobSkill.Builder(MobSkillType.STUN, 1).prop(0.0f).build();
        MobSkill alwaysStun = new MobSkill.Builder(MobSkillType.STUN, 1).prop(1.0f).build();

        int trials = 1000;
        int neverHits = 0;
        int alwaysHits = 0;
        for (int i = 0; i < trials; i++) {
            if (neverStun.makeChanceResult()) {
                neverHits++;
            }
            if (alwaysStun.makeChanceResult()) {
                alwaysHits++;
            }
        }

        assertEquals(0, neverHits, "A 0% prop should never stun");
        assertEquals(trials, alwaysHits, "A 100% prop should always stun");
    }

    @ParameterizedTest
    @ValueSource(longs = {1000L, 3000L, 5000L})
    @DisplayName("Case 4: stun duration reflects the configured level value")
    void getDuration_matchesConfiguredLevel(long duration) {
        MobSkill stun = new MobSkill.Builder(MobSkillType.STUN, 1).duration(duration).build();

        assertEquals(duration, stun.getDuration());
    }

    @Test
    @DisplayName("Case 4: different stun levels yield different durations")
    void getDuration_differsAcrossLevels() {
        MobSkill level1 = new MobSkill.Builder(MobSkillType.STUN, 1).duration(5000L).build();
        MobSkill level6 = new MobSkill.Builder(MobSkillType.STUN, 6).duration(1000L).build();

        assertNotEquals(level1.getDuration(), level6.getDuration());
    }
}
