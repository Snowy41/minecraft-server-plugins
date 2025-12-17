package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameSchedulerTest {

    @Mock
    private BattleRoyalePlugin plugin;

    @Mock
    private Game game;

    @Mock
    private Server server;

    @Mock
    private BukkitScheduler scheduler;

    private GameScheduler gameScheduler;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getGlobal());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        gameScheduler = new GameScheduler(plugin, game);
    }

    @Test
    void testSchedulerInitializes() {
        assertNotNull(gameScheduler, "Scheduler should not be null");
    }

    @Test
    void testSchedulerStartWithStartingState() {
        when(game.getState()).thenReturn(GameState.STARTING);

        gameScheduler.start();

        // Verify scheduler was called (countdown should start)
        verify(scheduler, atLeastOnce()).runTaskTimer(
                eq(plugin),
                any(Runnable.class),
                anyLong(),
                anyLong()
        );
    }

    @Test
    void testSchedulerStartWithActiveState() {
        when(game.getState()).thenReturn(GameState.ACTIVE);

        gameScheduler.start();

        // Verify scheduler was called (game tick + win check should start)
        verify(scheduler, atLeastOnce()).runTaskTimer(
                eq(plugin),
                any(Runnable.class),
                anyLong(),
                anyLong()
        );
    }

    @Test
    void testSchedulerStop() {
        when(game.getState()).thenReturn(GameState.STARTING);
        gameScheduler.start();

        gameScheduler.stop();

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToStarting() {
        gameScheduler.onStateChange(GameState.STARTING);

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToActive() {
        gameScheduler.onStateChange(GameState.ACTIVE);

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToDeathmatch() {
        gameScheduler.onStateChange(GameState.DEATHMATCH);

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testOnStateChangeToEnding() {
        gameScheduler.onStateChange(GameState.ENDING);

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testMultipleStartStopCycles() {
        when(game.getState()).thenReturn(GameState.STARTING);

        // Start and stop multiple times
        gameScheduler.start();
        gameScheduler.stop();
        gameScheduler.start();
        gameScheduler.stop();

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testStopWithoutStart() {
        // Stop without starting first
        gameScheduler.stop();

        // Should not crash
        assertNotNull(gameScheduler);
    }

    @Test
    void testStateTransitionSequence() {
        // Test full state transition sequence
        gameScheduler.onStateChange(GameState.WAITING);
        gameScheduler.onStateChange(GameState.STARTING);
        gameScheduler.onStateChange(GameState.ACTIVE);
        gameScheduler.onStateChange(GameState.DEATHMATCH);
        gameScheduler.onStateChange(GameState.ENDING);

        // Should not crash
        assertNotNull(gameScheduler);
    }
}