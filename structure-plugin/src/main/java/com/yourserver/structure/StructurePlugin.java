package com.yourserver.structure;

import com.yourserver.structure.api.StructureRegistry;
import com.yourserver.structure.command.StructureCommand;
import com.yourserver.structure.integration.WorldEditIntegration;
import com.yourserver.structure.storage.StructureStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Structure Management Plugin
 *
 * Provides advanced structure saving, loading, and placement capabilities
 * with WorldEdit integration and API for other plugins.
 *
 * Features:
 * - Unlimited structure size (beyond 48x48x48 structure block limit)
 * - WorldEdit schematic support (.schem, .schematic)
 * - Multiple format support (WorldEdit, NBT, Legacy)
 * - Rich metadata system (tags, search, filtering)
 * - Rotation, mirroring, terrain adaptation
 * - Async placement for large structures
 * - API for world generation in other plugins
 *
 * @author MCBZH
 * @version 1.0.0
 */
public class StructurePlugin extends JavaPlugin {

    private StructureStorage storage;
    private StructureRegistry registry;
    private WorldEditIntegration worldEditIntegration;

    @Override
    public void onLoad() {
        getLogger().info("Loading StructurePlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling StructurePlugin...");

        try {
            // Initialize storage
            this.storage = new StructureStorage(this);
            storage.initialize();
            getLogger().info("âœ“ Structure storage initialized");

            // Initialize registry
            this.registry = new StructureRegistry(this, storage);
            registry.initialize();
            getLogger().info("âœ“ Structure registry initialized");

            // Initialize WorldEdit integration
            if (getServer().getPluginManager().getPlugin("WorldEdit") != null) {
                this.worldEditIntegration = new WorldEditIntegration(this);
                worldEditIntegration.initialize();
                getLogger().info("âœ“ WorldEdit integration enabled");
            } else {
                getLogger().warning("âš  WorldEdit not found - schematic support disabled");
            }

            // Register commands
            StructureCommand structureCommand = new StructureCommand(this, registry, storage);
            getCommand("structure").setExecutor(structureCommand);
            getCommand("structure").setTabCompleter(structureCommand);
            getLogger().info("âœ“ Commands registered");

            // Register API service
            getServer().getServicesManager().register(
                    StructureRegistry.class,
                    registry,
                    this,
                    org.bukkit.plugin.ServicePriority.Normal
            );
            getLogger().info("âœ“ API service registered");

            getLogger().info("â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—");
            getLogger().info("â•‘  StructurePlugin Enabled Successfully  â•‘");
            getLogger().info("â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£");
            getLogger().info("â•‘  Structures: " + String.format("%-28s", registry.getStructureCount()) + " â•‘");
            getLogger().info("â•‘  Formats: WorldEdit, NBT, Legacy      â•‘");
            getLogger().info("â•‘  WorldEdit: " + String.format("%-24s", (worldEditIntegration != null ? "Enabled" : "Disabled")) + " â•‘");
            getLogger().info("â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable StructurePlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling StructurePlugin...");

        if (registry != null) {
            registry.shutdown();
            getLogger().info("âœ“ Registry shutdown");
        }

        if (storage != null) {
            storage.shutdown();
            getLogger().info("âœ“ Storage shutdown");
        }

        getLogger().info("StructurePlugin disabled successfully!");
    }

    // ===== PUBLIC API =====

    public StructureStorage getStorage() {
        return storage;
    }

    public StructureRegistry getRegistry() {
        return registry;
    }

    public WorldEditIntegration getWorldEditIntegration() {
        return worldEditIntegration;
    }

    public boolean hasWorldEdit() {
        return worldEditIntegration != null;
    }
}