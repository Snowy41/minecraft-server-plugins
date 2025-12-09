package com.yourserver.api.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlayerData model.
 * Tests immutability, validation, and builder methods.
 */
class PlayerDataTest {

    @Test
    void constructor_withValidData_createsInstance() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        String username = "TestPlayer";
        Instant now = Instant.now();
        long playtime = 3600L;

        // Act
        PlayerData data = new PlayerData(uuid, username, now, now, playtime);

        // Assert
        assertEquals(uuid, data.getUuid());
        assertEquals(username, data.getUsername());
        assertEquals(now, data.getFirstJoin());
        assertEquals(now, data.getLastJoin());
        assertEquals(playtime, data.getPlaytimeSeconds());
    }

    @Test
    void constructor_withNullUuid_throwsException() {
        // Arrange
        Instant now = Instant.now();

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new PlayerData(null, "TestPlayer", now, now, 0L)
        );
    }

    @Test
    void constructor_withNullUsername_throwsException() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new PlayerData(uuid, null, now, now, 0L)
        );
    }

    @Test
    void constructor_withNegativePlaytime_setsToZero() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();

        // Act
        PlayerData data = new PlayerData(uuid, "TestPlayer", now, now, -100L);

        // Assert
        assertEquals(0L, data.getPlaytimeSeconds());
    }

    @Test
    void createDefault_createsInstanceWithDefaultValues() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        String username = "TestPlayer";

        // Act
        PlayerData data = PlayerData.createDefault(uuid, username);

        // Assert
        assertEquals(uuid, data.getUuid());
        assertEquals(username, data.getUsername());
        assertEquals(0L, data.getPlaytimeSeconds());
        assertNotNull(data.getFirstJoin());
        assertNotNull(data.getLastJoin());
    }

    @Test
    void withUsername_createsNewInstanceWithUpdatedUsername() {
        // Arrange
        PlayerData original = PlayerData.createDefault(UUID.randomUUID(), "OldName");

        // Act
        PlayerData updated = original.withUsername("NewName");

        // Assert
        assertEquals("OldName", original.getUsername()); // Original unchanged
        assertEquals("NewName", updated.getUsername());   // New instance updated
        assertEquals(original.getUuid(), updated.getUuid()); // Same UUID
    }

    @Test
    void withLastJoin_createsNewInstanceWithUpdatedLastJoin() {
        // Arrange
        PlayerData original = PlayerData.createDefault(UUID.randomUUID(), "TestPlayer");
        Instant newLastJoin = Instant.now().plusSeconds(3600);

        // Act
        PlayerData updated = original.withLastJoin(newLastJoin);

        // Assert
        assertNotEquals(newLastJoin, original.getLastJoin()); // Original unchanged
        assertEquals(newLastJoin, updated.getLastJoin());      // New instance updated
    }

    @Test
    void withPlaytime_createsNewInstanceWithUpdatedPlaytime() {
        // Arrange
        PlayerData original = PlayerData.createDefault(UUID.randomUUID(), "TestPlayer");
        long newPlaytime = 7200L;

        // Act
        PlayerData updated = original.withPlaytime(newPlaytime);

        // Assert
        assertEquals(0L, original.getPlaytimeSeconds()); // Original unchanged
        assertEquals(newPlaytime, updated.getPlaytimeSeconds()); // New instance updated
    }

    @Test
    void equals_withSameUuid_returnsTrue() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        PlayerData data1 = new PlayerData(uuid, "Player1", now, now, 100L);
        PlayerData data2 = new PlayerData(uuid, "Player2", now, now, 200L);

        // Act & Assert
        assertEquals(data1, data2); // Same UUID means equal
    }

    @Test
    void equals_withDifferentUuid_returnsFalse() {
        // Arrange
        Instant now = Instant.now();
        PlayerData data1 = new PlayerData(UUID.randomUUID(), "Player1", now, now, 100L);
        PlayerData data2 = new PlayerData(UUID.randomUUID(), "Player2", now, now, 100L);

        // Act & Assert
        assertNotEquals(data1, data2);
    }

    @Test
    void hashCode_withSameUuid_returnsSameHashCode() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        PlayerData data1 = new PlayerData(uuid, "Player1", now, now, 100L);
        PlayerData data2 = new PlayerData(uuid, "Player2", now, now, 200L);

        // Act & Assert
        assertEquals(data1.hashCode(), data2.hashCode());
    }

    @Test
    void toString_returnsFormattedString() {
        // Arrange
        UUID uuid = UUID.randomUUID();
        PlayerData data = PlayerData.createDefault(uuid, "TestPlayer");

        // Act
        String result = data.toString();

        // Assert
        assertTrue(result.contains("TestPlayer"));
        assertTrue(result.contains(uuid.toString()));
    }
}