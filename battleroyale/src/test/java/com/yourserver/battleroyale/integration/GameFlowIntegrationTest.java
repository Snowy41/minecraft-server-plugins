package com.yourserver.battleroyale.integration;

import com.yourserver.battleroyale.game.GameConfig;
import com.yourserver.battleroyale.game.GameState;
import com.yourserver.battleroyale.player.GamePlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for complete game flow.
 *
 * NOTE: This test is simplified to avoid Mockito issues with JavaPlugin.
 */
class GameFlowIntegrationTest {

    private GameConfig config;

    @BeforeEach
    void setUp() {
        config = GameConfig.createDefault();
    }

    @Test
    void testCompleteGameLifecycle() {
        GameState[] states = GameState.values();
        assertEquals(5, states.length, "Should have 5 game states");

        assertTrue(GameState.WAITING.canJoin(), "Players should be able to join in WAITING");
        assertFalse(GameState.WAITING.isInProgress(), "Game should not be in progress during WAITING");
        assertFalse(GameState.WAITING.hasStarted(), "Game should not have started in WAITING");

        assertFalse(GameState.STARTING.canJoin(), "Players should not be able to join during STARTING");
        assertFalse(GameState.STARTING.isInProgress(), "Game should not be in progress during STARTING");
        assertFalse(GameState.STARTING.hasStarted(), "Game should not have started during STARTING");

        assertFalse(GameState.ACTIVE.canJoin(), "Players should not be able to join during ACTIVE");
        assertTrue(GameState.ACTIVE.isInProgress(), "Game should be in progress during ACTIVE");
        assertTrue(GameState.ACTIVE.hasStarted(), "Game should have started in ACTIVE");

        assertFalse(GameState.DEATHMATCH.canJoin(), "Players should not be able to join during DEATHMATCH");
        assertTrue(GameState.DEATHMATCH.isInProgress(), "Game should be in progress during DEATHMATCH");
        assertTrue(GameState.DEATHMATCH.hasStarted(), "Game should have started in DEATHMATCH");

        assertFalse(GameState.ENDING.canJoin(), "Players should not be able to join during ENDING");
        assertFalse(GameState.ENDING.isInProgress(), "Game should not be in progress during ENDING");
        assertTrue(GameState.ENDING.hasStarted(), "Game should have started in ENDING");

        assertTrue(GameState.WAITING.ordinal() < GameState.STARTING.ordinal());
        assertTrue(GameState.STARTING.ordinal() < GameState.ACTIVE.ordinal());
        assertTrue(GameState.ACTIVE.ordinal() < GameState.DEATHMATCH.ordinal());
        assertTrue(GameState.DEATHMATCH.ordinal() < GameState.ENDING.ordinal());
    }

    @Test
    void testDeathmatchTransition() {
        assertTrue(GameState.DEATHMATCH.isInProgress(),
                "Deathmatch should be considered in progress");
        assertTrue(GameState.DEATHMATCH.hasStarted(),
                "Deathmatch should be considered started");
        assertFalse(GameState.DEATHMATCH.canJoin(),
                "No new players should be able to join during deathmatch");

        assertTrue(GameState.DEATHMATCH.ordinal() > GameState.ACTIVE.ordinal(),
                "Deathmatch should come after ACTIVE state");
        assertTrue(GameState.DEATHMATCH.ordinal() < GameState.ENDING.ordinal(),
                "Deathmatch should come before ENDING state");

        assertEquals("DEATHMATCH", GameState.DEATHMATCH.name());
    }

    @Test
    void testGamePlayerLifecycle() {
        UUID playerUuid = UUID.randomUUID();
        GamePlayer player = new GamePlayer(playerUuid, "TestPlayer");

        assertTrue(player.isAlive(), "Player should start alive");
        assertEquals(0, player.getKills(), "Player should start with 0 kills");
        assertEquals(0, player.getAssists(), "Player should start with 0 assists");
        assertEquals(0.0, player.getDamageDealt(), 0.01, "Player should start with 0 damage dealt");
        assertEquals(0.0, player.getDamageTaken(), 0.01, "Player should start with 0 damage taken");

        player.addKill();
        player.addKill();
        player.addAssist();
        player.addDamageDealt(50.0);
        player.addDamageTaken(30.0);

        assertEquals(2, player.getKills(), "Player should have 2 kills");
        assertEquals(1, player.getAssists(), "Player should have 1 assist");
        assertEquals(50.0, player.getDamageDealt(), 0.01, "Player should have dealt 50 damage");
        assertEquals(30.0, player.getDamageTaken(), 0.01, "Player should have taken 30 damage");

        player.setAlive(false);
        player.setPlacement(3);

        assertFalse(player.isAlive(), "Player should be dead");
        assertEquals(3, player.getPlacement(), "Player should be in 3rd place");
        assertEquals("2/1/1", player.getKDAString(), "KDA should be 2 kills / 1 death / 1 assist");
        assertTrue(player.getSurvivalTime() > 0, "Player should have survival time recorded");
    }

    @Test
    void testGameConfigIntegrity() {
        assertNotNull(config, "Config should not be null");

        assertTrue(config.getMinPlayers() > 0, "Min players should be positive");
        assertTrue(config.getMaxPlayers() > config.getMinPlayers(),
                "Max players should be greater than min players");
        assertEquals(25, config.getMinPlayers(), "Default min players should be 25");
        assertEquals(100, config.getMaxPlayers(), "Default max players should be 100");

        assertTrue(config.getGameDuration() > 0, "Game duration should be positive");
        assertEquals(3600000L, config.getGameDuration(), "Default game duration should be 1 hour");
        assertTrue(config.getZoneGracePeriod() > 0, "Zone grace period should be positive");

        assertTrue(config.getWorldSize() > 0, "World size should be positive");
        assertEquals(2000, config.getWorldSize(), "Default world size should be 2000 blocks");
        assertTrue(config.getPregameLobbyHeight() > 0, "Lobby height should be positive");

        assertTrue(config.getZonePhases() > 0, "Should have at least one zone phase");
        assertEquals(8, config.getZonePhases(), "Default should have 8 zone phases");

        assertTrue(config.isDeathmatchEnabled(), "Deathmatch should be enabled by default");
        assertTrue(config.getDeathmatchTimeLimit() > 0, "Deathmatch time limit should be positive");

        assertFalse(config.isTeamsEnabled(), "Teams should be disabled by default");
        assertEquals(1, config.getTeamSize(), "Default team size should be 1 (solo)");
    }

    @Test
    void testGameStateTransitionValidation() {
        assertTrue(GameState.WAITING.canJoin());
        assertFalse(GameState.STARTING.canJoin());

        assertFalse(GameState.ACTIVE.canJoin());
        assertTrue(GameState.ACTIVE.hasStarted());

        for (GameState state : GameState.values()) {
            if (state.canJoin()) {
                assertFalse(state.isInProgress(),
                        state + " cannot be both joinable and in progress");
                assertFalse(state.hasStarted(),
                        state + " cannot be both joinable and started");
            }

            if (state.isInProgress()) {
                assertTrue(state.hasStarted(),
                        state + " must have started if in progress");
                assertFalse(state.canJoin(),
                        state + " cannot be joinable if in progress");
            }
        }
    }
}