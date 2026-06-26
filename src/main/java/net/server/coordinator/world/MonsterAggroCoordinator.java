/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.server.coordinator.world;

import client.Character;
import server.life.Monster;

import java.util.List;

/**
 * Contract for resolving which player a monster targets (aggro).
 * <p>
 * The {@code controller} of a monster is the player whose client animates and
 * is targeted by the mob; implementations decide how that controller is chosen
 * and how it changes over the monster's lifetime.
 *
 * @author Ronan
 * @see DefaultMonsterAggroCoordinator
 */
public interface MonsterAggroCoordinator {
    void addAggroDamage(Monster mob, int cid, int damage);

    boolean isLeadingCharacterAggro(Monster mob, Character player);

    void removeAggroEntries(Monster mob);

    void addPuppetAggro(Character player);

    void removePuppetAggro(Integer cid);

    List<Integer> getPuppetAggroList();

    void startAggroCoordinator();

    void stopAggroCoordinator();

    void dispose();
}
