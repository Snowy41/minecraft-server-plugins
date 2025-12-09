package com.yourserver.core;

import com.yourserver.core.config.DatabaseConfig;
import com.yourserver.core.database.DatabaseManager;
import com.yourserver.core.database.MySQLPlayerDataRepository;
import com.yourserver.core.database.MySQLPlayerStatsRepository;
import com.yourserver.core.player.PlayerDataManager;
import com.yourserver.core.redis.RedisManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Core plugin providing essential infrastructure:
 * - MySQL database connection and management
 * - Redis pub/sub messaging
 * - Player data persistence and caching
 * - Configuration management
 */
public class CorePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private PlayerDataManager playerDataManager;
    private DatabaseConfig databaseConfig;

    @Override
    public void onLoad() {
        getLogger().info("Loading CorePlugin...");

        // Save default configurations
        saveDefaultConfig();
        saveResource("database.yml", false);
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling CorePlugin...");

        try {
            // 1. Load database configuration
            databaseConfig = DatabaseConfig.load(getDataFolder());
            getLogger().info("Database configuration loaded");

            // 2. Initialize database manager
            databaseManager = new DatabaseManager(getLogger());
            databaseManager.initialize(databaseConfig.getMySQLConfig());
            getLogger().info("Database connection established");

            // 3. Initialize repositories
            MySQLPlayerDataRepository playerDataRepo = new MySQLPlayerDataRepository(databaseManager);
            MySQLPlayerStatsRepository playerStatsRepo = new MySQLPlayerStatsRepository(databaseManager);
            getLogger().info("Database repositories initialized");

            // 4. Initialize Redis manager
            redisManager = new RedisManager(getLogger());
            redisManager.initialize(databaseConfig.getRedisConfig());
            getLogger().info("Redis connection established");

            // 5. Initialize player data manager
            playerDataManager = new PlayerDataManager(playerDataRepo, playerStatsRepo);
            getLogger().info("Player data manager initialized");

            // 6. Register listeners
            getServer().getPluginManager().registerEvents(
                    new com.yourserver.core.listener.PlayerConnectionListener(playerDataManager),
                    this
            );
            getLogger().info("Event listeners registered");

            // 7. Register commands
            getCommand("core").setExecutor(new com.yourserver.core.command.CoreCommand(this));

            getLogger().info("CorePlugin enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable CorePlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling CorePlugin...");

        // Gracefully shutdown components
        if (playerDataManager != null) {
            playerDataManager.shutdown();
            getLogger().info("Player data manager shut down");
        }

        if (redisManager != null) {
            redisManager.shutdown();
            getLogger().info("Redis connection closed");
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
            getLogger().info("Database connection closed");
        }

        getLogger().info("CorePlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        databaseConfig = DatabaseConfig.load(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    // ===== Public API for other plugins =====

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
}