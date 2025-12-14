package com.yourserver.social.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ClanTest {

    @Test
    void create_createsClanWithOwner() {
        UUID owner = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);

        assertNotNull(clan.getId());
        assertEquals("TestClan", clan.getName());
        assertEquals("TEST", clan.getTag());
        assertEquals(owner, clan.getOwner());
        assertEquals(1, clan.size());
        assertTrue(clan.isOwner(owner));
        assertEquals(Clan.ClanRank.OWNER, clan.getRank(owner));
    }

    @Test
    void addMember_withSpace_addsSuccessfully() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);

        boolean added = clan.addMember(member, Clan.ClanRank.MEMBER);

        assertTrue(added);
        assertEquals(2, clan.size());
        assertTrue(clan.hasMember(member));
        assertEquals(Clan.ClanRank.MEMBER, clan.getRank(member));
    }

    @Test
    void removeMember_notOwner_removesSuccessfully() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);
        clan.addMember(member, Clan.ClanRank.MEMBER);

        boolean removed = clan.removeMember(member);

        assertTrue(removed);
        assertEquals(1, clan.size());
        assertFalse(clan.hasMember(member));
    }

    @Test
    void removeMember_owner_returnsFalse() {
        UUID owner = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);

        boolean removed = clan.removeMember(owner);

        assertFalse(removed);
        assertEquals(1, clan.size());
    }

    @Test
    void setRank_validMember_updatesRank() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);
        clan.addMember(member, Clan.ClanRank.MEMBER);

        boolean updated = clan.setRank(member, Clan.ClanRank.ADMIN);

        assertTrue(updated);
        assertEquals(Clan.ClanRank.ADMIN, clan.getRank(member));
    }

    @Test
    void transferOwnership_toMember_transfersSuccessfully() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);
        clan.addMember(member, Clan.ClanRank.MEMBER);

        boolean transferred = clan.transferOwnership(member);

        assertTrue(transferred);
        assertEquals(member, clan.getOwner());
        assertTrue(clan.isOwner(member));
        assertEquals(Clan.ClanRank.OWNER, clan.getRank(member));
        assertEquals(Clan.ClanRank.ADMIN, clan.getRank(owner));
    }

    @Test
    void hasPermission_owner_hasAllPermissions() {
        UUID owner = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);

        assertTrue(clan.hasPermission(owner, "invite"));
        assertTrue(clan.hasPermission(owner, "kick"));
        assertTrue(clan.hasPermission(owner, "promote"));
        assertTrue(clan.hasPermission(owner, "anything"));
    }

    @Test
    void hasPermission_admin_hasLimitedPermissions() {
        UUID owner = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);
        clan.addMember(admin, Clan.ClanRank.ADMIN);

        assertTrue(clan.hasPermission(admin, "invite"));
        assertTrue(clan.hasPermission(admin, "kick"));
        assertTrue(clan.hasPermission(admin, "chat"));
    }

    @Test
    void hasPermission_member_hasOnlyChatPermission() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Clan clan = Clan.create("TestClan", "TEST", owner, 50);
        clan.addMember(member, Clan.ClanRank.MEMBER);

        assertTrue(clan.hasPermission(member, "chat"));
        assertFalse(clan.hasPermission(member, "invite"));
        assertFalse(clan.hasPermission(member, "kick"));
    }
}