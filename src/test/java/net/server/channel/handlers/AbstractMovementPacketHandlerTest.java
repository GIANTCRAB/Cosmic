/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.server.channel.handlers;

import org.junit.jupiter.api.Test;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;
import server.movement.RelativeLifeMovement;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ground-walk detector that gates Nett's Pyramid readiness. A "ground walk" is
 * movement command 0 ({@link AbsoluteLifeMovement}); it is the signal that the player has genuine
 * control and the fieldType=23 field has loaded, so massacre UI init can be sent without crashing
 * the v83 client.
 */
class AbstractMovementPacketHandlerTest {

    @Test
    void detectsCommandZeroAbsoluteMove() {
        LifeMovementFragment walk = new AbsoluteLifeMovement(0, new Point(0, 0), 0, 0);
        assertTrue(AbstractMovementPacketHandler.hasGroundWalk(List.of(walk)));
    }

    @Test
    void ignoresFloatJumpAndMixedNonWalks() {
        // commands 5/17 share AbsoluteLifeMovement but are floats, not walks; 1 is a jump.
        LifeMovementFragment floatMove = new AbsoluteLifeMovement(5, new Point(0, 0), 0, 0);
        LifeMovementFragment jump = new RelativeLifeMovement(1, new Point(0, 0), 0, 0);

        assertFalse(AbstractMovementPacketHandler.hasGroundWalk(List.of(floatMove)));
        assertFalse(AbstractMovementPacketHandler.hasGroundWalk(List.of(jump)));
        assertFalse(AbstractMovementPacketHandler.hasGroundWalk(List.of(floatMove, jump)));
    }

    @Test
    void findsWalkAmongOtherMoves() {
        LifeMovementFragment jump = new RelativeLifeMovement(1, new Point(0, 0), 0, 0);
        LifeMovementFragment walk = new AbsoluteLifeMovement(0, new Point(10, 0), 0, 0);
        assertTrue(AbstractMovementPacketHandler.hasGroundWalk(List.of(jump, walk)));
    }

    @Test
    void falseForEmpty() {
        assertFalse(AbstractMovementPacketHandler.hasGroundWalk(List.of()));
    }
}
