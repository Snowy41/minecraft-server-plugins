package com.yourserver.lobby;

import com.yourserver.core.CorePlugin;
import com.yourserver.lobby.command.LobbyCommand;
import com.yourserver.lobby.command.SpawnCommand;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.cosmetics.CosmeticsManager;
import com.yourserver.lobby.gui.GUIManager;
import com.yourserver.lobby.items.ItemToggleManager;
import com.yourserver.lobby.listener.CompassClickListener;
import com.yourserver.lobby.listener.LobbyProtectionListener;
import com.yourserver.lobby.listener.NetherStarClickListener;
import com.yourserver.lobby.listener.PlayerConnectionListener;
import com.yourserver.lobby.scoreboard.ScoreboardManager;
import com.yourserver.lobby.spawn.SpawnManager;
import com.yourserver.lobby.tablist.TabListManager;
import com.yourserver.lobby.time.TimeManager;
import com.yourserver.lobby.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private ItemToggleManager itemToggleManager;
    private TimeManager timeManager;

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
            itemToggleManager = new ItemToggleManager();
            timeManager = new TimeManager(this, lobbyConfig);

            // Set the item giver callback for when items are re-enabled
            itemToggleManager.setItemGiver(this::giveJoinItems);

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
            getServer().getPluginManager().registerEvents(
                    new NetherStarClickListener(guiManager),
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
            timeManager.startTimeTask();
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

        if (itemToggleManager != null) {
            itemToggleManager.shutdown();
            getLogger().info("Item toggle manager shut down");
        }

        if (timeManager != null) {
            timeManager.shutdown();
            getLogger().info("Time manager shut down");
        }

        getLogger().info("LobbyPlugin disabled successfully!");
    }

    /**
     * Called by PartitionPlugin when a player changes partitions.
     * This method is invoked via reflection - NO IMPORTS NEEDED!
     */
    public void onPlayerPartitionChange(Player player, String oldPartition, String newPartition) {
        getLogger().info("Player " + player.getName() +
                " changed partitions: " + oldPartition + " → " + newPartition);

        // Update scoreboard immediately
        if (scoreboardManager != null) {
            scoreboardManager.updateScoreboard(player);
        }

        // Update tab list immediately
        if (tabListManager != null) {
            tabListManager.updatePlayer(player);
        }

        // Optional: Send message to player
        if (newPartition != null) {
            player.sendMessage(miniMessage.deserialize(
                    "<gray>You entered a new partition"
            ));
        }
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

        timeManager.shutdown();
        timeManager = new TimeManager(this, lobbyConfig);
        timeManager.startTimeTask();

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

    public ItemToggleManager getItemToggleManager() {
        return itemToggleManager;
    }

    public TimeManager getTimeManager() {
        return timeManager;
    }

    /**
     * Gives join items to a player.
     * Public method so it can be called by ItemToggleManager.
     *
     * @param player The player
     */
    public void giveJoinItems(Player player) {
        var joinItemsConfig = lobbyConfig.getJoinItemsConfig();

        if (!joinItemsConfig.isEnabled()) {
            return;
        }

        // Clear inventory if configured
        if (joinItemsConfig.isClearInventory()) {
            player.getInventory().clear();
        }

        // Give configured items
        for (var itemConfig : joinItemsConfig.getItems()) {
            try {
                Material material = Material.valueOf(itemConfig.getMaterial());

                ItemStack item = new ItemBuilder(material)
                        .name(miniMessage.deserialize(itemConfig.getName()))
                        .lore(itemConfig.getLore().stream()
                                .map(line -> miniMessage.deserialize(line))
                                .toList())
                        .build();

                player.getInventory().setItem(itemConfig.getSlot(), item);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material: " + itemConfig.getMaterial());
            }
        }
    }
}