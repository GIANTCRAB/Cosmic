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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTest {

    private static Family newFamilyWithLeader() {
        Family family = new Family(200, 0); // explicit id avoids Server singleton
        FamilyEntry leader = new FamilyEntry(family, 1, "Leader", 50, Job.BEGINNER);
        family.setLeader(leader);
        family.addEntry(leader);
        return family;
    }

    private static FamilyEntry wireJunior(Family family, FamilyEntry senior, int cid, String name, int level, Job job) {
        FamilyEntry junior = new FamilyEntry(family, cid, name, level, job);
        junior.setSenior(senior, false);
        return junior;
    }

    private static Family newLeaderlessFamily(int id) {
        Family family = new Family(id, 0); // explicit id avoids Server singleton
        FamilyEntry entry = new FamilyEntry(family, 1, "Orphan", 10, Job.BEGINNER);
        family.addEntry(entry); // a member exists, but setLeader() is never called
        return family;
    }

    @Test
    void disbandClearsMembersLeaderAndEntryReferences() {
        Family family = newFamilyWithLeader();
        FamilyEntry leader = family.getLeader();
        FamilyEntry junior = wireJunior(family, leader, 2, "Junior", 20, Job.BEGINNER);

        assertEquals(2, family.getTotalMembers());

        family.disband();

        assertEquals(0, family.getTotalMembers());
        assertNull(family.getLeader());
        assertNull(family.getEntryByID(1));
        assertNull(family.getEntryByID(2));
        assertNull(leader.getFamily());
        assertNull(junior.getFamily());
        assertNull(junior.getSenior());
    }

    @Test
    void removeMemberForDeletionDisbandsWhenEntryIsLeader() {
        Family family = newFamilyWithLeader();
        FamilyEntry leader = family.getLeader();
        wireJunior(family, leader, 2, "Junior", 20, Job.BEGINNER);

        family.removeMemberForDeletion(leader);

        assertEquals(0, family.getTotalMembers());
        assertNull(family.getLeader());
    }

    @Test
    void removeMemberForDeletionDetachesWhenEntryIsNotLeader() {
        Family family = newFamilyWithLeader();
        FamilyEntry leader = family.getLeader();
        FamilyEntry junior = wireJunior(family, leader, 2, "Junior", 20, Job.BEGINNER);

        family.removeMemberForDeletion(junior);

        // junior removed, family (with leader) survives
        assertTrue(family.getTotalMembers() >= 1);
        assertTrue(family.getLeader() == leader);
        assertFalse(leader.getJuniors().contains(junior));
        assertNull(family.getEntryByID(2));
        assertNull(junior.getSenior());
    }

    @Test
    void repairLeaderlessFamiliesDisbandsOnlyLeaderless() {
        Family healthy = newFamilyWithLeader(); // id 200, has leader
        FamilyEntry healthyLeader = healthy.getLeader();
        wireJunior(healthy, healthyLeader, 2, "J", 20, Job.BEGINNER);

        Family leaderlessWithMember = newLeaderlessFamily(201);
        Family leaderlessEmpty = new Family(202, 0);

        List<Family> families = Arrays.asList(healthy, leaderlessWithMember, leaderlessEmpty);

        Set<Integer> disbanded = Family.repairLeaderlessFamilies(families);

        assertTrue(disbanded.contains(201));
        assertTrue(disbanded.contains(202));
        assertEquals(2, disbanded.size());

        // healthy family untouched
        assertTrue(healthy.getLeader() == healthyLeader);
        assertEquals(2, healthy.getTotalMembers());

        // leaderless families disbanded
        assertEquals(0, leaderlessWithMember.getTotalMembers());
        assertNull(leaderlessWithMember.getLeader());
        assertEquals(0, leaderlessEmpty.getTotalMembers());
        assertNull(leaderlessEmpty.getLeader());
    }

    @Test
    void repairLeaderlessFamiliesPreservesHealthyFamilyCounts() {
        Family family = newFamilyWithLeader();
        FamilyEntry leader = family.getLeader();
        wireJunior(family, leader, 2, "Junior", 20, Job.BEGINNER);

        Set<Integer> disbanded = Family.repairLeaderlessFamilies(List.of(family));

        assertTrue(disbanded.isEmpty());
        assertEquals(2, family.getTotalGenerations()); // leader + 1 junior generation
    }

    @Test
    void repairLeaderlessFamiliesReturnsEmptyForAllHealthy() {
        Family a = newFamilyWithLeader();
        Family b = new Family(203, 0);
        FamilyEntry leaderB = new FamilyEntry(b, 1, "LeaderB", 40, Job.BEGINNER);
        b.setLeader(leaderB);
        b.addEntry(leaderB);

        Set<Integer> disbanded = Family.repairLeaderlessFamilies(Arrays.asList(a, b));

        assertTrue(disbanded.isEmpty());
        assertTrue(a.getLeader() != null);
        assertTrue(b.getLeader() != null);
    }
}
