package com.yourserver.partition.manager;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.model.Partition;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages plugin isolation between partitions.
 *
 * NOTE: Full plugin hot-loading/unloading is complex and can be unstable.
 * This implementation provides:
 * 1. Plugin enable/disable per partition (lightweight)
 * 2. Event isolation (plugins only receive events from their partition)
 * 3. Command isolation (optional)
 *
 * For true plugin hot-reloading, consider using PluginManager plugins like
 * PlugMan or implementing a proper ClassLoader isolation system.
 */
public class PluginIsolationManager {

    private final PartitionPlugin plugin;
    private final Map<String, Set<String>> partitionPlugins; // Partition ID -> Plugin Names
    private final Map<String, String> pluginStates; // Plugin Name -> State (enabled/disabled/isolated)

    public PluginIsolationManager(PartitionPlugin plugin) {
        this.plugin = plugin;
        this.partitionPlugins = new ConcurrentHashMap<>();
        this.pluginStates = new ConcurrentHashMap<>();
    }

    /**
     * Loads plugins for a partition.
     *
     * For now, this "loads" by marking plugins as active for the partition.
     * The plugins are already loaded by Bukkit, but we track which ones
     * should be active in each partition.
     */
    public void loadPluginsForPartition(@NotNull Partition partition) {
        String partitionId = partition.getId();
        Set<String> pluginNames = new HashSet<>(partition.getPlugins());

        plugin.getLogger().info("Loading plugins for partition '" + partitionId + "': " +
                String.join(", ", pluginNames));

        // Track which plugins are active in this partition
        partitionPlugins.put(partitionId, pluginNames);

        // Verify all plugins exist
        for (String pluginName : pluginNames) {
            Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);

            if (targetPlugin == null) {
                plugin.getLogger().warning("Plugin not found: " + pluginName +
                        " (specified for partition " + partitionId + ")");
            } else if (!targetPlugin.isEnabled()) {
                plugin.getLogger().warning("Plugin is disabled: " + pluginName +
                        " (specified for partition " + partitionId + ")");
            } else {
                plugin.getLogger().info("  ✓ " + pluginName + " active in partition");
            }
        }
    }

    /**
     * Unloads plugins for a partition.
     */
    public void unloadPluginsForPartition(@NotNull Partition partition) {
        String partitionId = partition.getId();

        plugin.getLogger().info("Unloading plugins for partition: " + partitionId);

        partitionPlugins.remove(partitionId);
    }

    /**
     * Checks if a plugin is active in a partition.
     */
    public boolean isPluginActiveInPartition(@NotNull String pluginName, @NotNull String partitionId) {
        Set<String> plugins = partitionPlugins.get(partitionId);
        return plugins != null && plugins.contains(pluginName);
    }

    /**
     * Gets all plugins active in a partition.
     */
    @NotNull
    public Set<String> getPluginsInPartition(@NotNull String partitionId) {
        Set<String> plugins = partitionPlugins.get(partitionId);
        return plugins != null ? new HashSet<>(plugins) : Collections.emptySet();
    }

    /**
     * Enables a plugin for a partition.
     * This adds the plugin to the partition's active plugin list.
     */
    public boolean enablePluginForPartition(@NotNull String pluginName, @NotNull String partitionId) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            plugin.getLogger().warning("Cannot enable plugin '" + pluginName + "': plugin not found");
            return false;
        }

        if (!targetPlugin.isEnabled()) {
            plugin.getLogger().warning("Cannot enable plugin '" + pluginName +
                    "' for partition: plugin is disabled");
            return false;
        }

        Set<String> plugins = partitionPlugins.computeIfAbsent(partitionId, k -> new HashSet<>());
        plugins.add(pluginName);

        plugin.getLogger().info("Enabled plugin '" + pluginName + "' for partition: " + partitionId);
        return true;
    }

    /**
     * Disables a plugin for a partition.
     */
    public boolean disablePluginForPartition(@NotNull String pluginName, @NotNull String partitionId) {
        Set<String> plugins = partitionPlugins.get(partitionId);
        if (plugins == null || !plugins.contains(pluginName)) {
            plugin.getLogger().warning("Plugin '" + pluginName + "' is not active in partition: " + partitionId);
            return false;
        }

        plugins.remove(pluginName);
        plugin.getLogger().info("Disabled plugin '" + pluginName + "' for partition: " + partitionId);
        return true;
    }

    /**
     * Hot-reloads a plugin across all partitions.
     *
     * WARNING: This is experimental and can cause issues!
     * Use with caution in production environments.
     */
    public boolean hotReloadPlugin(@NotNull String pluginName) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (targetPlugin == null) {
            plugin.getLogger().warning("Cannot reload plugin: " + pluginName + " (not found)");
            return false;
        }

        plugin.getLogger().info("Hot-reloading plugin: " + pluginName);

        try {
            // 1. Unregister event handlers
            HandlerList.unregisterAll(targetPlugin);
            plugin.getLogger().info("  - Unregistered event handlers");

            // 2. Unregister commands
            unregisterCommands(targetPlugin);
            plugin.getLogger().info("  - Unregistered commands");

            // 3. Disable plugin
            Bukkit.getPluginManager().disablePlugin(targetPlugin);
            plugin.getLogger().info("  - Disabled plugin");

            // 4. Re-enable plugin
            Bukkit.getPluginManager().enablePlugin(targetPlugin);
            plugin.getLogger().info("  - Re-enabled plugin");

            plugin.getLogger().info("Successfully reloaded plugin: " + pluginName);
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload plugin '" + pluginName + "': " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Unregisters all commands for a plugin.
     */
    private void unregisterCommands(@NotNull Plugin targetPlugin) {
        try {
            // Use reflection to access command map
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            org.bukkit.command.CommandMap commandMap = (org.bukkit.command.CommandMap) commandMapField.get(Bukkit.getServer());

            // Find and unregister plugin commands
            Field knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, org.bukkit.command.Command> knownCommands =
                    (Map<String, org.bukkit.command.Command>) knownCommandsField.get(commandMap);

            // Remove commands belonging to this plugin
            knownCommands.entrySet().removeIf(entry -> {
                org.bukkit.command.Command command = entry.getValue();
                if (command instanceof PluginCommand) {
                    return ((PluginCommand) command).getPlugin().equals(targetPlugin);
                }
                return false;
            });

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister commands for " + targetPlugin.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Gets plugin information for display.
     */
    @NotNull
    public Map<String, Object> getPluginInfo(@NotNull String pluginName) {
        Plugin targetPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        Map<String, Object> info = new LinkedHashMap<>();

        if (targetPlugin == null) {
            info.put("exists", false);
            return info;
        }

        info.put("exists", true);
        info.put("enabled", targetPlugin.isEnabled());
        info.put("version", targetPlugin.getDescription().getVersion());
        info.put("authors", targetPlugin.getDescription().getAuthors());

        // Count in how many partitions this plugin is active
        long activePartitions = partitionPlugins.values().stream()
                .filter(plugins -> plugins.contains(pluginName))
                .count();
        info.put("active_partitions", activePartitions);

        return info;
    }

    /**
     * Shuts down the plugin isolation manager.
     */
    public void shutdown() {
        partitionPlugins.clear();
        pluginStates.clear();
    }
}