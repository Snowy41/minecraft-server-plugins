package com.yourserver.lobby.tablist;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manages player tab list with CloudNet 4.0 awareness.
 *
 * CLOUDNET INTEGRATION:
 * - Shows CloudNet service information
 * - Displays accurate player counts
 * - Updates rank icons in tab names
 */
public class TabListManager {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final MiniMessage miniMessage;
    private TabListUpdateTask updateTask;

    public TabListManager(LobbyPlugin plugin, LobbyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Updates a player's tab list.
     */
    public void updatePlayer(Player player) {
        Component header = parseLines(config.getTabListConfig().getHeader(), player);
        Component footer = parseLines(config.getTabListConfig().getFooter(), player);

        player.sendPlayerListHeaderAndFooter(header, footer);

        // Update tab name with rank icon
        updatePlayerTabName(player);
    }

    /**
     * Updates a player's tab list name with rank icon.
     */
    public void updatePlayerTabName(Player player) {
        var corePlugin = plugin.getCorePlugin();
        if (corePlugin == null) return;

        var rankManager = corePlugin.getRankDisplayManager();
        if (rankManager == null) return;

        // Get formatted name with rank icon (MiniMessage format)
        String formattedName = rankManager.getFormattedPlayerName(player);

        // Convert to Component and set
        Component nameComponent = miniMessage.deserialize(formattedName);
        player.playerListName(nameComponent);
    }

    /**
     * Updates all players' tab lists.
     */
    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    /**
     * Parses a list of lines into a Component.
     */
    private Component parseLines(java.util.List<String> lines, Player player) {
        if (lines.isEmpty()) {
            return Component.empty();
        }

        Component result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            String line = parsePlaceholders(lines.get(i), player);
            result = result.append(miniMessage.deserialize(line));

            if (i < lines.size() - 1) {
                result = result.append(Component.newline());
            }
        }
        return result;
    }

    /**
     * Parses placeholders with CloudNet awareness.
     */
    private String parsePlaceholders(String text, Player player) {
        // Get CloudNet service info
        var corePlugin = plugin.getCorePlugin();
        var serviceInfo = corePlugin.getServiceInfo();

        String serviceName = serviceInfo != null && serviceInfo.isCloudNetService()
                ? serviceInfo.getName()
                : "Standalone";

        String serviceGroup = serviceInfo != null && serviceInfo.isCloudNetService()
                ? serviceInfo.getGroup()
                : "N/A";

        // Use partition helper for accurate counts
        text = com.yourserver.lobby.util.PartitionHelper.replacePlaceholders(text, player);

        // Replace CloudNet-specific placeholders
        text = text
                .replace("{player}", player.getName())
                .replace("{service}", serviceName)
                .replace("{service_group}", serviceGroup)
                .replace("{server}", serviceName); // Alias

        return text;
    }

    /**
     * Starts the tab list update task.
     */
    public void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        int interval = config.getTabListConfig().getUpdateInterval();
        updateTask = new TabListUpdateTask(this);
        updateTask.runTaskTimer(plugin, 0L, interval);
    }

    /**
     * Shuts down the tab list manager.
     */
    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }
}