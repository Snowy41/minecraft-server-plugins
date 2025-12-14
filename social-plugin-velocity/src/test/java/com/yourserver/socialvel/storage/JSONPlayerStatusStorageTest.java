package com.yourserver.socialvel.storage;

import com.yourserver.socialvel.model.PlayerStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JSONPlayerStatusStorageTest {

    private static final Logger logger = LoggerFactory.getLogger(JSONPlayerStatusStorageTest.class);

    @Test
    void updateStatus_andGet_persistsCorrectly(@TempDir File tempDir) {
        JSONPlayerStatusStorage storage = new JSONPlayerStatusStorage(tempDir, logger);

        UUID uuid = UUID.randomUUID();
        PlayerStatus status = new PlayerStatus(
                uuid,
                "lobby-1",
                PlayerStatus.Status.ONLINE,
                System.currentTimeMillis()
        );

        storage.updateStatus(status);
        PlayerStatus retrieved = storage.getStatus(uuid);

        assertNotNull(retrieved);
        assertEquals(uuid, retrieved.getUuid());
        assertEquals("lobby-1", retrieved.getServer());
        assertEquals(PlayerStatus.Status.ONLINE, retrieved.getStatus());
    }

    @Test
    void getStatus_nonExistent_returnsNull(@TempDir File tempDir) {
        JSONPlayerStatusStorage storage = new JSONPlayerStatusStorage(tempDir, logger);

        UUID uuid = UUID.randomUUID();
        PlayerStatus status = storage.getStatus(uuid);

        assertNull(status);
    }

    @Test
    void save_persistsAcrossInstances(@TempDir File tempDir) {
        UUID uuid = UUID.randomUUID();
        PlayerStatus status = new PlayerStatus(
                uuid,
                "game-1",
                PlayerStatus.Status.ONLINE,
                System.currentTimeMillis()
        );

        // First instance
        JSONPlayerStatusStorage storage1 = new JSONPlayerStatusStorage(tempDir, logger);
        storage1.updateStatus(status);
        storage1.save().join();

        // Second instance (simulates server restart)
        JSONPlayerStatusStorage storage2 = new JSONPlayerStatusStorage(tempDir, logger);
        PlayerStatus retrieved = storage2.getStatus(uuid);

        assertNotNull(retrieved);
        assertEquals(uuid, retrieved.getUuid());
        assertEquals("game-1", retrieved.getServer());
    }

    @Test
    void updateStatus_overwritesExisting(@TempDir File tempDir) {
        JSONPlayerStatusStorage storage = new JSONPlayerStatusStorage(tempDir, logger);

        UUID uuid = UUID.randomUUID();

        PlayerStatus status1 = new PlayerStatus(
                uuid, "lobby-1", PlayerStatus.Status.ONLINE, System.currentTimeMillis()
        );
        storage.updateStatus(status1);

        PlayerStatus status2 = new PlayerStatus(
                uuid, "game-1", PlayerStatus.Status.ONLINE, System.currentTimeMillis()
        );
        storage.updateStatus(status2);

        PlayerStatus retrieved = storage.getStatus(uuid);
        assertEquals("game-1", retrieved.getServer());
    }
}