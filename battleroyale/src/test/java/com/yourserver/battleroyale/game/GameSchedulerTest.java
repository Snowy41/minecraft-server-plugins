package com.yourserver.battleroyale.game;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.config.BattleRoyaleConfig;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameScheduler.
 *
 * SOLUTION: Use MockBukkit + extend BattleRoyalePlugin with a test stub
 * that overrides the methods GameScheduler uses, avoiding CorePlugin dependency.
 */
class GameSchedulerTest {

    private static ServerMock server;
    private Game game;
    private BattleRoyalePlugin plugin;
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

        // Create a test plugin that extends BattleRoyalePlugin
        // but overrides methods to avoid CorePlugin dependency
        plugin = MockBukkit.load(TestBattleRoyalePlugin.class);

        gameScheduler = new GameScheduler(plugin, game);
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

        gameScheduler.start();

        assertTrue(gameScheduler.isRunning());
        assertTrue(gameScheduler.getCountdownSeconds() > 0);
    }

    @Test
    void testSchedulerStartWithActiveState() {
        when(game.getState()).thenReturn(GameState.ACTIVE);

        gameScheduler.start();

        assertTrue(gameScheduler.isRunning());
    }

    @Test
    void testSchedulerStop() {
        when(game.getState()).thenReturn(GameState.STARTING);
        gameScheduler.start();
        assertTrue(gameScheduler.isRunning());

        gameScheduler.stop();

        assertFalse(gameScheduler.isRunning());
    }

    @Test
    void testOnStateChangeToStarting() {
        when(game.getState()).thenReturn(GameState.STARTING);

        gameScheduler.onStateChange(GameState.STARTING);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);

        gameScheduler.onStateChange(GameState.ACTIVE);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToDeathmatch() {
        when(game.getState()).thenReturn(GameState.DEATHMATCH);

        gameScheduler.onStateChange(GameState.DEATHMATCH);

        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToEnding() {
        gameScheduler.onStateChange(GameState.ENDING);

        assertNotNull(gameScheduler);
        assertFalse(gameScheduler.isRunning());
    }

    @Test
    void testMultipleStartStopCycles() {
        when(game.getState()).thenReturn(GameState.STARTING);

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
        assertFalse(gameScheduler.isRunning());

        gameScheduler.stop();

        assertFalse(gameScheduler.isRunning());
        assertNotNull(gameScheduler);
    }

    @Test
    void testStateTransitionSequence() {
        // WAITING
        when(game.getState()).thenReturn(GameState.WAITING);
        gameScheduler.onStateChange(GameState.WAITING);
        assertFalse(gameScheduler.isRunning());

        // STARTING
        when(game.getState()).thenReturn(GameState.STARTING);
        gameScheduler.onStateChange(GameState.STARTING);

        // ACTIVE
        when(game.getState()).thenReturn(GameState.ACTIVE);
        gameScheduler.onStateChange(GameState.ACTIVE);

        // DEATHMATCH
        when(game.getState()).thenReturn(GameState.DEATHMATCH);
        gameScheduler.onStateChange(GameState.DEATHMATCH);

        // ENDING
        when(game.getState()).thenReturn(GameState.ENDING);
        gameScheduler.onStateChange(GameState.ENDING);
        assertFalse(gameScheduler.isRunning());

        assertNotNull(gameScheduler);
    }

    /**
     * Test implementation of BattleRoyalePlugin.
     *
     * This extends the real BattleRoyalePlugin but overrides methods
     * to avoid the CorePlugin dependency and prevent onEnable() from running.
     */
    public static class TestBattleRoyalePlugin extends BattleRoyalePlugin {
        private final BattleRoyaleConfig config;

        public TestBattleRoyalePlugin() {
            // Create a mock config with default values
            this.config = mock(BattleRoyaleConfig.class);
            when(config.getCountdownSeconds()).thenReturn(30);
            when(config.getMinPlayers()).thenReturn(25);
            when(config.getMaxPlayers()).thenReturn(100);
        }

        @Override
        public void onLoad() {
            // Skip normal onLoad to avoid CorePlugin dependency
        }

        @Override
        public void onEnable() {
            // Skip normal onEnable to avoid CorePlugin dependency
            getLogger().info("Test plugin enabled (skipping normal initialization)");
        }

        @Override
        public void onDisable() {
            // Skip normal onDisable
        }

        @Override
        public BattleRoyaleConfig getBRConfig() {
            return config;
        }

        // All other methods will use the parent class implementation
        // or MockBukkit's defaults, which is fine for testing GameScheduler
    }
}