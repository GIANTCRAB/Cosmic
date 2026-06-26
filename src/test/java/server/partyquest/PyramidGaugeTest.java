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
import org.junit.jupiter.api.Test;
import testutil.Mocks;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Instance-level tests for Nett's Pyramid gauge/counter mechanics. Uses the package-private test
 * constructor so {@link Pyramid} can be built without booting the server, and asserts on the
 * package-private fields directly. Counts are kept below the 250 buff threshold to avoid touching
 * the {@code ItemInformationProvider} singleton.
 */
class PyramidGaugeTest {

    @Test
    void decreaseForModeMatchesIntendedDrainRates() {
        // Regression guard for the switch fall-through bug that made every mode drain like HELL (3).
        assertEquals(1, Pyramid.decreaseForMode(Pyramid.PyramidMode.EASY, 1), "EASY solo base");
        assertEquals(2, Pyramid.decreaseForMode(Pyramid.PyramidMode.NORMAL, 1), "NORMAL solo base");
        assertEquals(2, Pyramid.decreaseForMode(Pyramid.PyramidMode.HARD, 1), "HARD solo base");
        assertEquals(3, Pyramid.decreaseForMode(Pyramid.PyramidMode.HELL, 1), "HELL solo base");
        assertEquals(3, Pyramid.decreaseForMode(Pyramid.PyramidMode.EASY, 2), "EASY party of 2: base + partySize");
        assertEquals(5, Pyramid.decreaseForMode(Pyramid.PyramidMode.EASY, 4), "EASY party of 4: base + partySize");
        assertEquals(6, Pyramid.decreaseForMode(Pyramid.PyramidMode.HELL, 3), "HELL party of 3: base + partySize");
        assertEquals(1, Pyramid.decreaseForMode(Pyramid.PyramidMode.EASY, 0), "no participants treated as solo");
    }

    @Test
    void constructorAppliesPerModeScaling() {
        Pyramid easy = newPyramid(Pyramid.PyramidMode.EASY);
        assertEquals(1, easy.decrease);
        assertEquals(5, easy.coolAdd);   // 5 + 0
        assertEquals(4, easy.missSub);   // 4 + 0

        Pyramid hell = newPyramid(Pyramid.PyramidMode.HELL);
        assertEquals(3, hell.decrease);
        assertEquals(8, hell.coolAdd);   // 5 + 3
        assertEquals(7, hell.missSub);   // 4 + 3
    }

    @Test
    void constructorAppliesPartyScalingToDrain() {
        Pyramid easyParty2 = new Pyramid(List.of(participant(), participant()), Pyramid.PyramidMode.EASY, 926010100);
        assertEquals(3, easyParty2.decrease, "EASY party of 2: base 1 + 2");

        Pyramid hellParty3 = new Pyramid(List.of(participant(), participant(), participant()), Pyramid.PyramidMode.HELL, 926010100);
        assertEquals(6, hellParty3.decrease, "HELL party of 3: base 3 + 3");
    }

    @Test
    void killIncrementsCounterAndGaugeUntilCapped() {
        Pyramid py = newPyramid(Pyramid.PyramidMode.EASY);
        assertEquals(0, py.gauge.get());
        assertEquals(0, py.count.get());

        for (int i = 0; i < 105; i++) {
            py.kill();
        }

        assertEquals(105, py.kill);
        assertEquals(100, py.gauge.get(), "gauge must clamp at 100");
        assertEquals(105, py.count.get(), "count tracks every kill (monotonic, unclamped)");
    }

    @Test
    void coolAddsCoolAddToGauge() {
        Pyramid py = newPyramid(Pyramid.PyramidMode.NORMAL); // coolAdd = 6

        py.cool();
        assertEquals(1, py.cool);
        assertEquals(6, py.gauge.get());
        assertEquals(1, py.count.get(), "cool adds 1 to count");

        py.cool();
        assertEquals(12, py.gauge.get());
        assertEquals(2, py.count.get());
    }

    @Test
    void missSubtractsMissSubClampedAtZero() {
        Pyramid py = newPyramid(Pyramid.PyramidMode.EASY); // missSub = 4
        py.gauge.set(2);
        py.count.set(7);

        py.miss();

        assertEquals(1, py.miss);
        assertEquals(0, py.gauge.get(), "gauge must not go negative");
        assertEquals(7, py.count.get(), "miss must not affect count");
    }

    @Test
    void useSkillFailsWithoutCharges() {
        Pyramid py = newPyramid(Pyramid.PyramidMode.EASY);
        assertFalse(py.useSkill());
        assertEquals(0, py.skill);
    }

    @Test
    void useSkillConsumesChargeAndBroadcastsToParticipants() {
        Character chr = Mocks.chr();
        when(chr.getMapId()).thenReturn(926010100); // must be a pyramid map or broadcast is filtered out
        when(chr.getId()).thenReturn(1);
        Pyramid py = new Pyramid(List.of(chr), Pyramid.PyramidMode.EASY, 926010100);
        py.skill = 2;

        assertTrue(py.useSkill());
        assertEquals(1, py.skill);
        assertTrue(py.useSkill());
        assertEquals(0, py.skill);
        assertFalse(py.useSkill(), "no charges left");

        verify(chr, atLeastOnce()).sendPacket(anyPacket());
    }

    private static Pyramid newPyramid(Pyramid.PyramidMode mode) {
        return new Pyramid(List.of(), mode, 926010100);
    }

    private static Character participant() {
        return Mocks.chr();
    }

    private static net.packet.Packet anyPacket() {
        return org.mockito.ArgumentMatchers.any();
    }
}
