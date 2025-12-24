package com.yourserver.battleroyale;

import com.yourserver.battleroyale.command.BattleRoyaleCommand;
import com.yourserver.battleroyale.command.BattleRoyaleDebugCommand;
import com.yourserver.battleroyale.config.BattleRoyaleConfig;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.listener.GameListener;
import com.yourserver.battleroyale.listener.PlayerConnectionListener;
import com.yourserver.battleroyale.redis.RedisGameStateBroadcaster;
import com.yourserver.core.CorePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import com.yourserver.battleroyale.listener.MinimalProtectionListener;

import java.util.logging.Level;

/*
 * BattleRoyale Plugin - Main Class
 *
 * UPDATED FOR GENERIC LOBBY SYSTEM:
 * - Uses RedisGameStateBroadcaster (generic, reusable)
 * - Works with GameLobbyPlugin on lobby servers
 * - Detects CloudNet service name automatically
 * - Broadcasts game state to Redis for real-time GUI updates
 *
 * Game Flow:
 * 1. WAITING - Players join pre-game lobby (broadcasts to Redis)
 * 2. STARTING - Countdown before game starts
 * 3. ACTIVE - Players fight in shrinking zone
 * 4. DEATHMATCH - Final arena phase (after 1 hour)
 * 5. ENDING - Game ends, stats saved, cleanup
 */
public class BattleRoyalePlugin extends JavaPlugin {

    private CorePlugin corePlugin;
    private MiniMessage miniMessage;
    private BattleRoyaleConfig config;
    private GameManager gameManager;
    private GameListener gameListener;
    private RedisGameStateBroadcaster broadcaster;

    @Override
    public void onLoad() {
        getLogger().info("Loading BattleRoyalePlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling BattleRoyalePlugin...");

        getLogger().info("━━━━━━━━ CLOUDNET DEBUG ━━━━━━━━");
        getLogger().info("System Properties:");
        getLogger().info("  cloudnet.service.name = " + System.getProperty("cloudnet.service.name"));
        getLogger().info("  cloudnet.service.group = " + System.getProperty("cloudnet.service.group"));
        getLogger().info("  cloudnet.service.task = " + System.getProperty("cloudnet.service.task"));
        getLogger().info("Environment Variables:");
        getLogger().info("  CLOUDNET_SERVICE_NAME = " + System.getenv("CLOUDNET_SERVICE_NAME"));
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        getLogger().info("━━━━━━━━ CLOUDNET DETECTION DEBUG ━━━━━━━━");
        getLogger().info("Checking ALL possible CloudNet properties...");

// CloudNet 4.0 properties
        String cn4Name = System.getProperty("cloudnet.wrapper.serviceInfo.name");
        String cn4Uid = System.getProperty("cloudnet.wrapper.serviceInfo.uniqueId");
        String cn4Task = System.getProperty("cloudnet.wrapper.serviceInfo.taskName");
        String cn4Node = System.getProperty("cloudnet.wrapper.serviceInfo.nodeUniqueId");

        getLogger().info("CloudNet 4.0 Properties:");
        getLogger().info("  cloudnet.wrapper.serviceInfo.name = " + cn4Name);
        getLogger().info("  cloudnet.wrapper.serviceInfo.uniqueId = " + cn4Uid);
        getLogger().info("  cloudnet.wrapper.serviceInfo.taskName = " + cn4Task);
        getLogger().info("  cloudnet.wrapper.serviceInfo.nodeUniqueId = " + cn4Node);

// CloudNet 3.x properties (legacy)
        String cn3Name = System.getProperty("cloudnet.service.name");
        String cn3Group = System.getProperty("cloudnet.service.group");
        String cn3Task = System.getProperty("cloudnet.service.task");
        String cn3Uid = System.getProperty("cloudnet.service.uid");

        getLogger().info("CloudNet 3.x Properties (legacy):");
        getLogger().info("  cloudnet.service.name = " + cn3Name);
        getLogger().info("  cloudnet.service.group = " + cn3Group);
        getLogger().info("  cloudnet.service.task = " + cn3Task);
        getLogger().info("  cloudnet.service.uid = " + cn3Uid);

// Environment variables
        String envName = System.getenv("CLOUDNET_SERVICE_NAME");
        String envId = System.getenv("CLOUDNET_SERVICE_ID");

        getLogger().info("Environment Variables:");
        getLogger().info("  CLOUDNET_SERVICE_NAME = " + envName);
        getLogger().info("  CLOUDNET_SERVICE_ID = " + envId);

// Server name fallback
        getLogger().info("Server Name (fallback): " + getServer().getName());

// ALL system properties (find any cloudnet related)
        getLogger().info("Searching ALL system properties for 'cloudnet'...");
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.toLowerCase().contains("cloudnet"))
                .forEach(key -> getLogger().info("  " + key + " = " + System.getProperty(key)));

        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

// Try CloudNet API
        getLogger().info("Attempting CloudNet API detection...");
        try {
            Class<?> injectionLayerClass = Class.forName("eu.cloudnetservice.driver.inject.InjectionLayer");
            getLogger().info("  ✓ InjectionLayer class found");

            Object injectionLayer = injectionLayerClass.getMethod("ext").invoke(null);
            getLogger().info("  ✓ InjectionLayer instance obtained");

            Class<?> serviceInfoClass = Class.forName("eu.cloudnetservice.driver.service.ServiceInfoSnapshot");
            getLogger().info("  ✓ ServiceInfoSnapshot class found");

            Object serviceInfo = injectionLayer.getClass()
                    .getMethod("instance", Class.class)
                    .invoke(injectionLayer, serviceInfoClass);

            if (serviceInfo != null) {
                String apiName = (String) serviceInfo.getClass().getMethod("name").invoke(serviceInfo);
                getLogger().info("  ✓ CloudNet API service name: " + apiName);
            } else {
                getLogger().warning("  ✗ ServiceInfoSnapshot is null");
            }

        } catch (ClassNotFoundException e) {
            getLogger().warning("  ✗ CloudNet driver classes not found");
            getLogger().warning("  ✗ This means CloudNet is NOT available");
        } catch (Exception e) {
            getLogger().warning("  ✗ CloudNet API error: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");


        try {
            // 1. Get CorePlugin (required for Redis and player data)
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! BattleRoyalePlugin requires CorePlugin.");
            }
            getLogger().info("✓ CorePlugin found");

            // 2. Initialize MiniMessage
            miniMessage = MiniMessage.miniMessage();

            // 3. Load configuration
            config = BattleRoyaleConfig.load(getDataFolder());
            getLogger().info("✓ Configuration loaded");

            // 4. Initialize game manager
            gameManager = new GameManager(this, corePlugin);
            getLogger().info("✓ Game manager initialized");

            // 5. Initialize generic Redis broadcaster
            if (corePlugin.getRedisManager() != null) {
                broadcaster = new RedisGameStateBroadcaster(
                        this,
                        corePlugin,
                        "battleroyale",  // Gamemode ID (must match GameLobbyPlugin config)
                        "br"             // Channel prefix
                );
                broadcaster.initialize();
                getLogger().info("✓ Generic Redis broadcaster initialized");
            } else {
                getLogger().warning("⚠ Redis not available - lobby GUI will not work!");
            }

            // 6. Register listeners
            gameListener = new GameListener(this, gameManager);

            getServer().getPluginManager().registerEvents(
                    new PlayerConnectionListener(this, gameManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    gameListener,
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new MinimalProtectionListener(gameManager),
                    this
            );
            getLogger().info("✓ Event listeners registered");

            // 7. Register commands
            BattleRoyaleCommand brCommand = new BattleRoyaleCommand(this, gameManager);
            getCommand("battleroyale").setExecutor(brCommand);
            getCommand("battleroyale").setTabCompleter(brCommand);

            BattleRoyaleDebugCommand debugCommand = new BattleRoyaleDebugCommand(this, gameManager);
            getCommand("brdebug").setExecutor(debugCommand);
            getCommand("brdebug").setTabCompleter(debugCommand);

            getLogger().info("✓ Commands registered");

            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("BattleRoyale Plugin enabled successfully!");
            getLogger().info("✓ CloudNet Service: " + getServiceName());
            getLogger().info("✓ Game modes: Solo" + (config.isTeamsEnabled() ? ", Teams" : ""));
            getLogger().info("✓ Max players: " + config.getMaxPlayers());
            getLogger().info("✓ Zone phases: " + config.getZonePhaseCount());
            getLogger().info("✓ Redis broadcasting: " + (broadcaster != null ? "ACTIVE" : "DISABLED"));
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable BattleRoyalePlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling BattleRoyalePlugin...");

        // Stop Redis broadcaster
        if (broadcaster != null) {
            broadcaster.shutdown();
            getLogger().info("✓ Redis broadcaster stopped");
        }

        // Shutdown game manager
        if (gameManager != null) {
            gameManager.shutdown();
            getLogger().info("✓ Game manager shut down");
        }

        getLogger().info("BattleRoyalePlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        config = BattleRoyaleConfig.load(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    /**
     * Gets the CloudNet service name.
     *
     * @return Service name (e.g., "BattleRoyale-1")
     */
    private String getServiceName() {
        String name = System.getProperty("cloudnet.service.name");
        return name != null ? name : "Unknown";
    }

    // ===== PUBLIC API =====

    public CorePlugin getCorePlugin() {
        return corePlugin;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public BattleRoyaleConfig getBRConfig() {
        return config;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public GameListener getGameListener() {
        return gameListener;
    }

    public RedisGameStateBroadcaster getBroadcaster() {
        return broadcaster;
    }
}