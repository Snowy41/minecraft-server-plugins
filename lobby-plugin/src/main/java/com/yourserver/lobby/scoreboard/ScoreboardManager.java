package com.yourserver.lobby.scoreboard;

import com.yourserver.core.CorePlugin;
import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player scoreboards and updates.
 */
public class ScoreboardManager {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final CorePlugin corePlugin;
    private final MiniMessage miniMessage;
    private final Map<UUID, LobbyScoreboard> scoreboards;
    private ScoreboardUpdateTask updateTask;

    public ScoreboardManager(LobbyPlugin plugin, LobbyConfig config, CorePlugin corePlugin) {
        this.plugin = plugin;
        this.config = config;
        this.corePlugin = corePlugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.scoreboards = new ConcurrentHashMap<>();
    }

    /**
     * Creates a scoreboard for a player.
     *
     * @param player The player
     */
    public void createScoreboard(Player player) {
        Component title = miniMessage.deserialize(config.getScoreboardConfig().getTitle());
        LobbyScoreboard scoreboard = new LobbyScoreboard(player, title);
        scoreboards.put(player.getUniqueId(), scoreboard);

        // Initial update
        updateScoreboard(player);
    }

    /**
     * Removes a player's scoreboard.
     *
     * @param player The player
     */
    public void removeScoreboard(Player player) {
        LobbyScoreboard scoreboard = scoreboards.remove(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.remove();
        }
    }

    /**
     * Updates a specific player's scoreboard.
     *
     * @param player The player
     */
    public void updateScoreboard(Player player) {
        LobbyScoreboard scoreboard = scoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            return;
        }

        // Parse lines with placeholders
        List<Component> lines = config.getScoreboardConfig().getLines().stream()
                .map(line -> parsePlaceholders(line, player))
                .map(miniMessage::deserialize)
                .toList();

        scoreboard.updateLines(lines);
    }

    /**
     * Updates all player scoreboards.
     */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }

    /**
     * Parses placeholders in a string.
     *
     * @param text The text with placeholders
     * @param player The player
     * @return The text with placeholders replaced
     */
    private String parsePlaceholders(String text, Player player) {
        return text
                .replace("{player}", player.getName())
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max_players}", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("{rank}", getRank(player));
    }

    /**
     * Gets the player's rank (from LuckPerms if available).
     *
     * @param player The player
     * @return The rank name
     */
    private String getRank(Player player) {
        // Try to get rank from LuckPerms (if available)
        // For now, return default
        if (player.isOp()) {
            return "Admin";
        }
        return "Member";
    }

    /**
     * Starts the scoreboard update task.
     */
    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        int interval = config.getScoreboardConfig().getUpdateInterval();
        updateTask = new ScoreboardUpdateTask(this);
        updateTask.runTaskTimer(plugin, 0L, interval);
    }

    /**
     * Shuts down the scoreboard manager.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        // Remove all scoreboards
        scoreboards.values().forEach(LobbyScoreboard::remove);
        scoreboards.clear();
    }
}