package com.yourserver.core;

import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.core.buildmode.BuildModeManager;
import com.yourserver.core.config.DatabaseConfig;
import com.yourserver.core.database.DatabaseManager;
import com.yourserver.core.database.mysql.MySQLPlayerDataRepository;
import com.yourserver.core.database.mysql.MySQLPlayerStatsRepository;
import com.yourserver.core.player.PlayerDataManager;
import com.yourserver.core.rank.RankDisplayManager;
import com.yourserver.core.redis.RedisManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * CorePlugin - FULLY MODERNIZED FOR MYSQL + CLOUDNET 4.0
 *
 * KEY IMPROVEMENTS:
 * ✅ MySQL database with HikariCP (replaces JSON)
 * ✅ Async operations (non-blocking)
 * ✅ Connection pooling (scalable)
 * ✅ Auto-reconnect on failure
 * ✅ Batch operations for performance
 * ✅ Health monitoring
 * ✅ CloudNet service detection
 * ✅ Redis pub/sub messaging
 * ✅ Proper error handling
 * ✅ Graceful shutdown
 * ✅ Thread-safe caching
 * ✅ ACID database transactions
 *
 * ARCHITECTURE:
 * - DatabaseManager: HikariCP connection pool
 * - Repositories: Clean data access layer
 * - PlayerDataManager: Caffeine cache + async saves
 * - RedisManager: Cross-server messaging
 * - Scheduled tasks: Auto-save, health checks
 *
 * @author MCBZH
 * @version 2.0.0 - MySQL Edition
 */
public class CorePlugin extends JavaPlugin {

    // ===== CORE MANAGERS =====
    private DatabaseManager databaseManager;
    private RedisManager redisManager;
    private PlayerDataManager playerDataManager;
    private BuildModeManager buildModeManager;
    private RankDisplayManager rankDisplayManager;

    // ===== CONFIGURATION =====
    private DatabaseConfig databaseConfig;

    // ===== REPOSITORIES (MySQL) =====
    private MySQLPlayerDataRepository playerDataRepository;
    private MySQLPlayerStatsRepository playerStatsRepository;

    // ===== ASYNC EXECUTION =====
    private ScheduledExecutorService asyncExecutor;

    // ===== CLOUDNET INFO =====
    private String serviceName;
    private String serviceGroup;
    private boolean isCloudNetService;

    // ===== LIFECYCLE: LOAD =====
    @Override
    public void onLoad() {
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  CorePlugin v" + getDescription().getVersion() + "                  ║");
        getLogger().info("║  MySQL + Redis + CloudNet Ready        ║");
        getLogger().info("╚════════════════════════════════════════╝");

        // Save default configuration files
        saveDefaultConfig();
        saveResource("database.yml", false);

        // Detect CloudNet environment
        detectCloudNetService();
    }

    // ===== LIFECYCLE: ENABLE =====
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        try {
            // === PHASE 1: INITIALIZATION ===
            getLogger().info("Starting initialization sequence...");

            initializeThreadPool();
            getLogger().info("✓ Thread pool ready");

            loadConfiguration();
            getLogger().info("✓ Configuration loaded");

            // === PHASE 2: DATABASE ===
            initializeDatabase();
            getLogger().info("✓ MySQL connected (" + databaseManager.getPoolStats() + ")");

            // === PHASE 3: REDIS (Optional) ===
            initializeRedis();
            if (redisManager != null && redisManager.isConnected()) {
                getLogger().info("✓ Redis connected");
            } else {
                getLogger().warning("⚠ Redis disabled (cross-server features unavailable)");
            }

            // === PHASE 4: REPOSITORIES & MANAGERS ===
            initializeRepositories();
            getLogger().info("✓ Repositories initialized");

            playerDataManager = new PlayerDataManager(
                    playerDataRepository,
                    playerStatsRepository
            );
            getLogger().info("✓ Player data manager ready");

            buildModeManager = new BuildModeManager();
            rankDisplayManager = new RankDisplayManager(getLogger());
            getLogger().info("✓ Auxiliary managers ready");

            // === PHASE 5: LISTENERS & COMMANDS ===
            registerListeners();
            getLogger().info("✓ Event listeners registered");

            registerCommands();
            getLogger().info("✓ Commands registered");

            // === PHASE 6: SCHEDULED TASKS ===
            startPeriodicTasks();
            getLogger().info("✓ Scheduled tasks started");

            // === SUCCESS ===
            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("╔════════════════════════════════════════╗");
            getLogger().info("║  ✓ COREPLUGIN ENABLED SUCCESSFULLY     ║");
            getLogger().info("╠════════════════════════════════════════╣");
            if (isCloudNetService) {
                getLogger().info("║  Service: " + String.format("%-28s", serviceName) + " ║");
                getLogger().info("║  Group:   " + String.format("%-28s", serviceGroup) + " ║");
            }
            getLogger().info("║  Database: MySQL (HikariCP)            ║");
            getLogger().info("║  Cache: Caffeine                       ║");
            getLogger().info("║  Startup: " + elapsed + "ms" + " ".repeat(25 - String.valueOf(elapsed).length()) + "║");
            getLogger().info("╚════════════════════════════════════════╝");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "╔════════════════════════════════════════╗", e);
            getLogger().severe("║  ✗ STARTUP FAILED!                     ║");
            getLogger().severe("║  Check logs for details                ║");
            getLogger().severe("╚════════════════════════════════════════╝");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    // ===== LIFECYCLE: DISABLE =====
    @Override
    public void onDisable() {
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  Shutting down CorePlugin...           ║");
        getLogger().info("╚════════════════════════════════════════╝");

        // 1. Save all player data
        if (playerDataManager != null) {
            getLogger().info("Saving all player data...");
            try {
                playerDataManager.shutdown();
                getLogger().info("✓ Player data saved");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to save player data!", e);
            }
        }

        // 2. Stop scheduled tasks
        if (asyncExecutor != null && !asyncExecutor.isShutdown()) {
            getLogger().info("Stopping scheduled tasks...");
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
                getLogger().info("✓ Tasks stopped");
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // 3. Close Redis
        if (redisManager != null) {
            getLogger().info("Closing Redis connection...");
            redisManager.shutdown();
            getLogger().info("✓ Redis closed");
        }

        // 4. Close database
        if (databaseManager != null) {
            getLogger().info("Closing database connection...");
            databaseManager.shutdown();
            getLogger().info("✓ Database closed");
        }

        // 5. Cleanup other managers
        if (buildModeManager != null) {
            buildModeManager.shutdown();
        }

        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  ✓ CorePlugin disabled successfully    ║");
        getLogger().info("╚════════════════════════════════════════╝");
    }

    // ===== INITIALIZATION METHODS =====

    private void initializeThreadPool() {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.asyncExecutor = Executors.newScheduledThreadPool(
                poolSize,
                runnable -> {
                    Thread thread = new Thread(runnable, "CorePlugin-Async");
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((t, e) ->
                            getLogger().log(Level.SEVERE, "Uncaught exception in " + t.getName(), e)
                    );
                    return thread;
                }
        );
        getLogger().fine("Thread pool created with " + poolSize + " threads");
    }

    private void loadConfiguration() {
        this.databaseConfig = DatabaseConfig.load(getDataFolder());

        if (!databaseConfig.hasMySQLConfig()) {
            throw new RuntimeException("MySQL configuration not found in database.yml!");
        }
    }

    private void initializeDatabase() throws SQLException {
        this.databaseManager = new DatabaseManager(
                getLogger(),
                databaseConfig.getMySQLConfig()
        );

        this.databaseManager.initialize();

        if (!databaseManager.isConnected()) {
            throw new SQLException("Database connection test failed!");
        }
    }

    private void initializeRedis() {
        try {
            this.redisManager = new RedisManager(getLogger());
            this.redisManager.initialize(databaseConfig.getRedisConfig());
            subscribeToCloudNetChannels();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Redis initialization failed", e);
            getLogger().warning("Continuing without Redis (cross-server features disabled)");
            this.redisManager = null;
        }
    }

    private void initializeRepositories() {
        this.playerDataRepository = new MySQLPlayerDataRepository(
                databaseManager.getDataSource(),
                asyncExecutor,
                getLogger()
        );

        this.playerStatsRepository = new MySQLPlayerStatsRepository(
                databaseManager.getDataSource(),
                asyncExecutor,
                getLogger()
        );
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(
                new com.yourserver.core.listener.PlayerConnectionListener(playerDataManager),
                this
        );

        getServer().getPluginManager().registerEvents(
                new com.yourserver.core.listener.BuildModeListener(buildModeManager),
                this
        );
    }

    private void registerCommands() {
        getCommand("core").setExecutor(
                new com.yourserver.core.command.CoreCommand(this)
        );
        getCommand("build").setExecutor(
                new com.yourserver.core.command.BuildModeCommand(this, buildModeManager)
        );
    }

    private void startPeriodicTasks() {
        // Auto-save every 5 minutes
        asyncExecutor.scheduleAtFixedRate(() -> {
            try {
                playerDataManager.saveAll().join();
                getLogger().fine("Auto-save completed");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Auto-save failed", e);
            }
        }, 5, 5, TimeUnit.MINUTES);

        // Health check every 30 seconds
        asyncExecutor.scheduleAtFixedRate(() -> {
            if (!databaseManager.healthCheck()) {
                getLogger().warning("⚠ Database health check failed!");
            }
        }, 30, 30, TimeUnit.SECONDS);

        // Pool stats logging every 5 minutes (for monitoring)
        asyncExecutor.scheduleAtFixedRate(() -> {
            getLogger().info("Pool: " + databaseManager.getPoolStats());
        }, 5, 5, TimeUnit.MINUTES);
    }

    private void detectCloudNetService() {
        this.serviceName = System.getProperty("cloudnet.service.name");
        this.serviceGroup = System.getProperty("cloudnet.service.group");
        this.isCloudNetService = serviceName != null;

        if (isCloudNetService) {
            getLogger().info("Running on CloudNet: " + serviceName + " (" + serviceGroup + ")");
        } else {
            getLogger().info("Running in standalone mode");
        }
    }

    private void subscribeToCloudNetChannels() {
        if (redisManager == null || !redisManager.isConnected()) {
            return;
        }

        redisManager.subscribe("player:join", message ->
                getLogger().fine("Player joined server: " + message)
        );

        redisManager.subscribe("player:quit", message ->
                getLogger().fine("Player quit server: " + message)
        );

        redisManager.subscribe("game:state", message ->
                getLogger().fine("Game state update: " + message)
        );

        getLogger().info("Subscribed to CloudNet Redis channels");
    }

    // ===== PUBLIC API =====

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public BuildModeManager getBuildModeManager() {
        return buildModeManager;
    }

    public RankDisplayManager getRankDisplayManager() {
        return rankDisplayManager;
    }

    public RedisMessenger getRedisMessenger() {
        return redisManager;
    }

    public ScheduledExecutorService getAsyncExecutor() {
        return asyncExecutor;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceGroup() {
        return serviceGroup;
    }

    public boolean isCloudNetService() {
        return isCloudNetService;
    }

    public void reloadConfiguration() {
        reloadConfig();
        databaseConfig = DatabaseConfig.load(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    public boolean isHealthy() {
        return databaseManager != null &&
                databaseManager.isConnected() &&
                playerDataManager != null;
    }
}