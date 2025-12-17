package com.yourserver.battleroyale.player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GamePlayer.
 * Tests player state management and statistics tracking.
 */
class GamePlayerTest {

    private UUID playerUuid;
    private String playerName;
    private GamePlayer gamePlayer;

    @BeforeEach
    void setUp() {
        playerUuid = UUID.randomUUID();
        playerName = "TestPlayer";
        gamePlayer = new GamePlayer(playerUuid, playerName);
    }

    @Test
    void constructor_withValidData_createsPlayer() {
        // Assert
        assertNotNull(gamePlayer);
        assertEquals(playerUuid, gamePlayer.getUuid());
        assertEquals(playerName, gamePlayer.getName());
        assertEquals(PlayerState.WAITING, gamePlayer.getState());
        assertTrue(gamePlayer.isAlive());
    }

    @Test
    void constructor_withNullUuid_throwsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new GamePlayer(null, "TestPlayer")
        );
    }

    @Test
    void constructor_withNullName_throwsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new GamePlayer(UUID.randomUUID(), null)
        );
    }

    @Test
    void defaultStatistics_areZero() {
        // Assert
        assertEquals(0, gamePlayer.getKills());
        assertEquals(0, gamePlayer.getAssists());
        assertEquals(0.0, gamePlayer.getDamageDealt(), 0.01);
        assertEquals(0.0, gamePlayer.getDamageTaken(), 0.01);
        assertEquals(0, gamePlayer.getPlacement());
    }

    @Test
    void setState_updatesState() {
        // Act
        gamePlayer.setState(PlayerState.PLAYING);

        // Assert
        assertEquals(PlayerState.PLAYING, gamePlayer.getState());
    }

    @Test
    void setState_withNull_throwsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                gamePlayer.setState(null)
        );
    }

    @Test
    void setAlive_toFalse_updatesStateAndSurvivalTime() throws InterruptedException {
        // Arrange
        Thread.sleep(100); // Wait a bit for survival time

        // Act
        gamePlayer.setAlive(false);

        // Assert
        assertFalse(gamePlayer.isAlive());
        assertEquals(PlayerState.SPECTATING, gamePlayer.getState());
        assertTrue(gamePlayer.getSurvivalTime() > 0);
    }

    @Test
    void setAlive_toTrue_doesNotChangeState() {
        // Arrange
        gamePlayer.setState(PlayerState.PLAYING);

        // Act
        gamePlayer.setAlive(true);

        // Assert
        assertTrue(gamePlayer.isAlive());
        assertEquals(PlayerState.PLAYING, gamePlayer.getState());
    }

    @Test
    void addKill_incrementsKills() {
        // Act
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addKill();

        // Assert
        assertEquals(3, gamePlayer.getKills());
    }

    @Test
    void addAssist_incrementsAssists() {
        // Act
        gamePlayer.addAssist();
        gamePlayer.addAssist();

        // Assert
        assertEquals(2, gamePlayer.getAssists());
    }

    @Test
    void addDamageDealt_accumulatesDamage() {
        // Act
        gamePlayer.addDamageDealt(10.5);
        gamePlayer.addDamageDealt(5.3);
        gamePlayer.addDamageDealt(2.7);

        // Assert
        assertEquals(18.5, gamePlayer.getDamageDealt(), 0.01);
    }

    @Test
    void addDamageTaken_accumulatesDamage() {
        // Act
        gamePlayer.addDamageTaken(7.0);
        gamePlayer.addDamageTaken(3.5);

        // Assert
        assertEquals(10.5, gamePlayer.getDamageTaken(), 0.01);
    }

    @Test
    void setPlacement_updatesPlacement() {
        // Act
        gamePlayer.setPlacement(3);

        // Assert
        assertEquals(3, gamePlayer.getPlacement());
    }

    @Test
    void getKDAString_withNoDeaths_returnsCorrectFormat() {
        // Arrange
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addAssist();

        // Act
        String kda = gamePlayer.getKDAString();

        // Assert
        assertEquals("2/0/1", kda);
    }

    @Test
    void getKDAString_withDeath_returnsCorrectFormat() {
        // Arrange
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addAssist();
        gamePlayer.setAlive(false);

        // Act
        String kda = gamePlayer.getKDAString();

        // Assert
        assertEquals("3/1/1", kda);
    }

    @Test
    void getKDAString_withNoStats_returnsZeros() {
        // Act
        String kda = gamePlayer.getKDAString();

        // Assert
        assertEquals("0/0/0", kda);
    }

    @Test
    void equals_withSameUuid_returnsTrue() {
        // Arrange
        GamePlayer other = new GamePlayer(playerUuid, "DifferentName");

        // Act & Assert
        assertEquals(gamePlayer, other);
    }

    @Test
    void equals_withDifferentUuid_returnsFalse() {
        // Arrange
        GamePlayer other = new GamePlayer(UUID.randomUUID(), playerName);

        // Act & Assert
        assertNotEquals(gamePlayer, other);
    }

    @Test
    void hashCode_withSameUuid_returnsSameHashCode() {
        // Arrange
        GamePlayer other = new GamePlayer(playerUuid, "DifferentName");

        // Act & Assert
        assertEquals(gamePlayer.hashCode(), other.hashCode());
    }

    @Test
    void toString_returnsFormattedString() {
        // Act
        String result = gamePlayer.toString();

        // Assert
        assertTrue(result.contains(playerName));
        assertTrue(result.contains("WAITING"));
        assertTrue(result.contains("alive=true"));
    }

    @Test
    void completeGameScenario_tracksAllStats() throws InterruptedException {
        // Arrange - Simulate a complete game
        gamePlayer.setState(PlayerState.PLAYING);

        // Player gets 5 kills
        for (int i = 0; i < 5; i++) {
            gamePlayer.addKill();
        }

        // Player gets 2 assists
        gamePlayer.addAssist();
        gamePlayer.addAssist();

        // Player deals and takes damage
        gamePlayer.addDamageDealt(150.5);
        gamePlayer.addDamageTaken(89.3);

        // Wait a bit for survival time
        Thread.sleep(100);

        // Player dies and gets 3rd place
        gamePlayer.setAlive(false);
        gamePlayer.setPlacement(3);

        // Assert
        assertEquals(5, gamePlayer.getKills());
        assertEquals(2, gamePlayer.getAssists());
        assertEquals(150.5, gamePlayer.getDamageDealt(), 0.01);
        assertEquals(89.3, gamePlayer.getDamageTaken(), 0.01);
        assertEquals(3, gamePlayer.getPlacement());
        assertEquals(PlayerState.SPECTATING, gamePlayer.getState());
        assertFalse(gamePlayer.isAlive());
        assertTrue(gamePlayer.getSurvivalTime() > 0);
        assertEquals("5/1/2", gamePlayer.getKDAString());
    }
}