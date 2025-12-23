package com.yourserver.battleroyale.game;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.yourserver.battleroyale.config.BattleRoyaleConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameScheduler.
 */
class GameSchedulerTest {

    private static ServerMock server;
    private Game game;
    private TestPlugin testPlugin;
    private BattleRoyaleConfig config;
    private GameScheduler gameScheduler;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        game = mock(Game.class);

        testPlugin = MockBukkit.load(TestPlugin.class);

        config = mock(BattleRoyaleConfig.class);
        when(config.getCountdownSeconds()).thenReturn(30);
        when(config.getMinPlayers()).thenReturn(25);
        when(config.getMaxPlayers()).thenReturn(100);

        gameScheduler = new GameScheduler(testPlugin, config, game);
    }

    @AfterEach
    void tearDown() {
        if (gameScheduler != null) {
            gameScheduler.stop();
        }
    }

    @Test
    void testSchedulerInitializes() {
        assertNotNull(gameScheduler, "Scheduler should not be null");
        assertFalse(gameScheduler.isRunning());
    }

    @Test
    void testSchedulerStartWithStartingState() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();

        assertTrue(gameScheduler.isRunning());
        assertTrue(gameScheduler.getCountdownSeconds() > 0);
    }

    @Test
    void testSchedulerStartWithActiveState() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();

        assertTrue(gameScheduler.isRunning());
    }

    @Test
    void testSchedulerStop() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();
        assertTrue(gameScheduler.isRunning());

        gameScheduler.stop();

        assertFalse(gameScheduler.isRunning());
    }

    @Test
    void testOnStateChangeToStarting() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();
        gameScheduler.onStateChange(GameState.STARTING);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();
        gameScheduler.onStateChange(GameState.ACTIVE);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToDeathmatch() {
        when(game.getState()).thenReturn(GameState.DEATHMATCH);
        when(game.getId()).thenReturn("test-game");

        gameScheduler.start();
        gameScheduler.onStateChange(GameState.DEATHMATCH);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToEnding() {
        when(game.getId()).thenReturn("test-game");
        when(game.getState()).thenReturn(GameState.ACTIVE);

        gameScheduler.start();
        gameScheduler.onStateChange(GameState.ENDING);

        assertNotNull(gameScheduler);
        assertFalse(gameScheduler.isRunning());
    }

    @Test
    void testMultipleStartStopCycles() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(game.getId()).thenReturn("test-game");

        // Cycle 1
        gameScheduler.start();
        assertTrue(gameScheduler.isRunning());
        gameScheduler.stop();
        assertFalse(gameScheduler.isRunning());

        // Cycle 2
        gameScheduler.start();
        assertTrue(gameScheduler.isRunning());
        gameScheduler.stop();
        assertFalse(gameScheduler.isRunning());

        assertNotNull(gameScheduler);
    }

    @Test
    void testStopWithoutStart() {
        when(game.getId()).thenReturn("test-game");
        assertFalse(gameScheduler.isRunning());

        gameScheduler.stop();

        assertFalse(gameScheduler.isRunning());
        assertNotNull(gameScheduler);
    }

    @Test
    void testStateTransitionSequence() {
        when(game.getId()).thenReturn("test-game");

        when(game.getState()).thenReturn(GameState.WAITING);
        gameScheduler.onStateChange(GameState.WAITING);
        assertFalse(gameScheduler.isRunning());

        when(game.getState()).thenReturn(GameState.STARTING);
        gameScheduler.start();
        gameScheduler.onStateChange(GameState.STARTING);

        when(game.getState()).thenReturn(GameState.ACTIVE);
        gameScheduler.onStateChange(GameState.ACTIVE);

        when(game.getState()).thenReturn(GameState.DEATHMATCH);
        gameScheduler.onStateChange(GameState.DEATHMATCH);

        when(game.getState()).thenReturn(GameState.ENDING);
        gameScheduler.onStateChange(GameState.ENDING);
        assertFalse(gameScheduler.isRunning());

        assertNotNull(gameScheduler);
    }

    /**
     * Simple test plugin - just needs to extend JavaPlugin.
     */
    public static class TestPlugin extends JavaPlugin {
        @Override
        public void onEnable() {
            getLogger().info("TestPlugin enabled");
        }
    }
}