package com.yourserver.gamelobby;

import com.yourserver.core.CorePlugin;
import com.yourserver.gamelobby.command.GameMenuCommand;
import com.yourserver.gamelobby.listener.MenuListener;
import com.yourserver.gamelobby.manager.GameMenuManager;
import com.yourserver.gamelobby.manager.GameServiceManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Generic Game Lobby Plugin
 *
 * Works for ANY CloudNet-based gamemode:
 * - BattleRoyale
 * - SkyWars
 * - BedWars
 * - Duels
 * - etc.
 *
 * Features:
 * - Generic GUI system with live updates
 * - Redis pub/sub for real-time state tracking
 * - CloudNet service detection
 * - Configurable gamemode support
 * - Modular and extensible
 *
 * Configuration:
 * Each gamemode has its own config section:
 * - battleroyale.enabled: true
 * - skywars.enabled: true
 * - etc.
 */
public class GameLobbyPlugin extends JavaPlugin {

    private CorePlugin corePlugin;
    private GameServiceManager serviceManager;
    private GameMenuManager menuManager;

    @Override
    public void onLoad() {
        getLogger().info("Loading GameLobbyPlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling GameLobbyPlugin...");

        try {
            // 1. Get CorePlugin (required for Redis)
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! GameLobbyPlugin requires CorePlugin.");
            }
            getLogger().info("✓ CorePlugin found");

            // 2. Check Redis availability
            if (corePlugin.getRedisManager() == null) {
                throw new IllegalStateException("Redis is not connected! This plugin requires Redis for real-time updates.");
            }
            getLogger().info("✓ Redis connected");

            // 3. Initialize service manager (tracks all game services)
            serviceManager = new GameServiceManager(this, corePlugin);
            serviceManager.initialize();
            getLogger().info("✓ Service manager initialized");

            // 4. Initialize menu manager (handles all GUIs)
            menuManager = new GameMenuManager(this, serviceManager);
            getLogger().info("✓ Menu manager initialized");

            // 5. Register listeners
            getServer().getPluginManager().registerEvents(
                    new MenuListener(menuManager),
                    this
            );
            getLogger().info("✓ Event listeners registered");

            // 6. Register commands for each enabled gamemode
            registerGameCommands();
            getLogger().info("✓ Commands registered");

            // 7. Display enabled gamemodes
            logEnabledGamemodes();

            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("Game Lobby Plugin enabled successfully!");
            getLogger().info("✓ Real-time game state updates: ACTIVE");
            getLogger().info("✓ CloudNet integration: READY");
            getLogger().info("✓ Enabled gamemodes: " + serviceManager.getEnabledGamemodes().size());
            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable GameLobbyPlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling GameLobbyPlugin...");

        if (serviceManager != null) {
            serviceManager.shutdown();
            getLogger().info("✓ Service manager shut down");
        }

        getLogger().info("GameLobbyPlugin disabled successfully!");
    }

    /**
     * Registers commands for each enabled gamemode.
     */
    private void registerGameCommands() {
        for (String gamemode : serviceManager.getEnabledGamemodes()) {
            GameMenuCommand command = new GameMenuCommand(menuManager, gamemode);

            // Register main command (e.g., /battleroyale, /skywars)
            getCommand(gamemode).setExecutor(command);
            getCommand(gamemode).setTabCompleter(command);

            getLogger().info("  Registered command: /" + gamemode);
        }
    }

    /**
     * Logs all enabled gamemodes.
     */
    private void logEnabledGamemodes() {
        getLogger().info("Enabled Gamemodes:");
        for (String gamemode : serviceManager.getEnabledGamemodes()) {
            getLogger().info("  - " + gamemode);
        }
    }

    // ===== PUBLIC API =====

    public CorePlugin getCorePlugin() {
        return corePlugin;
    }

    public GameServiceManager getServiceManager() {
        return serviceManager;
    }

    public GameMenuManager getMenuManager() {
        return menuManager;
    }
}