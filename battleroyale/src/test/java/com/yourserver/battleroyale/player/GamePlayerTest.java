package com.yourserver.battleroyale.player;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GamePlayer
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
        assertNotNull(gamePlayer);
        assertEquals(playerUuid, gamePlayer.getUuid());
        assertEquals(playerName, gamePlayer.getName());
        assertEquals(PlayerState.WAITING, gamePlayer.getState());
        assertTrue(gamePlayer.isAlive());
    }

    @Test
    void constructor_withNullUuid_throwsException() {
        assertThrows(NullPointerException.class, () ->
                new GamePlayer(null, "TestPlayer")
        );
    }

    @Test
    void constructor_withNullName_throwsException() {
        assertThrows(NullPointerException.class, () ->
                new GamePlayer(UUID.randomUUID(), null)
        );
    }

    @Test
    void defaultStatistics_areZero() {
        assertEquals(0, gamePlayer.getKills());
        assertEquals(0, gamePlayer.getAssists());
        assertEquals(0.0, gamePlayer.getDamageDealt(), 0.01);
        assertEquals(0.0, gamePlayer.getDamageTaken(), 0.01);
        assertEquals(0, gamePlayer.getPlacement());
    }

    @Test
    void setState_updatesState() {
        gamePlayer.setState(PlayerState.PLAYING);

        assertEquals(PlayerState.PLAYING, gamePlayer.getState());
    }

    @Test
    void setState_withNull_throwsException() {
        assertThrows(NullPointerException.class, () ->
                gamePlayer.setState(null)
        );
    }

    @Test
    void setAlive_toFalse_updatesStateAndSurvivalTime() throws InterruptedException {
        Thread.sleep(100);

        gamePlayer.setAlive(false);

        assertFalse(gamePlayer.isAlive());
        assertEquals(PlayerState.SPECTATING, gamePlayer.getState());
        assertTrue(gamePlayer.getSurvivalTime() > 0);
    }

    @Test
    void setAlive_toTrue_doesNotChangeState() {
        gamePlayer.setState(PlayerState.PLAYING);

        gamePlayer.setAlive(true);

        assertTrue(gamePlayer.isAlive());
        assertEquals(PlayerState.PLAYING, gamePlayer.getState());
    }

    @Test
    void addKill_incrementsKills() {
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addKill();

        assertEquals(3, gamePlayer.getKills());
    }

    @Test
    void addAssist_incrementsAssists() {
        gamePlayer.addAssist();
        gamePlayer.addAssist();

        assertEquals(2, gamePlayer.getAssists());
    }

    @Test
    void addDamageDealt_accumulatesDamage() {
        gamePlayer.addDamageDealt(10.5);
        gamePlayer.addDamageDealt(5.3);
        gamePlayer.addDamageDealt(2.7);

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
        gamePlayer.setPlacement(3);

        assertEquals(3, gamePlayer.getPlacement());
    }

    @Test
    void getKDAString_withNoDeaths_returnsCorrectFormat() {
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addAssist();

        String kda = gamePlayer.getKDAString();

        assertEquals("2/0/1", kda);
    }

    @Test
    void getKDAString_withDeath_returnsCorrectFormat() {
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addKill();
        gamePlayer.addAssist();
        gamePlayer.setAlive(false);

        String kda = gamePlayer.getKDAString();

        assertEquals("3/1/1", kda);
    }

    @Test
    void getKDAString_withNoStats_returnsZeros() {
        String kda = gamePlayer.getKDAString();

        assertEquals("0/0/0", kda);
    }

    @Test
    void equals_withSameUuid_returnsTrue() {
        GamePlayer other = new GamePlayer(playerUuid, "DifferentName");

        assertEquals(gamePlayer, other);
    }

    @Test
    void equals_withDifferentUuid_returnsFalse() {
        GamePlayer other = new GamePlayer(UUID.randomUUID(), playerName);

        assertNotEquals(gamePlayer, other);
    }

    @Test
    void hashCode_withSameUuid_returnsSameHashCode() {
        GamePlayer other = new GamePlayer(playerUuid, "DifferentName");

        assertEquals(gamePlayer.hashCode(), other.hashCode());
    }

    @Test
    void toString_returnsFormattedString() {
        String result = gamePlayer.toString();

        assertTrue(result.contains(playerName));
        assertTrue(result.contains("WAITING"));
        assertTrue(result.contains("alive=true"));
    }

    @Test
    void completeGameScenario_tracksAllStats() throws InterruptedException {
        gamePlayer.setState(PlayerState.PLAYING);

        for (int i = 0; i < 5; i++) {
            gamePlayer.addKill();
        }

        gamePlayer.addAssist();
        gamePlayer.addAssist();

        gamePlayer.addDamageDealt(150.5);
        gamePlayer.addDamageTaken(89.3);

        Thread.sleep(100);

        gamePlayer.setAlive(false);
        gamePlayer.setPlacement(3);

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