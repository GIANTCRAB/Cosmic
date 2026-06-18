/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyEntryTest {

    private static Family newFamily() {
        Family family = new Family(100, 0); // explicit id avoids Server singleton
        return family;
    }

    private static FamilyEntry wireJunior(Family family, FamilyEntry senior, int cid, String name, int level, Job job) {
        FamilyEntry junior = new FamilyEntry(family, cid, name, level, job);
        junior.setSenior(senior, false); // save=false -> no DB write, wires both directions + addEntry
        return junior;
    }

    @Test
    void detachLeafJuniorRemovesEntryAndUnlinksSenior() {
        Family family = newFamily();
        FamilyEntry leader = new FamilyEntry(family, 1, "Leader", 50, Job.BEGINNER);
        family.setLeader(leader);
        family.addEntry(leader);

        FamilyEntry junior = wireJunior(family, leader, 2, "Junior", 20, Job.BEGINNER);

        assertTrue(leader.getJuniors().contains(junior));
        assertTrue(family.getEntryByID(2) == junior);

        junior.detachForCharacterDeletion();

        assertFalse(leader.getJuniors().contains(junior));
        assertNull(junior.getSenior());
        assertNull(family.getEntryByID(2));
    }

    @Test
    void detachMiddleNodeOrphansItsJuniors() {
        Family family = newFamily();
        FamilyEntry leader = new FamilyEntry(family, 1, "Leader", 50, Job.BEGINNER);
        family.setLeader(leader);
        family.addEntry(leader);

        FamilyEntry mid = wireJunior(family, leader, 2, "Mid", 30, Job.BEGINNER);
        FamilyEntry subJunior = wireJunior(family, mid, 3, "Sub", 10, Job.BEGINNER);

        assertTrue(mid.getJuniors().contains(subJunior));
        assertTrue(subJunior.getSenior() == mid);

        mid.detachForCharacterDeletion();

        assertNull(mid.getSenior());
        assertFalse(leader.getJuniors().contains(mid));
        assertNull(family.getEntryByID(2));

        // sub-junior is orphaned but remains a (now detached) family member
        assertNull(subJunior.getSenior());
        assertFalse(mid.getJuniors().contains(subJunior));
    }

    @Test
    void detachNodeWithTwoJuniorsOrphansBoth() {
        Family family = newFamily();
        FamilyEntry leader = new FamilyEntry(family, 1, "Leader", 50, Job.BEGINNER);
        family.setLeader(leader);
        family.addEntry(leader);

        FamilyEntry mid = wireJunior(family, leader, 2, "Mid", 30, Job.BEGINNER);
        FamilyEntry j1 = wireJunior(family, mid, 3, "J1", 10, Job.BEGINNER);
        FamilyEntry j2 = wireJunior(family, mid, 4, "J2", 12, Job.BEGINNER);

        mid.detachForCharacterDeletion();

        assertNull(j1.getSenior());
        assertNull(j2.getSenior());
        assertNull(family.getEntryByID(2));
    }
}
