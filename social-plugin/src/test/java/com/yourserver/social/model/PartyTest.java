package com.yourserver.social.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PartyTest {

    @Test
    void create_createsPartyWithLeader() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader, 8);

        assertNotNull(party.getId());
        assertEquals(leader, party.getLeader());
        assertEquals(1, party.size());
        assertTrue(party.hasMember(leader));
        assertTrue(party.isLeader(leader));
    }

    @Test
    void addMember_withSpace_addsSuccessfully() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Party party = Party.create(leader, 8);

        boolean added = party.addMember(member);

        assertTrue(added);
        assertEquals(2, party.size());
        assertTrue(party.hasMember(member));
    }

    @Test
    void addMember_whenFull_returnsFalse() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader, 2);

        UUID member = UUID.randomUUID();
        party.addMember(member);

        UUID extraMember = UUID.randomUUID();
        boolean added = party.addMember(extraMember);

        assertFalse(added);
        assertEquals(2, party.size());
    }

    @Test
    void removeMember_notLeader_removesSuccessfully() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Party party = Party.create(leader, 8);
        party.addMember(member);

        boolean removed = party.removeMember(member);

        assertTrue(removed);
        assertEquals(1, party.size());
        assertFalse(party.hasMember(member));
    }

    @Test
    void removeMember_leader_returnsFalse() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader, 8);

        boolean removed = party.removeMember(leader);

        assertFalse(removed);
        assertEquals(1, party.size());
    }

    @Test
    void transferLeadership_toMember_transfersSuccessfully() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Party party = Party.create(leader, 8);
        party.addMember(member);

        boolean transferred = party.transferLeadership(member);

        assertTrue(transferred);
        assertEquals(member, party.getLeader());
        assertTrue(party.isLeader(member));
        assertFalse(party.isLeader(leader));
    }

    @Test
    void isFull_whenAtCapacity_returnsTrue() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader, 2);
        party.addMember(UUID.randomUUID());

        assertTrue(party.isFull());
    }
}