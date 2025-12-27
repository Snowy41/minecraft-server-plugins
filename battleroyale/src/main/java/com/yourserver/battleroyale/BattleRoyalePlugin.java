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

import java.io.File;
import java.nio.file.Files;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
 * CLOUDNET DETECTION FIXED:
 * - Removed failing reflection-based API detection
 * - Kept all working detection methods
 * - Added wrapper.json file reading (CloudNet 4.0 standard)
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
    private String serviceName;

    @Override
    public void onLoad() {
        getLogger().info("Loading BattleRoyalePlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling BattleRoyalePlugin...");

        // Detect CloudNet service name FIRST
        serviceName = detectCloudNetServiceName();

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
            getLogger().info("✓ CloudNet Service: " + serviceName);
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
     * Detects the CloudNet service name using multiple methods.
     * Tries various detection methods in order of reliability.
     *
     * @return Service name (e.g., "BattleRoyale-1") or "Unknown" if not detected
     */
    private String detectCloudNetServiceName() {
        getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().info("Detecting CloudNet service name...");

        // METHOD 1: Try wrapper.json file (CloudNet 4.0 standard method)
        String name = readServiceNameFromWrapperJson();
        if (name != null) {
            getLogger().info("✓ Service detected via wrapper.json: " + name);
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return name;
        }

        // METHOD 2: CloudNet 4.0 system properties
        name = System.getProperty("cloudnet.wrapper.serviceInfo.name");
        if (name != null && !name.isEmpty()) {
            getLogger().info("✓ Service detected via system property (CN 4.0): " + name);
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return name;
        }

        // METHOD 3: CloudNet 3.x system properties (legacy)
        name = System.getProperty("cloudnet.service.name");
        if (name != null && !name.isEmpty()) {
            getLogger().info("✓ Service detected via system property (CN 3.x): " + name);
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return name;
        }

        // METHOD 4: Environment variables
        name = System.getenv("CLOUDNET_SERVICE_NAME");
        if (name != null && !name.isEmpty()) {
            getLogger().info("✓ Service detected via environment variable: " + name);
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return name;
        }

        // METHOD 5: Try CloudNet Wrapper API (non-reflection, proper API)
        name = tryWrapperConfigurationApi();
        if (name != null) {
            getLogger().info("✓ Service detected via CloudNet Wrapper API: " + name);
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return name;
        }

        // FAILED: No CloudNet detection method worked
        getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        getLogger().warning("✗ CloudNet service name NOT detected!");
        getLogger().warning("  Tried:");
        getLogger().warning("    1. wrapper.json file");
        getLogger().warning("    2. System properties (CN 4.0)");
        getLogger().warning("    3. System properties (CN 3.x)");
        getLogger().warning("    4. Environment variables");
        getLogger().warning("    5. CloudNet Wrapper API");
        getLogger().warning("  Using fallback: Unknown");
        getLogger().warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return "Unknown";
    }

    /**
     * Reads service name from wrapper.json file (CloudNet 4.0 standard method).
     *
     * @return Service name or null if not found
     */
    private String readServiceNameFromWrapperJson() {
        try {
            File wrapperFile = new File("wrapper.json");
            if (!wrapperFile.exists()) {
                getLogger().fine("  wrapper.json not found");
                return null;
            }

            String content = new String(Files.readAllBytes(wrapperFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            // CloudNet 4.0 wrapper.json structure
            if (json.has("serviceName")) {
                String serviceName = json.get("serviceName").getAsString();

                // Log what we found
                getLogger().info("  wrapper.json found:");
                getLogger().info("    serviceName: " + serviceName);

                // If we have taskName and serviceId, construct the full name
                if (json.has("taskName") && json.has("taskServiceId")) {
                    String taskName = json.get("taskName").getAsString();
                    int serviceId = json.get("taskServiceId").getAsInt();
                    String splitter = json.has("nameSplitter") ? json.get("nameSplitter").getAsString() : "-";

                    String constructedName = taskName + splitter + serviceId;

                    getLogger().info("    taskName: " + taskName);
                    getLogger().info("    taskServiceId: " + serviceId);
                    getLogger().info("    nameSplitter: " + splitter);
                    getLogger().info("    constructed: " + constructedName);

                    return constructedName;
                }

                return serviceName;
            }

        } catch (Exception e) {
            getLogger().fine("  Error reading wrapper.json: " + e.getMessage());
        }

        return null;
    }

    /**
     * Tries to use CloudNet's proper WrapperConfiguration API (non-reflection).
     * This is the official CloudNet 4.0 API for getting service info.
     *
     * @return Service name or null if API not available
     */
    private String tryWrapperConfigurationApi() {
        try {
            // Try to load CloudNet's Wrapper API class
            Class<?> wrapperConfigClass = Class.forName("eu.cloudnetservice.wrapper.configuration.WrapperConfiguration");

            // Get the singleton instance
            Object wrapperConfig = wrapperConfigClass.getMethod("instance").invoke(null);

            if (wrapperConfig != null) {
                // Get ServiceInfoSnapshot
                Object serviceInfo = wrapperConfigClass.getMethod("serviceInfoSnapshot").invoke(wrapperConfig);

                if (serviceInfo != null) {
                    // Get the service name
                    String name = (String) serviceInfo.getClass().getMethod("name").invoke(serviceInfo);

                    if (name != null && !name.isEmpty()) {
                        getLogger().info("  CloudNet Wrapper API available");
                        return name;
                    }
                }
            }

        } catch (ClassNotFoundException e) {
            getLogger().fine("  CloudNet Wrapper API not available (class not found)");
        } catch (Exception e) {
            getLogger().fine("  CloudNet Wrapper API error: " + e.getMessage());
        }

        return null;
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
    public String getServiceName() {
        return serviceName != null ? serviceName : "Unknown";
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