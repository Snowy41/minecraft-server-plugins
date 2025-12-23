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