package com.yourserver.battleroyale.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameConfig and its Builder.
 * Tests configuration creation and validation.
 */
class GameConfigTest {

    @Test
    void createDefault_createsConfigWithExpectedValues() {
        // Act
        GameConfig config = GameConfig.createDefault();

        // Assert
        assertEquals(25, config.getMinPlayers());
        assertEquals(100, config.getMaxPlayers());
        assertEquals(3600000L, config.getGameDuration());
        assertEquals(2000, config.getWorldSize());
        assertEquals(320, config.getPregameLobbyHeight());
        assertFalse(config.isTeamsEnabled());
        assertEquals(1, config.getTeamSize());
        assertEquals(8, config.getZonePhases());
        assertEquals(120, config.getZoneGracePeriod());
        assertTrue(config.isDeathmatchEnabled());
        assertEquals(3600, config.getDeathmatchTimeLimit());
    }

    @Test
    void builder_withAllParameters_createsConfig() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .minPlayers(10)
                .maxPlayers(50)
                .gameDuration(1800000L)
                .worldSize(1000)
                .pregameLobbyHeight(300)
                .teamsEnabled(true)
                .teamSize(4)
                .zonePhases(6)
                .zoneGracePeriod(60)
                .deathmatchEnabled(false)
                .deathmatchTimeLimit(1800)
                .build();

        // Assert
        assertEquals(10, config.getMinPlayers());
        assertEquals(50, config.getMaxPlayers());
        assertEquals(1800000L, config.getGameDuration());
        assertEquals(1000, config.getWorldSize());
        assertEquals(300, config.getPregameLobbyHeight());
        assertTrue(config.isTeamsEnabled());
        assertEquals(4, config.getTeamSize());
        assertEquals(6, config.getZonePhases());
        assertEquals(60, config.getZoneGracePeriod());
        assertFalse(config.isDeathmatchEnabled());
        assertEquals(1800, config.getDeathmatchTimeLimit());
    }

    @Test
    void builder_withPartialParameters_usesDefaults() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .minPlayers(15)
                .maxPlayers(75)
                .build();

        // Assert
        assertEquals(15, config.getMinPlayers());
        assertEquals(75, config.getMaxPlayers());
        // Rest should be defaults
        assertEquals(3600000L, config.getGameDuration());
        assertEquals(2000, config.getWorldSize());
    }

    @Test
    void builder_chainedCalls_worksCorrectly() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .minPlayers(20)
                .maxPlayers(60)
                .gameDuration(2000000L)
                .worldSize(1500)
                .build();

        // Assert
        assertEquals(20, config.getMinPlayers());
        assertEquals(60, config.getMaxPlayers());
        assertEquals(2000000L, config.getGameDuration());
        assertEquals(1500, config.getWorldSize());
    }

    @Test
    void builder_soloMode_hasCorrectSettings() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .teamsEnabled(false)
                .teamSize(1)
                .build();

        // Assert
        assertFalse(config.isTeamsEnabled());
        assertEquals(1, config.getTeamSize());
    }

    @Test
    void builder_teamMode_hasCorrectSettings() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .teamsEnabled(true)
                .teamSize(4)
                .build();

        // Assert
        assertTrue(config.isTeamsEnabled());
        assertEquals(4, config.getTeamSize());
    }

    @Test
    void builder_deathmatchEnabled_hasCorrectSettings() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .deathmatchEnabled(true)
                .deathmatchTimeLimit(3000)
                .build();

        // Assert
        assertTrue(config.isDeathmatchEnabled());
        assertEquals(3000, config.getDeathmatchTimeLimit());
    }

    @Test
    void builder_deathmatchDisabled_hasCorrectSettings() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .deathmatchEnabled(false)
                .build();

        // Assert
        assertFalse(config.isDeathmatchEnabled());
    }

    @Test
    void gameDuration_oneHour_correctValue() {
        // Arrange
        long oneHourInMillis = 60 * 60 * 1000;

        // Act
        GameConfig config = new GameConfig.Builder()
                .gameDuration(oneHourInMillis)
                .build();

        // Assert
        assertEquals(3600000L, config.getGameDuration());
    }

    @Test
    void zoneConfiguration_multiplePhases_correctValues() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .zonePhases(10)
                .zoneGracePeriod(180)
                .build();

        // Assert
        assertEquals(10, config.getZonePhases());
        assertEquals(180, config.getZoneGracePeriod());
    }

    @Test
    void worldSize_largeMap_correctValue() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .worldSize(5000)
                .build();

        // Assert
        assertEquals(5000, config.getWorldSize());
    }

    @Test
    void worldSize_smallMap_correctValue() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .worldSize(500)
                .build();

        // Assert
        assertEquals(500, config.getWorldSize());
    }

    @Test
    void pregameLobbyHeight_aboveBuildLimit_correctValue() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .pregameLobbyHeight(350)
                .build();

        // Assert
        assertEquals(350, config.getPregameLobbyHeight());
    }

    @Test
    void playerLimits_smallGame_correctValues() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .minPlayers(8)
                .maxPlayers(16)
                .build();

        // Assert
        assertEquals(8, config.getMinPlayers());
        assertEquals(16, config.getMaxPlayers());
    }

    @Test
    void playerLimits_largeGame_correctValues() {
        // Act
        GameConfig config = new GameConfig.Builder()
                .minPlayers(50)
                .maxPlayers(200)
                .build();

        // Assert
        assertEquals(50, config.getMinPlayers());
        assertEquals(200, config.getMaxPlayers());
    }

    @Test
    void multipleConfigs_areIndependent() {
        // Act
        GameConfig config1 = new GameConfig.Builder()
                .minPlayers(10)
                .maxPlayers(20)
                .build();

        GameConfig config2 = new GameConfig.Builder()
                .minPlayers(50)
                .maxPlayers(100)
                .build();

        // Assert
        assertEquals(10, config1.getMinPlayers());
        assertEquals(50, config2.getMinPlayers());
        assertNotEquals(config1.getMinPlayers(), config2.getMinPlayers());
    }
}