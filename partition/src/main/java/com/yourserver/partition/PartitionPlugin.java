package com.yourserver.partition;

import com.yourserver.partition.command.PartitionCommand;
import com.yourserver.partition.config.PartitionConfig;
import com.yourserver.partition.listener.PlayerWorldChangeListener;
import com.yourserver.partition.listener.PluginIsolationListener;
import com.yourserver.partition.manager.PartitionManager;
import com.yourserver.partition.manager.PluginIsolationManager;
import com.yourserver.partition.manager.WorldManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main partition plugin class.
 * Provides multi-server partition system for isolated mini-servers.
 */
public class PartitionPlugin extends JavaPlugin {

    private PartitionConfig config;
    private MiniMessage miniMessage;
    private WorldManager worldManager;
    private PartitionManager partitionManager;
    private PluginIsolationManager pluginIsolationManager;

    @Override
    public void onLoad() {
        getLogger().info("Loading PartitionPlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling PartitionPlugin...");

        try {
            // 1. Initialize MiniMessage
            miniMessage = MiniMessage.miniMessage();

            // 2. Load configuration
            config = PartitionConfig.load(getDataFolder());
            getLogger().info("Configuration loaded");

            // 3. Initialize managers
            worldManager = new WorldManager(this);
            pluginIsolationManager = new PluginIsolationManager(this);
            partitionManager = new PartitionManager(this, config, worldManager);
            getLogger().info("Managers initialized");

            // 4. Load all partitions
            partitionManager.loadAllPartitions();

            // 5. Register listeners
            getServer().getPluginManager().registerEvents(
                    new PlayerWorldChangeListener(partitionManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new PluginIsolationListener(pluginIsolationManager),
                    this
            );
            getLogger().info("Event listeners registered");

            // 6. Register commands
            PartitionCommand partitionCommand = new PartitionCommand(this, partitionManager);
            getCommand("partition").setExecutor(partitionCommand);
            getCommand("partition").setTabCompleter(partitionCommand);
            getLogger().info("Commands registered");

            getLogger().info("PartitionPlugin enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable PartitionPlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PartitionPlugin...");

        if (partitionManager != null) {
            partitionManager.shutdown();
            getLogger().info("Partition manager shut down");
        }

        if (pluginIsolationManager != null) {
            pluginIsolationManager.shutdown();
            getLogger().info("Plugin isolation manager shut down");
        }

        getLogger().info("PartitionPlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        config = PartitionConfig.load(getDataFolder());

        // Restart managers with new config
        if (partitionManager != null) {
            partitionManager.shutdown();
        }

        worldManager = new WorldManager(this);
        partitionManager = new PartitionManager(this, config, worldManager);
        partitionManager.loadAllPartitions();

        getLogger().info("Configuration reloaded");
    }

    // ===== Public API =====

    public PartitionConfig getPartitionConfig() {
        return config;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public PartitionManager getPartitionManager() {
        return partitionManager;
    }

    public PluginIsolationManager getPluginIsolationManager() {
        return pluginIsolationManager;
    }
}