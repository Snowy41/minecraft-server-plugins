package com.yourserver.battleroyale.game;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.arena.Arena;
import com.yourserver.battleroyale.player.GamePlayer;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the complete game flow.
 * Tests interaction between Game, Zone, Arena, and Loot systems.
 * Fixed with proper MockBukkit and mocked dependencies.
 */
class GameIntegrationTest {

    private ServerMock server;
    private BattleRoyalePlugin mockPlugin;
    private WorldMock world;
    private GameConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("game_world");

        // Mock the plugin (can't load full plugin in test)
        mockPlugin = mock(BattleRoyalePlugin.class);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));

        // Create test configuration
        config = GameConfig.createDefault();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void gameLifecycle_completeFlow_worksCorrectly() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        // Assert initial state
        assertEquals(GameState.WAITING, game.getState());
        assertEquals(0, game.getPlayerCount());
        assertNull(game.getArena());

        // Act - add players
        GamePlayer player1 = new GamePlayer(UUID.randomUUID(), "Player1");
        GamePlayer player2 = new GamePlayer(UUID.randomUUID(), "Player2");

        boolean added1 = game.addPlayer(player1);
        boolean added2 = game.addPlayer(player2);

        // Assert players added
        assertTrue(added1);
        assertTrue(added2);
        assertEquals(2, game.getPlayerCount());
    }

    @Test
    void gameWithArena_initializesSystemsCorrectly() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);
        Location center = new Location(world, 0, 100, 0);
        Arena arena = new Arena(
                "test-arena",
                "Test Arena",
                world,
                center,
                1000,
                Arena.ArenaConfig.createDefault()
        );

        // Act
        game.setArena(arena);

        // Assert
        assertNotNull(game.getArena());
        assertEquals("test-arena", game.getArena().getId());
        assertNotNull(game.getZoneManager());
        assertNotNull(game.getLootManager());
    }

    @Test
    void gameStateTransitions_followCorrectFlow() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        // Act & Assert - WAITING -> STARTING
        assertEquals(GameState.WAITING, game.getState());

        // Add minimum players to trigger countdown
        for (int i = 0; i < config.getMinPlayers(); i++) {
            GamePlayer player = new GamePlayer(UUID.randomUUID(), "Player" + i);
            game.addPlayer(player);
        }

        assertEquals(GameState.STARTING, game.getState());

        // Act & Assert - STARTING -> ACTIVE
        game.setState(GameState.ACTIVE);
        assertEquals(GameState.ACTIVE, game.getState());
        assertNotNull(game.getStartedAt());

        // Act & Assert - ACTIVE -> DEATHMATCH
        game.setState(GameState.DEATHMATCH);
        assertEquals(GameState.DEATHMATCH, game.getState());

        // Act & Assert - DEATHMATCH -> ENDING
        game.setState(GameState.ENDING);
        assertEquals(GameState.ENDING, game.getState());
        assertNotNull(game.getEndedAt());
    }

    @Test
    void playerElimination_updatesGameState() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        UUID player1Uuid = UUID.randomUUID();
        UUID player2Uuid = UUID.randomUUID();
        UUID player3Uuid = UUID.randomUUID();

        game.addPlayer(new GamePlayer(player1Uuid, "Player1"));
        game.addPlayer(new GamePlayer(player2Uuid, "Player2"));
        game.addPlayer(new GamePlayer(player3Uuid, "Player3"));

        game.setState(GameState.ACTIVE);

        // Act - eliminate players
        game.eliminatePlayer(player1Uuid);

        // Assert
        assertEquals(2, game.getAliveCount());
        assertFalse(game.isPlayerAlive(player1Uuid));
        assertTrue(game.isPlayerAlive(player2Uuid));
        assertTrue(game.isPlayerAlive(player3Uuid));
        assertEquals(GameState.ACTIVE, game.getState()); // Still active with 2 alive

        // Act - eliminate second player
        game.eliminatePlayer(player2Uuid);

        // Assert - game should end with 1 player remaining
        assertEquals(1, game.getAliveCount());
        assertEquals(GameState.ENDING, game.getState());
        assertEquals(player3Uuid, game.getWinner());
    }

    @Test
    void maxPlayers_preventsOverflow() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        // Act - try to add max + 1 players
        for (int i = 0; i < config.getMaxPlayers() + 1; i++) {
            GamePlayer player = new GamePlayer(UUID.randomUUID(), "Player" + i);
            game.addPlayer(player);
        }

        // Assert
        assertEquals(config.getMaxPlayers(), game.getPlayerCount());
    }

    @Test
    void playerRemoval_cleansUpCorrectly() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);
        UUID playerUuid = UUID.randomUUID();
        GamePlayer player = new GamePlayer(playerUuid, "TestPlayer");

        game.addPlayer(player);
        game.setState(GameState.ACTIVE);

        // Act
        game.removePlayer(playerUuid);

        // Assert
        assertEquals(0, game.getPlayerCount());
        assertFalse(game.hasPlayer(playerUuid));
        assertNull(game.getPlayer(playerUuid));
    }

    @Test
    void deathmatchTrigger_worksWithTimeLimit() {
        // Arrange
        GameConfig quickConfig = new GameConfig.Builder()
                .minPlayers(2)
                .maxPlayers(10)
                .gameDuration(1000L) // 1 second
                .build();

        Game game = new Game("test-game", mockPlugin, quickConfig);
        game.setState(GameState.ACTIVE);

        // Wait for time limit
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertTrue(game.shouldTriggerDeathmatch());
    }

    @Test
    void gameToString_containsRelevantInfo() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);
        game.addPlayer(new GamePlayer(UUID.randomUUID(), "Player1"));

        // Act
        String result = game.toString();

        // Assert
        assertTrue(result.contains("test-game"));
        assertTrue(result.contains("WAITING"));
        assertTrue(result.contains("1/" + config.getMaxPlayers()));
    }

    @Test
    void zoneManager_initializedWithGame() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        // Assert
        assertNotNull(game.getZoneManager());
        assertFalse(game.getZoneManager().isActive());
    }

    @Test
    void lootManager_initializedWithGame() {
        // Arrange
        Game game = new Game("test-game", mockPlugin, config);

        // Assert
        assertNotNull(game.getLootManager());
        assertEquals(0, game.getLootManager().getActiveChestCount());
    }
}