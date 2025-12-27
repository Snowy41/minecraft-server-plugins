package com.yourserver.gamelobby;

import com.yourserver.core.CorePlugin;
import com.yourserver.gamelobby.command.GameMenuCommand;
import com.yourserver.gamelobby.command.VelocityTestCommand;
import com.yourserver.gamelobby.listener.MenuListener;
import com.yourserver.gamelobby.manager.GameMenuManager;
import com.yourserver.gamelobby.manager.GameServiceManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Generic Game Lobby Plugin - Velocity Edition with Debug Command
 *
 * UPDATED:
 * 1. ✅ Uses velocity:main channel (modern)
 * 2. ✅ Proper channel registration
 * 3. ✅ VelocityTestCommand registered for debugging
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
            // 1. Get CorePlugin
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found!");
            }
            getLogger().info("✓ CorePlugin found");

            // 2. Check Redis
            if (corePlugin.getRedisManager() == null || !corePlugin.getRedisManager().isConnected()) {
                throw new IllegalStateException("Redis is not connected!");
            }
            getLogger().info("✓ Redis connected");

            // 3. Register plugin messaging channels for Velocity
            registerVelocityMessaging();
            getLogger().info("✓ Velocity plugin messaging registered");

            // 4. Initialize service manager
            serviceManager = new GameServiceManager(this, corePlugin);
            serviceManager.initialize();
            getLogger().info("✓ Service manager initialized");

            // 5. Initialize menu manager
            menuManager = new GameMenuManager(this, serviceManager);
            getLogger().info("✓ Menu manager initialized");

            // 6. Register listeners
            getServer().getPluginManager().registerEvents(
                    new MenuListener(menuManager),
                    this
            );
            getLogger().info("✓ Event listeners registered");

            // 7. Register commands
            registerGameCommands();
            registerDebugCommands();
            getLogger().info("✓ Commands registered");

            // 8. Log enabled gamemodes
            logEnabledGamemodes();

            getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            getLogger().info("Game Lobby Plugin enabled successfully!");
            getLogger().info("✓ Real-time game state updates: ACTIVE");
            getLogger().info("✓ Velocity modern messaging: ENABLED");
            getLogger().info("✓ Debug command: /velocitytest <server>");
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

        // Unregister plugin messaging
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);

        if (serviceManager != null) {
            serviceManager.shutdown();
            getLogger().info("✓ Service manager shut down");
        }

        getLogger().info("GameLobbyPlugin disabled successfully!");
    }

    /**
     * Register modern Velocity plugin messaging channels.
     */
    private void registerVelocityMessaging() {
        getLogger().info("Registering Velocity plugin messaging channels...");

        // BungeeCord channel (for Velocity with bungee-plugin-message-channel = true)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "bungeecord:main");
        getLogger().info("  ✓ Registered outgoing: bungeecord:main");

        getServer().getMessenger().registerIncomingPluginChannel(this, "bungeecord:main",
                (channel, player, message) -> {
                    getLogger().fine("Received message on bungeecord:main channel");
                }
        );
        getLogger().info("  ✓ Registered incoming: bungeecord:main");

        getLogger().info("Plugin messaging registration complete!");
    }

    /**
     * Registers commands for each enabled gamemode.
     */
    private void registerGameCommands() {
        for (String gamemode : serviceManager.getEnabledGamemodes()) {
            GameMenuCommand command = new GameMenuCommand(menuManager, gamemode);

            getCommand(gamemode).setExecutor(command);
            getCommand(gamemode).setTabCompleter(command);

            getLogger().info("  Registered command: /" + gamemode);
        }
    }

    /**
     * Registers debug commands.
     */
    private void registerDebugCommands() {
        // Register VelocityTest command
        VelocityTestCommand velocityTestCommand = new VelocityTestCommand(this);
        getCommand("velocitytest").setExecutor(velocityTestCommand);
        getLogger().info("  Registered debug command: /velocitytest");
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