package com.yourserver.partition.api;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.manager.PartitionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class for other plugins to integrate with the partition system.
 *
 * This allows plugins to:
 * - Check if PartitionPlugin is available
 * - Check if a player is in a partition where the plugin is active
 * - Get partition-specific player counts
 *
 * Usage in other plugins:
 * ```java
 * PluginPartitionHelper helper = PluginPartitionHelper.create(myPlugin);
 * if (helper.shouldAffectPlayer(player)) {
 *     // Only affect players in partitions where this plugin is active
 * }
 * ```
 */
public class PluginPartitionHelper {

    private final Plugin callingPlugin;
    private final PartitionPlugin partitionPlugin;
    private final PartitionManager partitionManager;
    private final boolean available;

    private PluginPartitionHelper(Plugin callingPlugin,
                                  PartitionPlugin partitionPlugin,
                                  PartitionManager partitionManager,
                                  boolean available) {
        this.callingPlugin = callingPlugin;
        this.partitionPlugin = partitionPlugin;
        this.partitionManager = partitionManager;
        this.available = available;
    }

    /**
     * Creates a new PluginPartitionHelper for the given plugin.
     *
     * @param callingPlugin The plugin that wants partition integration
     * @return A helper instance (may have partitions disabled if PartitionPlugin not found)
     */
    @NotNull
    public static PluginPartitionHelper create(@NotNull Plugin callingPlugin) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PartitionPlugin");

        if (plugin instanceof PartitionPlugin partitionPlugin) {
            PartitionManager manager = partitionPlugin.getPartitionManager();
            callingPlugin.getLogger().info("✓ PartitionPlugin integration enabled for " +
                    callingPlugin.getName());
            callingPlugin.getLogger().info("  Plugin will only affect players in its assigned partitions");

            return new PluginPartitionHelper(callingPlugin, partitionPlugin, manager, true);
        } else {
            callingPlugin.getLogger().info("PartitionPlugin not found - " +
                    callingPlugin.getName() + " running in global mode");

            return new PluginPartitionHelper(callingPlugin, null, null, false);
        }
    }

    /**
     * Checks if PartitionPlugin is available and loaded.
     *
     * @return true if partitions are active
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Checks if the calling plugin should affect this player.
     *
     * This checks:
     * 1. Is the player in a partition?
     * 2. Is the calling plugin active in that partition?
     *
     * @param player The player to check
     * @return true if the plugin should affect this player
     */
    public boolean shouldAffectPlayer(@NotNull Player player) {
        if (!available) {
            return true; // No partitions = affect everyone
        }

        String playerPartition = partitionManager.getPartitionForPlayer(player);
        if (playerPartition == null) {
            return true; // Not in any partition = affect them
        }

        // Check if calling plugin is active in this partition
        return partitionPlugin.getPluginIsolationManager()
                .isPluginActiveInPartition(callingPlugin.getName(), playerPartition);
    }

    /**
     * Gets the partition ID for a player.
     *
     * @param player The player
     * @return The partition ID, or null if not in a partition or partitions disabled
     */
    @Nullable
    public String getPlayerPartition(@NotNull Player player) {
        if (!available) {
            return null;
        }

        return partitionManager.getPartitionForPlayer(player);
    }

    /**
     * Checks if two players are in the same partition.
     *
     * @param player1 First player
     * @param player2 Second player
     * @return true if in same partition (or if partitions disabled)
     */
    public boolean arePlayersInSamePartition(@NotNull Player player1, @NotNull Player player2) {
        if (!available) {
            return true; // No partitions = everyone is together
        }

        return partitionManager.arePlayersInSamePartition(player1, player2);
    }

    /**
     * Gets the online player count for a player's partition.
     *
     * If partitions are disabled, returns total online count.
     *
     * @param player The player
     * @return Number of players in the same partition
     */
    public int getOnlineCountForPlayer(@NotNull Player player) {
        if (!available) {
            return Bukkit.getOnlinePlayers().size();
        }

        String playerPartition = getPlayerPartition(player);
        if (playerPartition == null) {
            return Bukkit.getOnlinePlayers().size();
        }

        // Count only players in the same partition
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> {
                    String otherPartition = getPlayerPartition(p);
                    return playerPartition.equals(otherPartition);
                })
                .count();
    }

    /**
     * Gets all players in a specific partition.
     *
     * @param partitionId The partition ID
     * @return List of players in that partition (empty if partitions disabled)
     */
    @NotNull
    public java.util.List<Player> getPlayersInPartition(@NotNull String partitionId) {
        if (!available) {
            return java.util.List.of();
        }

        return partitionManager.getPlayersInPartition(partitionId);
    }
}