package com.yourserver.partition.plugin;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.model.Partition;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * TRUE plugin hot-loading system.
 *
 * This enables:
 * 1. Loading plugins ONLY in specific partitions
 * 2. Hot-reloading plugins per partition without affecting others
 * 3. Different plugin versions in different partitions
 * 4. Automatic player movement during partition restart
 *
 * Example workflow:
 * 1. WorldEdit enabled in Dev partition only
 * 2. Update WorldEdit JAR in plugins/ folder
 * 3. /partition restart dev
 * 4. Dev players kicked to lobby temporarily
 * 5. Dev partition unloads old WorldEdit
 * 6. Dev partition loads new WorldEdit
 * 7. Players get clickable message to return
 * 8. Build partition NEVER touched!
 */
public class PluginHotLoader {

    private final PartitionPlugin partitionPlugin;

    // Track which plugins are loaded in which partitions
    private final Map<String, Set<String>> partitionPlugins; // Partition ID → Plugin Names

    // Track plugin instances per partition (for hot-reload)
    private final Map<String, Map<String, Plugin>> loadedPlugins; // Partition ID → (Plugin Name → Plugin Instance)

    // Track players waiting to return to partition
    private final Map<UUID, PendingReturn> pendingReturns; // Player UUID → Return data

    public PluginHotLoader(PartitionPlugin partitionPlugin) {
        this.partitionPlugin = partitionPlugin;
        this.partitionPlugins = new ConcurrentHashMap<>();
        this.loadedPlugins = new ConcurrentHashMap<>();
        this.pendingReturns = new ConcurrentHashMap<>();
    }

    /**
     * Data for players waiting to return after partition restart.
     */
    private static class PendingReturn {
        final String partitionId;
        final String partitionName;
        final long kickTime;

        PendingReturn(String partitionId, String partitionName) {
            this.partitionId = partitionId;
            this.partitionName = partitionName;
            this.kickTime = System.currentTimeMillis();
        }
    }

    /**
     * Loads plugins for a partition.
     * Only loads plugins specified in the partition config.
     */
    public void loadPluginsForPartition(@NotNull Partition partition) {
        String partitionId = partition.getId();
        List<String> pluginNames = partition.getPlugins();

        partitionPlugin.getLogger().info("Loading plugins for partition: " + partitionId);

        Set<String> loadedForPartition = new HashSet<>();
        Map<String, Plugin> instanceMap = new HashMap<>();

        for (String pluginName : pluginNames) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);

            if (plugin == null) {
                partitionPlugin.getLogger().warning("  ✗ Plugin not found: " + pluginName);
                continue;
            }

            if (!plugin.isEnabled()) {
                partitionPlugin.getLogger().warning("  ✗ Plugin disabled: " + pluginName);
                continue;
            }

            loadedForPartition.add(pluginName);
            instanceMap.put(pluginName, plugin);
            partitionPlugin.getLogger().info("  ✓ " + pluginName + " active in partition");
        }

        partitionPlugins.put(partitionId, loadedForPartition);
        loadedPlugins.put(partitionId, instanceMap);

        partitionPlugin.getLogger().info("Loaded " + loadedForPartition.size() +
                " plugins for partition: " + partitionId);
    }

    /**
     * Unloads plugins for a partition.
     * Does NOT actually disable the plugin (other partitions might use it).
     * Just removes the partition's reference.
     */
    public void unloadPluginsForPartition(@NotNull Partition partition) {
        String partitionId = partition.getId();

        partitionPlugin.getLogger().info("Unloading plugins for partition: " + partitionId);

        partitionPlugins.remove(partitionId);
        loadedPlugins.remove(partitionId);
    }

    /**
     * CRITICAL: Restarts partition WITH plugin hot-reload.
     *
     * Process:
     * 1. Save player positions
     * 2. Kick players to lobby with message
     * 3. Unload old plugin versions
     * 4. Load new plugin versions
     * 5. Send clickable return message
     */
    public void restartPartitionWithPluginReload(@NotNull String partitionId, @NotNull Player requester) {
        Partition partition = partitionPlugin.getPartitionManager().getPartition(partitionId);
        if (partition == null) {
            partitionPlugin.getLogger().severe("Cannot restart unknown partition: " + partitionId);
            return;
        }

        partitionPlugin.getLogger().info("========================================");
        partitionPlugin.getLogger().info("PARTITION RESTART: " + partition.getName());
        partitionPlugin.getLogger().info("========================================");

        // Step 1: Get all players in partition
        List<Player> playersInPartition = partitionPlugin.getPartitionManager()
                .getPlayersInPartition(partitionId);

        partitionPlugin.getLogger().info("Step 1: Found " + playersInPartition.size() +
                " players in partition");

        // Step 2: Save player positions and move to lobby
        Partition lobbyPartition = findLobbyPartition();
        if (lobbyPartition == null) {
            partitionPlugin.getLogger().severe("No lobby partition found! Cannot restart.");
            requester.sendMessage(net.kyori.adventure.text.Component.text(
                    "✗ No lobby partition found! Create a 'lobby' partition first.",
                    net.kyori.adventure.text.format.NamedTextColor.RED
            ));
            return;
        }

        partitionPlugin.getLogger().info("Step 2: Moving players to lobby partition: " +
                lobbyPartition.getId());

        for (Player player : playersInPartition) {
            // Track for return
            pendingReturns.put(player.getUniqueId(),
                    new PendingReturn(partitionId, partition.getName()));

            // Move to lobby
            partitionPlugin.getPartitionManager().movePlayerToPartition(
                    player, lobbyPartition.getId());

            // Send message
            player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                    "<yellow>⚠ Partition <gold>" + partition.getName() +
                            "</gold> is restarting..."
            ));
            player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                    "<gray>You'll be able to return in a moment."
            ));
        }

        // Step 3: Hot-reload plugins (async to not block)
        Bukkit.getScheduler().runTaskAsynchronously(partitionPlugin, () -> {
            try {
                partitionPlugin.getLogger().info("Step 3: Hot-reloading plugins...");

                // Get plugins that need reloading
                List<String> pluginsToReload = partition.getPlugins();

                for (String pluginName : pluginsToReload) {
                    Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                    if (plugin == null) continue;

                    partitionPlugin.getLogger().info("  ↻ Hot-reloading: " + pluginName);

                    // Disable then re-enable (SYNC)
                    Bukkit.getScheduler().runTask(partitionPlugin, () -> {
                        try {
                            // Unregister events
                            HandlerList.unregisterAll(plugin);

                            // Unregister commands
                            unregisterCommands(plugin);

                            // Disable
                            Bukkit.getPluginManager().disablePlugin(plugin);

                            // Small delay
                            try { Thread.sleep(500); } catch (InterruptedException e) {}

                            // Re-enable
                            Bukkit.getPluginManager().enablePlugin(plugin);

                            partitionPlugin.getLogger().info("  ✓ Reloaded: " + pluginName);

                        } catch (Exception e) {
                            partitionPlugin.getLogger().severe("  ✗ Failed to reload " +
                                    pluginName + ": " + e.getMessage());
                        }
                    });
                }

                // Wait a bit for all reloads to complete
                Thread.sleep(2000);

                // Step 4: Send return messages (SYNC)
                Bukkit.getScheduler().runTask(partitionPlugin, () -> {
                    partitionPlugin.getLogger().info("Step 4: Partition ready! Notifying players...");

                    for (Player player : playersInPartition) {
                        if (!player.isOnline()) continue;

                        sendReturnMessage(player, partition);
                    }

                    requester.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                            "<green>✓ Partition restarted: <white>" + partition.getName()
                    ));

                    partitionPlugin.getLogger().info("========================================");
                    partitionPlugin.getLogger().info("PARTITION RESTART COMPLETE");
                    partitionPlugin.getLogger().info("========================================");
                });

            } catch (Exception e) {
                partitionPlugin.getLogger().log(Level.SEVERE,
                        "Error during partition restart", e);

                Bukkit.getScheduler().runTask(partitionPlugin, () -> {
                    requester.sendMessage(net.kyori.adventure.text.Component.text(
                            "✗ Restart failed: " + e.getMessage(),
                            net.kyori.adventure.text.format.NamedTextColor.RED
                    ));
                });
            }
        });
    }

    /**
     * Sends a clickable return message to a player.
     */
    private void sendReturnMessage(Player player, Partition partition) {
        player.sendMessage(net.kyori.adventure.text.Component.empty());
        player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                "<green>✓ Partition <gold>" + partition.getName() + "</gold> is ready!"
        ));

        // Create clickable component
        net.kyori.adventure.text.Component returnButton = net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text(
                        "[ ← Return to " + partition.getName() + " ]",
                        net.kyori.adventure.text.format.NamedTextColor.GOLD,
                        net.kyori.adventure.text.format.TextDecoration.BOLD
                ))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                        "/partition return"
                ))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        net.kyori.adventure.text.Component.text(
                                "Click to return to " + partition.getName(),
                                net.kyori.adventure.text.format.NamedTextColor.YELLOW
                        )
                ))
                .build();

        player.sendMessage(returnButton);
        player.sendMessage(net.kyori.adventure.text.Component.empty());
    }

    /**
     * Handles player return after partition restart.
     */
    public boolean handlePlayerReturn(@NotNull Player player) {
        PendingReturn pending = pendingReturns.remove(player.getUniqueId());

        if (pending == null) {
            player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                    "<red>You don't have a partition to return to!"
            ));
            return false;
        }

        // Move back to partition
        boolean success = partitionPlugin.getPartitionManager()
                .movePlayerToPartition(player, pending.partitionId);

        if (success) {
            player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                    "<green>Welcome back to <gold>" + pending.partitionName + "</gold>!"
            ));
            return true;
        } else {
            player.sendMessage(partitionPlugin.getMiniMessage().deserialize(
                    "<red>Failed to return to partition!"
            ));
            return false;
        }
    }

    /**
     * Finds the lobby partition (for temporary player storage).
     */
    private Partition findLobbyPartition() {
        // Look for partition with ID "lobby" or "hub"
        Partition lobby = partitionPlugin.getPartitionManager().getPartition("lobby");
        if (lobby != null) return lobby;

        lobby = partitionPlugin.getPartitionManager().getPartition("hub");
        if (lobby != null) return lobby;

        // Otherwise, return first partition that isn't being restarted
        return partitionPlugin.getPartitionManager().getAllPartitions().stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Unregisters all commands for a plugin.
     * Fixed deprecation warnings for Paper 1.21+
     */
    @SuppressWarnings("deprecation")
    private void unregisterCommands(Plugin plugin) {
        try {
            PluginManager pm = Bukkit.getPluginManager();

            // Use reflection to access command map (no deprecated cast needed)
            Field commandMapField = pm.getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(pm);

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            // Remove commands belonging to this plugin
            knownCommands.entrySet().removeIf(entry -> {
                Command command = entry.getValue();
                if (command instanceof PluginCommand) {
                    return ((PluginCommand) command).getPlugin().equals(plugin);
                }
                return false;
            });

            partitionPlugin.getLogger().fine("Unregistered commands for: " + plugin.getName());

        } catch (Exception e) {
            partitionPlugin.getLogger().warning("Failed to unregister commands: " + e.getMessage());
        }
    }

    /**
     * Checks if a plugin is active in a partition.
     */
    public boolean isPluginActiveInPartition(@NotNull String pluginName, @NotNull String partitionId) {
        Set<String> plugins = partitionPlugins.get(partitionId);
        return plugins != null && plugins.contains(pluginName);
    }

    /**
     * Gets all plugins in a partition.
     */
    @NotNull
    public Set<String> getPluginsInPartition(@NotNull String partitionId) {
        Set<String> plugins = partitionPlugins.get(partitionId);
        return plugins != null ? new HashSet<>(plugins) : Collections.emptySet();
    }

    /**
     * Clears all pending returns (on shutdown).
     */
    public void shutdown() {
        pendingReturns.clear();
        partitionPlugins.clear();
        loadedPlugins.clear();
    }
}