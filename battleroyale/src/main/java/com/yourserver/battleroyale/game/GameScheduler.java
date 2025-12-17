package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Game Scheduler - The "heartbeat" of the Battle Royale game.
 *
 * Responsibilities:
 * - Manages countdown timers (pre-game)
 * - Handles state transitions automatically
 * - Checks win conditions every second
 * - Triggers deathmatch when appropriate
 * - Coordinates with ZoneManager for zone progression
 *
 * This is the "brain" that makes the game run autonomously.
 */
public class GameScheduler {

    private final BattleRoyalePlugin plugin;
    private final Game game;

    // Scheduler tasks
    private BukkitTask countdownTask;
    private BukkitTask gameTickTask;
    private BukkitTask winCheckTask;

    // Countdown state
    private int countdownSeconds;

    // Running state
    private boolean running = false;

    public GameScheduler(@NotNull BattleRoyalePlugin plugin, @NotNull Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    /**
     * Starts the scheduler based on current game state.
     * This is the entry point for automation.
     */
    public void start() {
        if (running) {
            plugin.getLogger().warning("GameScheduler already running for game " + game.getId());
            return;
        }

        running = true;

        // Start appropriate tasks based on game state
        switch (game.getState()) {
            case STARTING -> startCountdown();
            case ACTIVE, DEATHMATCH -> {
                startGameTick();
                startWinCheck();
            }
            case WAITING, ENDING -> {} // No automation needed
        }

        plugin.getLogger().info("GameScheduler started for game " + game.getId() + " (state: " + game.getState() + ")");
    }

    /**
     * Stops all scheduler tasks.
     */
    public void stop() {
        running = false;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (gameTickTask != null) {
            gameTickTask.cancel();
            gameTickTask = null;
        }

        if (winCheckTask != null) {
            winCheckTask.cancel();
            winCheckTask = null;
        }

        plugin.getLogger().info("GameScheduler stopped for game " + game.getId());
    }

    // ===== COUNTDOWN PHASE =====

    /**
     * Starts the pre-game countdown.
     * Shows countdown messages and transitions to ACTIVE when done.
     */
    private void startCountdown() {
        countdownSeconds = plugin.getBRConfig().getCountdownSeconds();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running || game.getState() != GameState.STARTING) {
                stopCountdown();
                return;
            }

            // Countdown messages at specific intervals
            if (countdownSeconds == 30 || countdownSeconds == 15 || countdownSeconds == 10 ||
                    countdownSeconds == 5 || countdownSeconds == 4 || countdownSeconds == 3 ||
                    countdownSeconds == 2 || countdownSeconds == 1) {

                announceCountdown(countdownSeconds);
            }

            // Game starts!
            if (countdownSeconds == 0) {
                stopCountdown();
                startGame();
                return;
            }

            countdownSeconds--;

        }, 0L, 20L); // Run every second

        plugin.getLogger().info("Started countdown for game " + game.getId() + " (" + countdownSeconds + " seconds)");
    }

    /**
     * Stops the countdown task.
     */
    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    /**
     * Announces countdown to all players.
     */
    private void announceCountdown(int seconds) {
        NamedTextColor color = switch (seconds) {
            case 30, 15, 10 -> NamedTextColor.YELLOW;
            case 5, 4, 3 -> NamedTextColor.GOLD;
            case 2, 1 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };

        Sound sound = seconds <= 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_HARP;
        float pitch = seconds <= 3 ? 2.0f : 1.5f;

        Component message = Component.text("⏰ Game starting in ", NamedTextColor.GRAY)
                .append(Component.text(seconds, color, TextDecoration.BOLD))
                .append(Component.text(" second" + (seconds != 1 ? "s" : "") + "...", NamedTextColor.GRAY));

        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
    }

    /**
     * Starts the game - transitions from STARTING to ACTIVE.
     */
    private void startGame() {
        try {
            // Transition game state
            game.setState(GameState.ACTIVE);

            // Start game automation
            startGameTick();
            startWinCheck();

            // Announce game start
            announceGameStart();

            plugin.getLogger().info("Game " + game.getId() + " started successfully!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start game " + game.getId(), e);
        }
    }

    /**
     * Announces game start to all players.
     */
    private void announceGameStart() {
        Component message = Component.empty()
                .append(Component.text("⚔ ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("GAME START", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" ⚔", NamedTextColor.GOLD, TextDecoration.BOLD));

        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(Component.empty());
            player.sendMessage(message);
            player.sendMessage(Component.text("  Survive and be the last one standing!", NamedTextColor.GRAY));
            player.sendMessage(Component.empty());

            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }
    }

    // ===== GAME TICK (AUTOMATION) =====

    /**
     * Main game tick - runs every second during active gameplay.
     * Handles all periodic checks and automation.
     */
    private void startGameTick() {
        gameTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running || !game.getState().isInProgress()) {
                stopGameTick();
                return;
            }

            try {
                // Check if deathmatch should trigger
                if (game.shouldTriggerDeathmatch() && game.getState() == GameState.ACTIVE) {
                    triggerDeathmatch();
                }

                // Additional periodic tasks can go here:
                // - Update UI elements
                // - Check for inactive players
                // - Send periodic announcements

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error in game tick for game " + game.getId(), e);
            }

        }, 20L, 20L); // Run every second
    }

    /**
     * Stops the game tick task.
     */
    private void stopGameTick() {
        if (gameTickTask != null) {
            gameTickTask.cancel();
            gameTickTask = null;
        }
    }

    /**
     * Triggers deathmatch phase.
     */
    private void triggerDeathmatch() {
        try {
            plugin.getLogger().info("Triggering deathmatch for game " + game.getId());

            // Transition to deathmatch state
            game.setState(GameState.DEATHMATCH);

            // Announce to players
            announceDeathmatch();

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to trigger deathmatch for game " + game.getId(), e);
        }
    }

    /**
     * Announces deathmatch to all players.
     */
    private void announceDeathmatch() {
        Component message = Component.empty()
                .append(Component.text("⚔ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("DEATHMATCH", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ⚔", NamedTextColor.RED, TextDecoration.BOLD));

        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(Component.empty());
            player.sendMessage(message);
            player.sendMessage(Component.text("  Teleporting to arena...", NamedTextColor.GRAY));
            player.sendMessage(Component.empty());

            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }
    }

    // ===== WIN CONDITION CHECK =====

    /**
     * Checks for win conditions every second.
     * Automatically ends game when only 1 player/team remains.
     */
    private void startWinCheck() {
        winCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running || !game.getState().isInProgress()) {
                stopWinCheck();
                return;
            }

            try {
                checkWinCondition();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error checking win condition for game " + game.getId(), e);
            }

        }, 20L, 20L); // Run every second
    }

    /**
     * Stops the win check task.
     */
    private void stopWinCheck() {
        if (winCheckTask != null) {
            winCheckTask.cancel();
            winCheckTask = null;
        }
    }

    /**
     * Checks if game should end (win condition met).
     */
    private void checkWinCondition() {
        int aliveCount = game.getAliveCount();

        // Solo mode: 1 player remaining = winner
        if (aliveCount <= 1) {
            endGame();
            return;
        }

        // Team mode: 1 team remaining = winner
        // TODO: Implement team-based win checking

        // No players left? End with no winner
        if (aliveCount == 0) {
            endGame();
        }
    }

    /**
     * Ends the game - transitions to ENDING state.
     */
    private void endGame() {
        try {
            plugin.getLogger().info("Ending game " + game.getId() +
                    " (alive: " + game.getAliveCount() + ")");

            // Stop all automation
            stop();

            // Transition to ending state
            game.setState(GameState.ENDING);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to end game " + game.getId(), e);
        }
    }

    // ===== STATE MANAGEMENT =====

    /**
     * Called when game state changes.
     * Adjusts automation accordingly.
     */
    public void onStateChange(@NotNull GameState newState) {
        if (!running) {
            return;
        }

        // Stop old tasks
        stopCountdown();
        stopGameTick();
        stopWinCheck();

        // Start new tasks based on state
        switch (newState) {
            case STARTING -> startCountdown();
            case ACTIVE, DEATHMATCH -> {
                startGameTick();
                startWinCheck();
            }
            case ENDING -> stop();
        }
    }

    // ===== GETTERS =====

    public boolean isRunning() {
        return running;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }
}