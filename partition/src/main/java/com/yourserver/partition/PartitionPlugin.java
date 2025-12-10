package com.yourserver.partition;

import com.yourserver.partition.command.PartitionCommand;
import com.yourserver.partition.config.PartitionConfig;
import com.yourserver.partition.isolation.PartitionIsolationSystem;
import com.yourserver.partition.listener.PartitionAwarePluginListener;
import com.yourserver.partition.listener.PartitionIsolationListener;
import com.yourserver.partition.listener.PlayerWorldChangeListener;
import com.yourserver.partition.listener.PluginIsolationListener;
import com.yourserver.partition.manager.PartitionManager;
import com.yourserver.partition.manager.PluginIsolationManager;
import com.yourserver.partition.manager.WorldManager;
import com.yourserver.partition.plugin.PluginHotLoader; // ← ADD THIS IMPORT
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ENHANCED PartitionPlugin - Complete mini-server isolation.
 */
public class PartitionPlugin extends JavaPlugin {

    private PartitionConfig config;
    private MiniMessage miniMessage;
    private WorldManager worldManager;
    private PartitionManager partitionManager;
    private PluginIsolationManager pluginIsolationManager;
    private PartitionIsolationSystem isolationSystem;
    private PluginHotLoader pluginHotLoader; // ← ADD THIS FIELD

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

            // 4. Initialize isolation system
            isolationSystem = new PartitionIsolationSystem(this, partitionManager);
            getLogger().info("Isolation system initialized");

            // 5. Initialize HOT-LOADER ← ADD THIS
            pluginHotLoader = new PluginHotLoader(this);
            getLogger().info("Plugin hot-loader initialized");

            // 6. Load all partitions
            partitionManager.loadAllPartitions();

            // 7. Register listeners
            getServer().getPluginManager().registerEvents(isolationSystem, this);
            getLogger().info("Isolation system listener registered");

            getServer().getPluginManager().registerEvents(
                    new PlayerWorldChangeListener(partitionManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new PluginIsolationListener(pluginIsolationManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new PartitionIsolationListener(this, partitionManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new PartitionAwarePluginListener(this, partitionManager, isolationSystem),
                    this
            );
            getLogger().info("Event listeners registered");

            // 8. Register commands ← UPDATED THIS
            PartitionCommand partitionCommand = new PartitionCommand(
                    this, partitionManager, isolationSystem, pluginHotLoader
            );
            getCommand("partition").setExecutor(partitionCommand);
            getCommand("partition").setTabCompleter(partitionCommand);
            getLogger().info("Commands registered");

            // 9. Log isolation settings
            logIsolationSettings();

            // 10. Schedule periodic visibility check
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (var partition : partitionManager.getAllPartitions()) {
                    isolationSystem.updatePartitionVisibility(partition.getId());
                }
            }, 600L, 600L);

            getLogger().info("PartitionPlugin enabled successfully!");
            getLogger().info("✓ Complete partition isolation active");
            getLogger().info("✓ Plugin hot-reloading enabled");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable PartitionPlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PartitionPlugin...");

        if (pluginHotLoader != null) { // ← ADD THIS
            pluginHotLoader.shutdown();
            getLogger().info("Plugin hot-loader shut down");
        }

        if (isolationSystem != null) {
            isolationSystem.shutdown();
            getLogger().info("Isolation system shut down");
        }

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
        if (isolationSystem != null) {
            isolationSystem.shutdown();
        }
        if (pluginHotLoader != null) { // ← ADD THIS
            pluginHotLoader.shutdown();
        }

        worldManager = new WorldManager(this);
        partitionManager = new PartitionManager(this, config, worldManager);
        isolationSystem = new PartitionIsolationSystem(this, partitionManager);
        pluginHotLoader = new PluginHotLoader(this); // ← ADD THIS
        partitionManager.loadAllPartitions();

        getLogger().info("Configuration reloaded");
        logIsolationSettings();
    }

    private void logIsolationSettings() {
        PartitionConfig.IsolationSettings settings = config.getIsolationSettings();

        getLogger().info("=== Partition Isolation Settings ===");
        getLogger().info("  Chat Isolation: " + (settings.isChatIsolation() ? "✓ ENABLED" : "✗ DISABLED"));
        getLogger().info("  Tab List Isolation: " + (settings.isTablistIsolation() ? "✓ ENABLED" : "✗ DISABLED"));
        getLogger().info("  Command Isolation: " + (settings.isCommandIsolation() ? "✓ ENABLED" : "✗ DISABLED"));
        getLogger().info("  World Border Isolation: " + (settings.isWorldBorderIsolation() ? "✓ ENABLED" : "✗ DISABLED"));
        getLogger().info("  Persistent State: ✓ ENABLED (across restarts)");
        getLogger().info("  Dynamic Updates: ✓ ENABLED (real-time)");
        getLogger().info("  Plugin Hot-Reload: ✓ ENABLED"); // ← ADD THIS
        getLogger().info("====================================");
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

    public PartitionIsolationSystem getIsolationSystem() {
        return isolationSystem;
    }

    // ← ADD THIS GETTER
    public PluginHotLoader getPluginHotLoader() {
        return pluginHotLoader;
    }
}