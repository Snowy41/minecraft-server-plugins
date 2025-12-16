package com.yourserver.core;

import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.core.buildmode.BuildModeManager;
import com.yourserver.core.config.DatabaseConfig;
import com.yourserver.core.database.json.JSONPlayerDataRepository;
import com.yourserver.core.database.json.JSONPlayerStatsRepository;
import com.yourserver.core.player.PlayerDataManager;
import com.yourserver.core.rank.RankDisplayManager;
import com.yourserver.core.redis.RedisManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Core plugin providing essential infrastructure - REDESIGNED FOR JSON STORAGE
 *
 * CHANGES FROM ORIGINAL:
 * - Removed MySQL dependency (DatabaseManager completely removed)
 * - Uses JSON files for player data (like SocialPlugin)
 * - Still uses Redis for cross-server messaging
 * - Much simpler, no database connection issues!
 *
 * Data Storage:
 * - Player data: plugins/CorePlugin/data/players.json
 * - Player stats: plugins/CorePlugin/data/player-stats.json
 * - Redis: Cross-server messaging only (no persistent storage)
 */
public class CorePlugin extends JavaPlugin {

    // REMOVED: private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private PlayerDataManager playerDataManager;
    private DatabaseConfig databaseConfig; // Only used for Redis config now
    private BuildModeManager buildModeManager;
    private RankDisplayManager rankDisplayManager;

    // NEW: JSON repositories
    private JSONPlayerDataRepository playerDataRepository;
    private JSONPlayerStatsRepository playerStatsRepository;

    @Override
    public void onLoad() {
        getLogger().info("Loading CorePlugin (JSON Storage Mode)...");

        // Only save redis.yml now (removed database.yml)
        saveResource("redis.yml", false);
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling CorePlugin (JSON Storage Mode)...");

        try {
            // 1. Load Redis configuration (no more MySQL config)
            databaseConfig = DatabaseConfig.loadRedisOnly(getDataFolder());
            getLogger().info("✓ Redis configuration loaded");

            // 2. Initialize JSON repositories (NO DATABASE!)
            playerDataRepository = new JSONPlayerDataRepository(getDataFolder(), getLogger());
            playerStatsRepository = new JSONPlayerStatsRepository(getDataFolder(), getLogger());
            getLogger().info("✓ JSON repositories initialized");
            getLogger().info("  - Data stored in: plugins/CorePlugin/data/");

            // 3. Initialize Redis manager
            redisManager = new RedisManager(getLogger());
            redisManager.initialize(databaseConfig.getRedisConfig());
            getLogger().info("✓ Redis connection established");

            // 4. Initialize player data manager (now uses JSON)
            playerDataManager = new PlayerDataManager(playerDataRepository, playerStatsRepository);
            getLogger().info("✓ Player data manager initialized (JSON mode)");

            // 5. Initialize other managers
            buildModeManager = new BuildModeManager();
            rankDisplayManager = new RankDisplayManager(getLogger());
            getLogger().info("✓ All managers initialized");

            // 6. Register listeners
            getServer().getPluginManager().registerEvents(
                    new com.yourserver.core.listener.PlayerConnectionListener(playerDataManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new com.yourserver.core.listener.BuildModeListener(buildModeManager),
                    this
            );
            getLogger().info("✓ Event listeners registered");

            // 7. Register commands
            getCommand("core").setExecutor(new com.yourserver.core.command.CoreCommand(this));
            getCommand("build").setExecutor(new com.yourserver.core.command.BuildModeCommand(this, buildModeManager));
            getLogger().info("✓ Commands registered");

            getLogger().info("CorePlugin enabled successfully (JSON mode)!");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("✓ NO DATABASE REQUIRED - All data stored in JSON");
            getLogger().info("✓ Player data: plugins/CorePlugin/data/players.json");
            getLogger().info("✓ Player stats: plugins/CorePlugin/data/player-stats.json");
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable CorePlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling CorePlugin...");

        // Save all data before shutdown
        if (playerDataManager != null) {
            getLogger().info("Saving all player data to JSON...");
            playerDataManager.shutdown();
            getLogger().info("✓ Player data saved");
        }

        if (redisManager != null) {
            redisManager.shutdown();
            getLogger().info("✓ Redis connection closed");
        }

        if (buildModeManager != null) {
            buildModeManager.shutdown();
            getLogger().info("✓ Build mode manager shut down");
        }

        getLogger().info("CorePlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        databaseConfig = DatabaseConfig.loadRedisOnly(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    // ===== Public API for other plugins =====

    // REMOVED: getDatabaseManager() - no longer exists!

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public BuildModeManager getBuildModeManager() {
        return buildModeManager;
    }

    public RankDisplayManager getRankDisplayManager() {
        return rankDisplayManager;
    }

    /**
     * Gets the Redis messenger for cross-server communication.
     */
    public RedisMessenger getRedisMessenger() {
        return redisManager;
    }

    // NEW: Check if database is available (always returns false now)
    @Deprecated
    public boolean isDatabaseAvailable() {
        getLogger().warning("isDatabaseAvailable() called - database support removed, using JSON");
        return false;
    }
}