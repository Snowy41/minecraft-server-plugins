package com.yourserver.partition.listener;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.isolation.PartitionIsolationSystem;
import com.yourserver.partition.manager.PartitionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.Plugin;

/**
 * Listens for partition changes and notifies other plugins dynamically.
 * Other plugins (like LobbyPlugin) can listen for partition events
 * without importing PartitionPlugin classes.
 *
 * Communication is one-way: PartitionPlugin → Other Plugins
 * This avoids circular dependencies.
 */
public class PartitionAwarePluginListener implements Listener {

    private final PartitionPlugin plugin;
    private final PartitionManager partitionManager;
    private final PartitionIsolationSystem isolationSystem;

    public PartitionAwarePluginListener(PartitionPlugin plugin,
                                        PartitionManager partitionManager,
                                        PartitionIsolationSystem isolationSystem) {
        this.plugin = plugin;
        this.partitionManager = partitionManager;
        this.isolationSystem = isolationSystem;
    }

    /**
     * When a player changes worlds (and potentially partitions),
     * notify other plugins so they can update their UIs.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String oldPartition = partitionManager.getPartitionForWorld(event.getFrom().getName());
        String newPartition = partitionManager.getPartitionForPlayer(player);

        // Only notify if partition actually changed
        if (oldPartition != null && !oldPartition.equals(newPartition)) {
            plugin.getLogger().fine("Player " + player.getName() +
                    " changed partitions: " + oldPartition + " → " + newPartition);

            // Update isolation first
            Bukkit.getScheduler().runTask(plugin, () -> {
                isolationSystem.updatePartitionVisibility(newPartition);
                if (oldPartition != null) {
                    isolationSystem.updatePartitionVisibility(oldPartition);
                }

                // Notify other plugins via custom event
                notifyPluginsOfPartitionChange(player, oldPartition, newPartition);
            });
        }
    }

    /**
     * Notifies other plugins that a player changed partitions.
     * Plugins can listen for this to update scoreboards, tab lists, etc.
     *
     * Example in LobbyPlugin:
     * ```java
     * @EventHandler
     * public void onPartitionChange(PartitionChangeEvent event) {
     *     // Update scoreboard with new partition-specific data
     *     updateScoreboard(event.getPlayer());
     * }
     * ```
     */
    private void notifyPluginsOfPartitionChange(Player player, String oldPartition, String newPartition) {
        // Call a method on LobbyPlugin if it exists
        Plugin lobbyPlugin = Bukkit.getPluginManager().getPlugin("LobbyPlugin");
        if (lobbyPlugin != null && lobbyPlugin.isEnabled()) {
            try {
                // Use reflection to call a method on LobbyPlugin
                // This avoids import/circular dependency issues
                var method = lobbyPlugin.getClass().getMethod(
                        "onPlayerPartitionChange",
                        Player.class,
                        String.class,
                        String.class
                );
                method.invoke(lobbyPlugin, player, oldPartition, newPartition);

                plugin.getLogger().fine("Notified LobbyPlugin of partition change");
            } catch (NoSuchMethodException e) {
                // LobbyPlugin doesn't have the method - that's fine
                plugin.getLogger().fine("LobbyPlugin doesn't implement partition awareness");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to notify LobbyPlugin: " + e.getMessage());
            }
        }

        // You can add more plugins here as needed
        // Each plugin can optionally implement the partition change handler
    }
}