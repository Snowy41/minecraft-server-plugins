package com.yourserver.socialvel.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PlayerStatusTest {

    @Test
    void create_withValidData_createsStatus() {
        UUID uuid = UUID.randomUUID();
        String server = "lobby-1";
        PlayerStatus.Status status = PlayerStatus.Status.ONLINE;
        long timestamp = System.currentTimeMillis();

        PlayerStatus playerStatus = new PlayerStatus(uuid, server, status, timestamp);

        assertEquals(uuid, playerStatus.getUuid());
        assertEquals(server, playerStatus.getServer());
        assertEquals(status, playerStatus.getStatus());
        assertEquals(timestamp, playerStatus.getTimestamp());
    }

    @Test
    void status_allValues_exist() {
        assertEquals(4, PlayerStatus.Status.values().length);
        assertNotNull(PlayerStatus.Status.ONLINE);
        assertNotNull(PlayerStatus.Status.AWAY);
        assertNotNull(PlayerStatus.Status.DND);
        assertNotNull(PlayerStatus.Status.OFFLINE);
    }
}
