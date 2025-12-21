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
 * Manages player scoreboards with CloudNet 4.0 awareness.
 *
 * CLOUDNET INTEGRATION:
 * - Shows service name instead of generic "server"
 * - Displays online count for current service
 * - Uses Redis for cross-service data
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
     */
    public void removeScoreboard(Player player) {
        LobbyScoreboard scoreboard = scoreboards.remove(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.remove();
        }
    }

    /**
     * Updates a specific player's scoreboard.
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
     * Parses placeholders with CloudNet awareness.
     */
    private String parsePlaceholders(String text, Player player) {
        // Get CloudNet service info
        var serviceInfo = corePlugin.getServiceInfo();

        // CloudNet-aware placeholders
        String serviceName = serviceInfo != null && serviceInfo.isCloudNetService()
                ? serviceInfo.getName()
                : "Standalone";

        String serviceGroup = serviceInfo != null && serviceInfo.isCloudNetService()
                ? serviceInfo.getGroup()
                : "N/A";

        String taskName = serviceInfo != null && serviceInfo.isCloudNetService()
                ? serviceInfo.getTask()
                : "N/A";

        // Use partition helper for accurate counts
        text = com.yourserver.lobby.util.PartitionHelper.replacePlaceholders(text, player);

        // Replace CloudNet-specific placeholders
        text = text
                .replace("{player}", player.getName())
                .replace("{rank}", getRank(player))
                .replace("{service}", serviceName)
                .replace("{service_group}", serviceGroup)
                .replace("{service_task}", taskName)
                .replace("{server}", serviceName); // Alias

        return text;
    }

    /**
     * Gets the player's rank with icon from RankDisplayManager.
     */
    private String getRank(Player player) {
        var rankManager = corePlugin.getRankDisplayManager();

        if (rankManager == null) {
            return player.isOp() ? "<red>Admin" : "<gray>Member";
        }

        // Get formatted rank with icon (MiniMessage format)
        return rankManager.getRankDisplay(player);
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