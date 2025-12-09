package com.yourserver.lobby;

import com.yourserver.core.CorePlugin;
import com.yourserver.lobby.command.LobbyCommand;
import com.yourserver.lobby.command.SpawnCommand;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.cosmetics.CosmeticsManager;
import com.yourserver.lobby.gui.GUIManager;
import com.yourserver.lobby.listener.CompassClickListener;
import com.yourserver.lobby.listener.LobbyProtectionListener;
import com.yourserver.lobby.listener.PlayerConnectionListener;
import com.yourserver.lobby.scoreboard.ScoreboardManager;
import com.yourserver.lobby.spawn.SpawnManager;
import com.yourserver.lobby.tablist.TabListManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main lobby plugin class providing hub functionality:
 * - Spawn management and protection
 * - Flicker-free scoreboards
 * - Dynamic tab list
 * - Custom GUI system
 * - Cosmetics (particle trails, etc.)
 */
public class LobbyPlugin extends JavaPlugin {

    // Core plugin reference
    private CorePlugin corePlugin;

    // Configuration
    private LobbyConfig lobbyConfig;
    private MiniMessage miniMessage;

    // Managers
    private SpawnManager spawnManager;
    private ScoreboardManager scoreboardManager;
    private TabListManager tabListManager;
    private GUIManager guiManager;
    private CosmeticsManager cosmeticsManager;

    @Override
    public void onLoad() {
        getLogger().info("Loading LobbyPlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling LobbyPlugin...");

        try {
            // 1. Get CorePlugin reference
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! LobbyPlugin requires CorePlugin.");
            }
            getLogger().info("CorePlugin found and loaded");

            // 2. Initialize MiniMessage
            miniMessage = MiniMessage.miniMessage();

            // 3. Load configuration
            lobbyConfig = LobbyConfig.load(getDataFolder());
            getLogger().info("Configuration loaded");

            // 4. Initialize managers
            spawnManager = new SpawnManager(this, lobbyConfig);
            scoreboardManager = new ScoreboardManager(this, lobbyConfig, corePlugin);
            tabListManager = new TabListManager(this, lobbyConfig);
            guiManager = new GUIManager(this, lobbyConfig, corePlugin);
            cosmeticsManager = new CosmeticsManager(this, lobbyConfig);
            getLogger().info("All managers initialized");

            // 5. Register listeners
            getServer().getPluginManager().registerEvents(
                    new PlayerConnectionListener(this, spawnManager, scoreboardManager,
                            tabListManager, cosmeticsManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new LobbyProtectionListener(lobbyConfig),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new CompassClickListener(guiManager),
                    this
            );
            getServer().getPluginManager().registerEvents(guiManager, this);
            getLogger().info("Event listeners registered");

            // 6. Register commands
            getCommand("lobby").setExecutor(new LobbyCommand(this, spawnManager));
            getCommand("spawn").setExecutor(new SpawnCommand(this, spawnManager));
            getLogger().info("Commands registered");

            // 7. Start update tasks
            scoreboardManager.startUpdateTask();
            tabListManager.startUpdateTask();
            cosmeticsManager.startTrailTask();
            getLogger().info("Update tasks started");

            getLogger().info("LobbyPlugin enabled successfully!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable LobbyPlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling LobbyPlugin...");

        // Stop update tasks
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
            getLogger().info("Scoreboard manager shut down");
        }

        if (tabListManager != null) {
            tabListManager.shutdown();
            getLogger().info("Tab list manager shut down");
        }

        if (cosmeticsManager != null) {
            cosmeticsManager.shutdown();
            getLogger().info("Cosmetics manager shut down");
        }

        getLogger().info("LobbyPlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        lobbyConfig = LobbyConfig.load(getDataFolder());

        // Restart managers with new config
        scoreboardManager.shutdown();
        scoreboardManager = new ScoreboardManager(this, lobbyConfig, corePlugin);
        scoreboardManager.startUpdateTask();

        tabListManager.shutdown();
        tabListManager = new TabListManager(this, lobbyConfig);
        tabListManager.startUpdateTask();

        cosmeticsManager.shutdown();
        cosmeticsManager = new CosmeticsManager(this, lobbyConfig);
        cosmeticsManager.startTrailTask();

        getLogger().info("Configuration reloaded");
    }

    // ===== Public API for other components =====

    public CorePlugin getCorePlugin() {
        return corePlugin;
    }

    public LobbyConfig getLobbyConfig() {
        return lobbyConfig;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public TabListManager getTabListManager() {
        return tabListManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public CosmeticsManager getCosmeticsManager() {
        return cosmeticsManager;
    }
}