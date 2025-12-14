package com.yourserver.social.model;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FriendTest {

    @Test
    void create_withValidData_createsFriend() {
        UUID playerUuid = UUID.randomUUID();
        UUID friendUuid = UUID.randomUUID();
        String friendName = "TestFriend";

        Friend friend = Friend.create(playerUuid, friendUuid, friendName);

        assertEquals(playerUuid, friend.getPlayerUuid());
        assertEquals(friendUuid, friend.getFriendUuid());
        assertEquals(friendName, friend.getFriendName());
        assertNotNull(friend.getSince());
    }

    @Test
    void equals_withSameFriendship_returnsTrue() {
        UUID player = UUID.randomUUID();
        UUID friend1 = UUID.randomUUID();
        Instant now = Instant.now();

        Friend f1 = new Friend(player, friend1, "Friend1", now);
        Friend f2 = new Friend(player, friend1, "Friend1", now);

        assertEquals(f1, f2);
    }

    @Test
    void equals_withDifferentFriend_returnsFalse() {
        UUID player = UUID.randomUUID();
        UUID friend1 = UUID.randomUUID();
        UUID friend2 = UUID.randomUUID();
        Instant now = Instant.now();

        Friend f1 = new Friend(player, friend1, "Friend1", now);
        Friend f2 = new Friend(player, friend2, "Friend2", now);

        assertNotEquals(f1, f2);
    }
}