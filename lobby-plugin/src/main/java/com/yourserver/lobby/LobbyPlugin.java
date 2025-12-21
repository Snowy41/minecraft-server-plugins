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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main lobby plugin class providing hub functionality:
 * - Spawn management and protection
 * - Flicker-free scoreboards
 * - Dynamic tab list
 * - Custom GUI system
 * - Cosmetics (particle trails, etc.)
 *
 * CLOUDNET 4.0 INTEGRATION:
 * ✅ CloudNet service detection via CorePlugin
 * ✅ Cross-server messaging via Redis
 * ✅ Shared MySQL database
 * ✅ Service-aware player counts
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
    private com.yourserver.lobby.listener.RankDisplayListener rankDisplayListener;

    @Override
    public void onLoad() {
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  LobbyPlugin v" + getDescription().getVersion() + "                  ║");
        getLogger().info("║  CloudNet 4.0 Compatible               ║");
        getLogger().info("╚════════════════════════════════════════╝");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        try {
            // === PHASE 1: CORE PLUGIN REFERENCE ===
            getLogger().info("Connecting to CorePlugin...");
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! LobbyPlugin requires CorePlugin.");
            }

            // Log CloudNet info if available
            var serviceInfo = corePlugin.getServiceInfo();
            if (serviceInfo != null && serviceInfo.isCloudNetService()) {
                getLogger().info("✓ Running on CloudNet service: " + serviceInfo.getName());
                getLogger().info("  Group: " + serviceInfo.getGroup());
                getLogger().info("  Task: " + serviceInfo.getTask());
            } else {
                getLogger().info("✓ Running in standalone mode");
            }

            // === PHASE 2: CONFIGURATION ===
            getLogger().info("Loading configuration...");
            miniMessage = MiniMessage.miniMessage();
            lobbyConfig = LobbyConfig.load(getDataFolder());
            getLogger().info("✓ Configuration loaded");

            // === PHASE 3: MANAGERS ===
            getLogger().info("Initializing managers...");
            spawnManager = new SpawnManager(this, lobbyConfig);
            scoreboardManager = new ScoreboardManager(this, lobbyConfig, corePlugin);
            tabListManager = new TabListManager(this, lobbyConfig);
            guiManager = new GUIManager(this, lobbyConfig, corePlugin);
            cosmeticsManager = new CosmeticsManager(this, lobbyConfig);
            itemToggleManager = new ItemToggleManager();
            timeManager = new TimeManager(this, lobbyConfig);

            // Set the item giver callback for when items are re-enabled
            itemToggleManager.setItemGiver(this::giveJoinItems);

            getLogger().info("✓ All managers initialized");

            // === PHASE 4: LISTENERS ===
            getLogger().info("Registering event listeners...");

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

            // Friends menu integration (optional)
            if (getServer().getPluginManager().isPluginEnabled("SocialPlugin")) {
                getServer().getPluginManager().registerEvents(
                        new com.yourserver.lobby.listener.FriendsMenuClickListener(),
                        this
                );
                getLogger().info("✓ Friends menu integration enabled");
            } else {
                getLogger().warning("SocialPlugin not found - friends menu will not work!");
            }

            getServer().getPluginManager().registerEvents(guiManager, this);

            // Register rank display listener for nametags and tab list
            rankDisplayListener = new com.yourserver.lobby.listener.RankDisplayListener(corePlugin);
            getServer().getPluginManager().registerEvents(rankDisplayListener, this);

            getLogger().info("✓ Event listeners registered");

            // === PHASE 5: COMMANDS ===
            getLogger().info("Registering commands...");
            getCommand("lobby").setExecutor(new LobbyCommand(this, spawnManager));
            getCommand("spawn").setExecutor(new SpawnCommand(this, spawnManager));
            getCommand("refreshnametags").setExecutor(
                    new com.yourserver.lobby.command.RefreshNametagsCommand(this)
            );
            getLogger().info("✓ Commands registered");

            // === PHASE 6: UPDATE TASKS ===
            getLogger().info("Starting update tasks...");
            scoreboardManager.startUpdateTask();
            tabListManager.startUpdateTask();
            cosmeticsManager.startTrailTask();
            timeManager.startTimeTask();
            getLogger().info("✓ Update tasks started");

            // === PHASE 7: REDIS INTEGRATION (Optional) ===
            if (corePlugin.getRedisManager() != null && corePlugin.getRedisManager().isConnected()) {
                setupRedisIntegration();
                getLogger().info("✓ Redis integration enabled");
            } else {
                getLogger().warning("Redis not available - cross-server features disabled");
            }

            // === SUCCESS ===
            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("╔════════════════════════════════════════╗");
            getLogger().info("║  ✓ LOBBYPLUGIN ENABLED SUCCESSFULLY    ║");
            getLogger().info("╠════════════════════════════════════════╣");

            if (serviceInfo != null && serviceInfo.isCloudNetService()) {
                getLogger().info("║  CloudNet Service: " +
                        String.format("%-20s", serviceInfo.getName()) + " ║");
                getLogger().info("║  Group: " +
                        String.format("%-29s", serviceInfo.getGroup()) + " ║");
            }

            getLogger().info("║  Startup: " + elapsed + "ms" +
                    " ".repeat(25 - String.valueOf(elapsed).length()) + "║");
            getLogger().info("╚════════════════════════════════════════╝");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "╔════════════════════════════════════════╗", e);
            getLogger().severe("║  ✗ STARTUP FAILED!                     ║");
            getLogger().severe("║  Check logs for details                ║");
            getLogger().severe("╚════════════════════════════════════════╝");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  Shutting down LobbyPlugin...          ║");
        getLogger().info("╚════════════════════════════════════════╝");

        // Stop update tasks
        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
            getLogger().info("✓ Scoreboard manager shut down");
        }

        if (tabListManager != null) {
            tabListManager.shutdown();
            getLogger().info("✓ Tab list manager shut down");
        }

        if (cosmeticsManager != null) {
            cosmeticsManager.shutdown();
            getLogger().info("✓ Cosmetics manager shut down");
        }

        if (itemToggleManager != null) {
            itemToggleManager.shutdown();
            getLogger().info("✓ Item toggle manager shut down");
        }

        if (timeManager != null) {
            timeManager.shutdown();
            getLogger().info("✓ Time manager shut down");
        }

        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  ✓ LobbyPlugin disabled successfully   ║");
        getLogger().info("╚════════════════════════════════════════╝");
    }

    /**
     * Sets up Redis pub/sub integration for cross-server communication.
     */
    private void setupRedisIntegration() {
        var redisManager = corePlugin.getRedisManager();
        if (redisManager == null) return;

        // Subscribe to lobby-specific channels
        redisManager.subscribe("lobby:player:teleport", message -> {
            getLogger().fine("Received teleport request: " + message);
            // Handle cross-server teleport requests
        });

        redisManager.subscribe("lobby:broadcast", message -> {
            // Broadcast message to all players in this lobby
            Bukkit.broadcast(miniMessage.deserialize(message));
        });

        getLogger().info("Subscribed to Redis channels: lobby:*");
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

    // ===== PUBLIC API FOR OTHER COMPONENTS =====

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
     * Refreshes a player's display (nametag and tab list).
     * Used by RefreshNametagsCommand.
     */
    public void refreshPlayerDisplay(Player player) {
        if (rankDisplayListener != null) {
            rankDisplayListener.updatePlayerDisplay(player);
        }
    }

    /**
     * Gives join items to a player.
     * Handles special case for PLAYER_HEAD with delay.
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

                if (material == Material.PLAYER_HEAD) {
                    // Give player head with delay to ensure profile is loaded
                    givePlayerHeadDelayed(player, itemConfig);
                } else {
                    // Regular items - give immediately
                    ItemStack item = new com.yourserver.lobby.util.ItemBuilder(material)
                            .name(miniMessage.deserialize(itemConfig.getName()))
                            .lore(itemConfig.getLore().stream()
                                    .map(line -> miniMessage.deserialize(line))
                                    .toList())
                            .build();

                    player.getInventory().setItem(itemConfig.getSlot(), item);
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material: " + itemConfig.getMaterial());
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error giving item", e);
            }
        }
    }

    /**
     * Gives player head with a slight delay to ensure profile is loaded.
     */
    private void givePlayerHeadDelayed(Player player,
                                       com.yourserver.lobby.config.LobbyConfig.JoinItem itemConfig) {
        // Give the head after a 1-tick delay
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }

            try {
                ItemStack skull = createPlayerHead(player, itemConfig);
                if (skull != null) {
                    player.getInventory().setItem(itemConfig.getSlot(), skull);
                }
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error giving player head", e);
            }
        }, 1L); // 1 tick delay (50ms)
    }

    /**
     * Creates a player head item using setOwningPlayer.
     */
    private ItemStack createPlayerHead(Player player,
                                       com.yourserver.lobby.config.LobbyConfig.JoinItem itemConfig) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

            if (skullMeta == null) {
                return null;
            }

            // Use setOwningPlayer - most reliable for online players
            skullMeta.setOwningPlayer(player);

            // Set display name
            Component displayName = miniMessage.deserialize(itemConfig.getName());
            skullMeta.displayName(displayName.decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false
            ));

            // Set lore
            java.util.List<Component> loreComponents = itemConfig.getLore().stream()
                    .map(line -> miniMessage.deserialize(line))
                    .map(component -> component.decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false
                    ))
                    .toList();
            skullMeta.lore(loreComponents);

            skull.setItemMeta(skullMeta);
            return skull;

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to create player head", e);
            return null;
        }
    }
}