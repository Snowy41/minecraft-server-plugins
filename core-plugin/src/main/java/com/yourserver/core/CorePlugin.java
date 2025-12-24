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
 * CorePlugin - MySQL + CloudNet 4.0 Ready
 *
 * CLOUDNET 4.0 INTEGRATION:
 * ✅ Service detection via system properties
 * ✅ Shared MySQL database across all services
 * ✅ Redis pub/sub for cross-server messaging
 * ✅ Proper async operations (non-blocking)
 * ✅ Connection pooling with HikariCP
 * ✅ Health monitoring and auto-reconnect
 * ✅ Graceful shutdown with data persistence
 *
 * ARCHITECTURE:
 * - DatabaseManager: HikariCP connection pool
 * - Repositories: Clean MySQL data access
 * - PlayerDataManager: Caffeine cache + async saves
 * - RedisManager: Cross-server pub/sub messaging
 * - Scheduled tasks: Auto-save, health checks
 *
 * CLOUDNET SERVICE PROPERTIES:
 * - cloudnet.service.name (e.g., "Lobby-1")
 * - cloudnet.service.group (e.g., "Lobby")
 * - cloudnet.service.task (e.g., "Lobby")
 * - cloudnet.service.uid (unique service ID)
 *
 * @author MCBZH
 * @version 2.1.0 - CloudNet 4.0 Edition
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

    // ===== REPOSITORIES =====
    private MySQLPlayerDataRepository playerDataRepository;
    private MySQLPlayerStatsRepository playerStatsRepository;

    // ===== ASYNC EXECUTION =====
    private ScheduledExecutorService asyncExecutor;

    // ===== CLOUDNET INFO =====
    private CloudNetServiceInfo serviceInfo;

    // ===== LIFECYCLE: LOAD =====
    @Override
    public void onLoad() {
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  CorePlugin v" + getDescription().getVersion() + "                  ║");
        getLogger().info("║  MySQL + Redis + CloudNet 4.0          ║");
        getLogger().info("╚════════════════════════════════════════╝");

        // Note: We don't use config.yml - only database.yml
        // Create plugin data folder if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // Detect CloudNet 4.0 environment
        this.serviceInfo = CloudNetServiceInfo.detect(getLogger());
        serviceInfo.logInfo();
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
                subscribeToCloudNetChannels();
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
            if (serviceInfo.isCloudNetService()) {
                getLogger().info("║  Service: " + String.format("%-28s", serviceInfo.getName()) + " ║");
                getLogger().info("║  Group:   " + String.format("%-28s", serviceInfo.getGroup()) + " ║");
                getLogger().info("║  Task:    " + String.format("%-28s", serviceInfo.getTask()) + " ║");
                getLogger().info("║  UID:     " + String.format("%-28s", serviceInfo.getUid()) + " ║");
            }
            getLogger().info("║  Database: MySQL (HikariCP)            ║");
            getLogger().info("║  Cache: Caffeine                       ║");
            getLogger().info("║  Redis: " + String.format("%-29s", (redisManager != null && redisManager.isConnected() ? "Connected" : "Disabled")) + "║");
            getLogger().info("║  Startup: " + elapsed + "ms" + " ".repeat(25 - String.valueOf(elapsed).length()) + "║");
            getLogger().info("╚════════════════════════════════════════╝");

            // Send startup message to Redis
            if (redisManager != null && redisManager.isConnected() && serviceInfo != null) {
                sendServiceStartupMessage();
            }

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

        // Send shutdown message to Redis
        if (redisManager != null && redisManager.isConnected() && serviceInfo != null) {
            sendServiceShutdownMessage();
        }

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

        // Pool stats logging every 5 minutes
        asyncExecutor.scheduleAtFixedRate(() -> {
            getLogger().info("Pool: " + databaseManager.getPoolStats());
        }, 5, 5, TimeUnit.MINUTES);

        // Service heartbeat (if Redis is available)
        if (redisManager != null && redisManager.isConnected()) {
            asyncExecutor.scheduleAtFixedRate(() -> {
                sendServiceHeartbeat();
            }, 10, 10, TimeUnit.SECONDS);
        }
    }

    private void subscribeToCloudNetChannels() {
        if (redisManager == null || !redisManager.isConnected()) {
            return;
        }

        // Subscribe to service events
        redisManager.subscribe("cloudnet:service:start", message ->
                getLogger().info("Service started: " + message)
        );

        redisManager.subscribe("cloudnet:service:stop", message ->
                getLogger().info("Service stopped: " + message)
        );

        // Subscribe to player events
        redisManager.subscribe("player:join", message ->
                getLogger().fine("Player joined network: " + message)
        );

        redisManager.subscribe("player:quit", message ->
                getLogger().fine("Player quit network: " + message)
        );

        // Subscribe to game state updates
        redisManager.subscribe("game:state", message ->
                getLogger().fine("Game state update: " + message)
        );

        getLogger().info("Subscribed to CloudNet Redis channels");
    }

    // ===== CLOUDNET MESSAGING =====

    private void sendServiceStartupMessage() {
        if (redisManager == null || serviceInfo == null || !serviceInfo.isCloudNetService()) {
            return;
        }

        String message = String.format(
                "{\"service\":\"%s\",\"group\":\"%s\",\"event\":\"startup\",\"timestamp\":%d}",
                serviceInfo.getName(),
                serviceInfo.getGroup(),
                System.currentTimeMillis()
        );

        redisManager.publish("cloudnet:service:start", message);
    }

    private void sendServiceShutdownMessage() {
        if (redisManager == null || serviceInfo == null || !serviceInfo.isCloudNetService()) {
            return;
        }

        String message = String.format(
                "{\"service\":\"%s\",\"group\":\"%s\",\"event\":\"shutdown\",\"timestamp\":%d}",
                serviceInfo.getName(),
                serviceInfo.getGroup(),
                System.currentTimeMillis()
        );

        redisManager.publish("cloudnet:service:stop", message);
    }

    private void sendServiceHeartbeat() {
        if (redisManager == null || serviceInfo == null || !serviceInfo.isCloudNetService()) {
            return;
        }

        String message = String.format(
                "{\"service\":\"%s\",\"players\":%d,\"tps\":%.2f,\"timestamp\":%d}",
                serviceInfo.getName(),
                getServer().getOnlinePlayers().size(),
                getServer().getTPS()[0],
                System.currentTimeMillis()
        );

        redisManager.publish("cloudnet:service:heartbeat", message);
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

    public CloudNetServiceInfo getServiceInfo() {
        return serviceInfo;
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

    // ===== CLOUDNET SERVICE INFO CLASS =====

    public static class CloudNetServiceInfo {
        private final String name;
        private final String group;
        private final String task;
        private final String uid;
        private final boolean isCloudNetService;

        private CloudNetServiceInfo(String name, String group, String task, String uid) {
            this.name = name;
            this.group = group;
            this.task = task;
            this.uid = uid;
            this.isCloudNetService = name != null;
        }

        public static CloudNetServiceInfo detect(java.util.logging.Logger logger) {
            try {
                // Use CloudNet 4.0 dependency injection API
                var injectionLayer = eu.cloudnetservice.driver.inject.InjectionLayer.ext();
                var serviceInfoSnapshot = injectionLayer.instance(eu.cloudnetservice.driver.service.ServiceInfoSnapshot.class);

                if (serviceInfoSnapshot != null) {
                    String name = serviceInfoSnapshot.name();
                    String group = serviceInfoSnapshot.configuration().groups().isEmpty()
                            ? "Unknown"
                            : serviceInfoSnapshot.configuration().groups().iterator().next();
                    String task = serviceInfoSnapshot.configuration().serviceId().taskName();
                    String uid = serviceInfoSnapshot.configuration().serviceId().uniqueId().toString();

                    logger.info("✓ Detected CloudNet 4.0 service: " + name);
                    return new CloudNetServiceInfo(name, group, task, uid);
                }
            } catch (NoClassDefFoundError | Exception e) {
                logger.info("CloudNet 4.0 not detected - running in standalone mode");
                logger.fine("Detection error: " + e.getMessage());
            }

            // Not an error - just running standalone
            logger.info("Running in standalone mode (not CloudNet)");
            return new CloudNetServiceInfo(null, null, null, null);
        }

        public void logInfo() {
            if (!isCloudNetService) {
                return;
            }

            java.util.logging.Logger.getLogger("CorePlugin").info("CloudNet Service Info:");
            java.util.logging.Logger.getLogger("CorePlugin").info("  Name:  " + name);
            java.util.logging.Logger.getLogger("CorePlugin").info("  Group: " + group);
            java.util.logging.Logger.getLogger("CorePlugin").info("  Task:  " + task);
            java.util.logging.Logger.getLogger("CorePlugin").info("  UID:   " + uid);
        }

        public String getName() { return name; }
        public String getGroup() { return group; }
        public String getTask() { return task; }
        public String getUid() { return uid; }
        public boolean isCloudNetService() { return isCloudNetService; }
    }
}