package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.config.BattleRoyaleConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Game Scheduler - The "heartbeat" of the Battle Royale game.
 */
public class GameScheduler {

    private final Plugin plugin;
    private final BattleRoyaleConfig config;
    private final Game game;

    private BukkitTask countdownTask;
    private BukkitTask gameTickTask;
    private BukkitTask winCheckTask;

    private int countdownSeconds;

    private boolean running = false;

    /**
     * Constructor that accepts Plugin and Config separately (test-friendly).
     */
    public GameScheduler(@NotNull Plugin plugin, @NotNull BattleRoyaleConfig config, @NotNull Game game) {
        this.plugin = plugin;
        this.config = config;
        this.game = game;
    }

    /**
     * Constructor for production use (backwards compatibility).
     */
    public GameScheduler(@NotNull com.yourserver.battleroyale.BattleRoyalePlugin plugin, @NotNull Game game) {
        this(plugin, plugin.getBRConfig(), game);
    }

    /**
     * Starts the scheduler based on current game state.
     */
    public void start() {
        if (running) {
            plugin.getLogger().warning("GameScheduler already running for game " + game.getId());
            return;
        }

        running = true;

        switch (game.getState()) {
            case STARTING -> startCountdown();
            case ACTIVE, DEATHMATCH -> {
                startGameTick();
                startWinCheck();
            }
            case WAITING, ENDING -> {}
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

    private void startCountdown() {
        countdownSeconds = config.getCountdownSeconds();

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running || game.getState() != GameState.STARTING) {
                stopCountdown();
                return;
            }

            if (countdownSeconds == 30 || countdownSeconds == 15 || countdownSeconds == 10 ||
                    countdownSeconds == 5 || countdownSeconds == 4 || countdownSeconds == 3 ||
                    countdownSeconds == 2 || countdownSeconds == 1) {

                announceCountdown(countdownSeconds);
            }

            if (countdownSeconds == 0) {
                stopCountdown();
                startGame();
                return;
            }

            countdownSeconds--;

        }, 0L, 20L);

        plugin.getLogger().info("Started countdown for game " + game.getId() + " (" + countdownSeconds + " seconds)");
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

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

    private void startGame() {
        try {
            game.setState(GameState.ACTIVE);
            startGameTick();
            startWinCheck();
            announceGameStart();
            plugin.getLogger().info("Game " + game.getId() + " started successfully!");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start game " + game.getId(), e);
        }
    }

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

    // ===== GAME TICK =====

    private void startGameTick() {
        gameTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!running || !game.getState().isInProgress()) {
                stopGameTick();
                return;
            }

            try {
                if (game.shouldTriggerDeathmatch() && game.getState() == GameState.ACTIVE) {
                    triggerDeathmatch();
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error in game tick for game " + game.getId(), e);
            }

        }, 20L, 20L);
    }

    private void stopGameTick() {
        if (gameTickTask != null) {
            gameTickTask.cancel();
            gameTickTask = null;
        }
    }

    private void triggerDeathmatch() {
        try {
            plugin.getLogger().info("Triggering deathmatch for game " + game.getId());
            game.setState(GameState.DEATHMATCH);
            announceDeathmatch();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to trigger deathmatch for game " + game.getId(), e);
        }
    }

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

    // ===== WIN CHECK =====

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

        }, 20L, 20L);
    }

    private void stopWinCheck() {
        if (winCheckTask != null) {
            winCheckTask.cancel();
            winCheckTask = null;
        }
    }

    private void checkWinCondition() {
        int aliveCount = game.getAliveCount();

        if (aliveCount <= 1) {
            endGame();
            return;
        }

        if (aliveCount == 0) {
            endGame();
        }
    }

    private void endGame() {
        try {
            plugin.getLogger().info("Ending game " + game.getId() +
                    " (alive: " + game.getAliveCount() + ")");

            stop();
            game.setState(GameState.ENDING);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to end game " + game.getId(), e);
        }
    }

    // ===== STATE MANAGEMENT =====

    public void onStateChange(@NotNull GameState newState) {
        if (!running) {
            return;
        }

        stopCountdown();
        stopGameTick();
        stopWinCheck();

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