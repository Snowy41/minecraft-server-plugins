package com.yourserver.api.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlayerStats model.
 */
class PlayerStatsTest {

    @Test
    void constructor_withValidData_createsInstance() {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act
        PlayerStats stats = new PlayerStats(uuid, 10, 5, 50, 10, 500.0, 300.0);

        // Assert
        assertEquals(uuid, stats.getUuid());
        assertEquals(10, stats.getGamesPlayed());
        assertEquals(5, stats.getGamesWon());
        assertEquals(50, stats.getKills());
        assertEquals(10, stats.getDeaths());
        assertEquals(500.0, stats.getDamageDealt(), 0.01);
        assertEquals(300.0, stats.getDamageTaken(), 0.01);
    }

    @Test
    void constructor_withNegativeValues_setsToZero() {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act
        PlayerStats stats = new PlayerStats(uuid, -1, -1, -1, -1, -100.0, -100.0);

        // Assert
        assertEquals(0, stats.getGamesPlayed());
        assertEquals(0, stats.getGamesWon());
        assertEquals(0, stats.getKills());
        assertEquals(0, stats.getDeaths());
        assertEquals(0.0, stats.getDamageDealt(), 0.01);
        assertEquals(0.0, stats.getDamageTaken(), 0.01);
    }

    @Test
    void createDefault_createsInstanceWithZeroValues() {
        // Arrange
        UUID uuid = UUID.randomUUID();

        // Act
        PlayerStats stats = PlayerStats.createDefault(uuid);

        // Assert
        assertEquals(uuid, stats.getUuid());
        assertEquals(0, stats.getGamesPlayed());
        assertEquals(0, stats.getGamesWon());
        assertEquals(0, stats.getKills());
        assertEquals(0, stats.getDeaths());
    }

    @Test
    void getKDRatio_withNoDeaths_returnsKills() {
        // Arrange
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), 10, 5, 50, 0, 0, 0);

        // Act
        double kd = stats.getKDRatio();

        // Assert
        assertEquals(50.0, kd, 0.01);
    }

    @Test
    void getKDRatio_withDeaths_returnsCorrectRatio() {
        // Arrange
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), 10, 5, 50, 10, 0, 0);

        // Act
        double kd = stats.getKDRatio();

        // Assert
        assertEquals(5.0, kd, 0.01);
    }

    @Test
    void getWinRate_withNoGames_returnsZero() {
        // Arrange
        PlayerStats stats = PlayerStats.createDefault(UUID.randomUUID());

        // Act
        double winRate = stats.getWinRate();

        // Assert
        assertEquals(0.0, winRate, 0.01);
    }

    @Test
    void getWinRate_withGames_returnsCorrectPercentage() {
        // Arrange
        PlayerStats stats = new PlayerStats(UUID.randomUUID(), 10, 5, 0, 0, 0, 0);

        // Act
        double winRate = stats.getWinRate();

        // Assert
        assertEquals(50.0, winRate, 0.01);
    }

    @Test
    void withGamePlayed_incrementsGamesPlayed() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 0, 0, 0, 0);

        // Act
        PlayerStats updated = original.withGamePlayed();

        // Assert
        assertEquals(10, original.getGamesPlayed()); // Original unchanged
        assertEquals(11, updated.getGamesPlayed());   // New instance updated
    }

    @Test
    void withGameWon_incrementsGamesWon() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 0, 0, 0, 0);

        // Act
        PlayerStats updated = original.withGameWon();

        // Assert
        assertEquals(5, original.getGamesWon());
        assertEquals(6, updated.getGamesWon());
    }

    @Test
    void withKills_addsKills() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 50, 0, 0, 0);

        // Act
        PlayerStats updated = original.withKills(10);

        // Assert
        assertEquals(50, original.getKills());
        assertEquals(60, updated.getKills());
    }

    @Test
    void withDeath_incrementsDeaths() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 50, 10, 0, 0);

        // Act
        PlayerStats updated = original.withDeath();

        // Assert
        assertEquals(10, original.getDeaths());
        assertEquals(11, updated.getDeaths());
    }

    @Test
    void withDamageDealt_addsDamage() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 0, 0, 500.0, 0);

        // Act
        PlayerStats updated = original.withDamageDealt(100.0);

        // Assert
        assertEquals(500.0, original.getDamageDealt(), 0.01);
        assertEquals(600.0, updated.getDamageDealt(), 0.01);
    }

    @Test
    void withDamageTaken_addsDamage() {
        // Arrange
        PlayerStats original = new PlayerStats(UUID.randomUUID(), 10, 5, 0, 0, 0, 300.0);

        // Act
        PlayerStats updated = original.withDamageTaken(100.0);

        // Assert
        assertEquals(300.0, original.getDamageTaken(), 0.01);
        assertEquals(400.0, updated.getDamageTaken(), 0.01);
    }

    @Test
    void chainingMethods_worksCorrectly() {
        // Arrange
        PlayerStats original = PlayerStats.createDefault(UUID.randomUUID());

        // Act - chain multiple updates
        PlayerStats updated = original
                .withGamePlayed()
                .withGameWon()
                .withKills(5)
                .withDamageDealt(250.0);

        // Assert
        assertEquals(1, updated.getGamesPlayed());
        assertEquals(1, updated.getGamesWon());
        assertEquals(5, updated.getKills());
        assertEquals(250.0, updated.getDamageDealt(), 0.01);

        // Original unchanged
        assertEquals(0, original.getGamesPlayed());
        assertEquals(0, original.getKills());
    }
}