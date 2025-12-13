package com.yourserver.lobby.tablist;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.yourserver.lobby.util.PartitionHelper;

/**
 * Manages player tab list headers and footers.
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
     *
     * @param player The player
     */
    public void updatePlayer(Player player) {
        Component header = parseLines(config.getTabListConfig().getHeader(), player);
        Component footer = parseLines(config.getTabListConfig().getFooter(), player);

        player.sendPlayerListHeaderAndFooter(header, footer);

        // ALSO UPDATE TAB NAME WITH RANK ICON
        updatePlayerTabName(player);
    }

    /**
     * Updates a player's tab list name with rank icon.
     *
     * @param player The player
     */
    public void updatePlayerTabName(Player player) {
        // Get rank display manager from CorePlugin
        var corePlugin = (com.yourserver.core.CorePlugin)
                plugin.getServer().getPluginManager().getPlugin("CorePlugin");

        if (corePlugin == null) return;

        var rankManager = corePlugin.getRankDisplayManager();
        if (rankManager == null) return;

        // Set tab list name with rank icon
        String formattedName = rankManager.getFormattedPlayerName(player);
        player.setPlayerListName(formattedName);
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
     *
     * @param lines The lines
     * @param player The player
     * @return The combined component
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
     * Parses placeholders in a string.
     *
     * @param text The text with placeholders
     * @param player The player
     * @return The text with placeholders replaced
     */
    private String parsePlaceholders(String text, Player player) {
        // NEW WAY (partition-aware):
        text = PartitionHelper.replacePlaceholders(text, player);

        return text
                .replace("{player}", player.getName());
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