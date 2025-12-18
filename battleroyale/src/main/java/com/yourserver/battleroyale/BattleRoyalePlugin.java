package com.yourserver.battleroyale;

import com.yourserver.battleroyale.command.BattleRoyaleCommand;
import com.yourserver.battleroyale.command.BattleRoyaleDebugCommand;
import com.yourserver.battleroyale.config.BattleRoyaleConfig;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.listener.GameListener;
import com.yourserver.battleroyale.listener.PlayerConnectionListener;
import com.yourserver.core.CorePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import com.yourserver.battleroyale.listener.MinimalProtectionListener;


import java.util.logging.Level;

/**
 * BattleRoyale Plugin - Main Class
 *
 * A battle royale game mode supporting 25-100 players.
 *
 * Features:
 * - Pre-game lobby above the map
 * - Random world generation with custom structures
 * - Shrinking zone system
 * - Deathmatch arena after time limit
 * - Solo and team modes
 * - Statistics tracking
 * - Cross-server messaging via Redis
 *
 * Game Flow:
 * 1. WAITING - Players join pre-game lobby
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

    @Override
    public void onLoad() {
        getLogger().info("Loading BattleRoyalePlugin...");

        // Save default config
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

            // 5. Register listeners
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

            // 6. Register commands
            BattleRoyaleCommand brCommand = new BattleRoyaleCommand(this, gameManager);
            getCommand("battleroyale").setExecutor(brCommand);
            getCommand("battleroyale").setTabCompleter(brCommand);

            BattleRoyaleDebugCommand debugCommand = new BattleRoyaleDebugCommand(this, gameManager);
            getCommand("brdebug").setExecutor(debugCommand);
            getCommand("brdebug").setTabCompleter(debugCommand);

            getLogger().info("✓ Commands registered");


            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("BattleRoyale Plugin enabled successfully!");
            getLogger().info("✓ Game modes: Solo" + (config.isTeamsEnabled() ? ", Teams" : ""));
            getLogger().info("✓ Max players: " + config.getMaxPlayers());
            getLogger().info("✓ Zone phases: " + config.getZonePhaseCount());
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable BattleRoyalePlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling BattleRoyalePlugin...");

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

    // ===== Public API =====

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
}