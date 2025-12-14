package com.yourserver.lobby;

import com.destroystokyo.paper.profile.PlayerProfile;
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
            getLogger().info("Event listeners registered");

            // 6. Register commands
            getCommand("lobby").setExecutor(new LobbyCommand(this, spawnManager));
            getCommand("spawn").setExecutor(new SpawnCommand(this, spawnManager));
            getCommand("refreshnametags").setExecutor(new com.yourserver.lobby.command.RefreshNametagsCommand(this));
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
     * DEBUG VERSION: Added extensive logging to track the issue.
     *
     * @param player The player
     */
    public void giveJoinItems(Player player) {
        var joinItemsConfig = lobbyConfig.getJoinItemsConfig();

        if (!joinItemsConfig.isEnabled()) {
            getLogger().info("Join items disabled in config");
            return;
        }

        getLogger().info("Giving join items to " + player.getName());

        // Clear inventory if configured
        if (joinItemsConfig.isClearInventory()) {
            player.getInventory().clear();
            getLogger().info("Cleared inventory");
        }

        // Give configured items
        int itemCount = 0;
        for (var itemConfig : joinItemsConfig.getItems()) {
            itemCount++;
            getLogger().info("Processing item #" + itemCount + ": " + itemConfig.getMaterial() + " at slot " + itemConfig.getSlot());

            try {
                Material material = Material.valueOf(itemConfig.getMaterial());
                getLogger().info("Material parsed successfully: " + material.name());

                if (material == Material.PLAYER_HEAD) {
                    getLogger().info("Detected PLAYER_HEAD - giving with delay");
                    // FIXED: Give player head with a delay to ensure profile is loaded
                    givePlayerHeadDelayed(player, itemConfig);
                } else {
                    getLogger().info("Regular item - giving immediately");
                    // Regular items - give immediately
                    ItemStack item = new ItemBuilder(material)
                            .name(miniMessage.deserialize(itemConfig.getName()))
                            .lore(itemConfig.getLore().stream()
                                    .map(line -> miniMessage.deserialize(line))
                                    .toList())
                            .build();

                    player.getInventory().setItem(itemConfig.getSlot(), item);
                    getLogger().info("Item placed in slot " + itemConfig.getSlot());
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material: " + itemConfig.getMaterial() + " - " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                getLogger().severe("Error giving item: " + e.getMessage());
                e.printStackTrace();
            }
        }

        getLogger().info("Finished processing " + itemCount + " items");
    }

    /**
     * Gives player head with a slight delay to ensure profile is loaded.
     * This fixes the issue where player heads don't show textures on join.
     */
    private void givePlayerHeadDelayed(Player player,
                                       com.yourserver.lobby.config.LobbyConfig.JoinItem itemConfig) {
        getLogger().info("Scheduling delayed player head for " + player.getName() + " at slot " + itemConfig.getSlot());

        // Give the head after a 1-tick delay to ensure the player's profile is fully loaded
        Bukkit.getScheduler().runTaskLater(this, () -> {
            getLogger().info("Delayed task executing for player head");

            if (!player.isOnline()) {
                getLogger().warning("Player " + player.getName() + " went offline before head could be given");
                return;
            }

            try {
                getLogger().info("Creating player head...");
                ItemStack skull = createPlayerHead(player, itemConfig);

                if (skull == null) {
                    getLogger().severe("createPlayerHead returned null!");
                    return;
                }

                getLogger().info("Player head created, placing in slot " + itemConfig.getSlot());
                player.getInventory().setItem(itemConfig.getSlot(), skull);

                getLogger().info("Player head successfully placed!");

                // Verify it's actually there
                ItemStack check = player.getInventory().getItem(itemConfig.getSlot());
                if (check != null && check.getType() == Material.PLAYER_HEAD) {
                    getLogger().info("VERIFIED: Player head is in inventory at slot " + itemConfig.getSlot());
                } else {
                    getLogger().severe("FAILED: Player head not in inventory! Slot contains: " +
                            (check == null ? "null" : check.getType().name()));
                }

            } catch (Exception e) {
                getLogger().severe("Error in delayed player head task: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1L); // 1 tick delay (50ms)
    }

    /**
     * Creates a player head item using Paper's PlayerProfile API.
     * Uses setOwningPlayer which is more reliable than PlayerProfile for online players.
     */
    private ItemStack createPlayerHead(Player player,
                                       com.yourserver.lobby.config.LobbyConfig.JoinItem itemConfig) {
        getLogger().info("createPlayerHead called for " + player.getName());

        try {
            // Create base player head
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            getLogger().info("Created ItemStack with PLAYER_HEAD material");

            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();

            if (skullMeta == null) {
                getLogger().severe("SkullMeta is null!");
                return skull;
            }

            getLogger().info("Got SkullMeta successfully");

            // Use setOwningPlayer - this is the most reliable method for online players
            skullMeta.setOwningPlayer(player);
            getLogger().info("Set owning player to " + player.getName());

            // Set display name
            Component displayName = this.getMiniMessage().deserialize(itemConfig.getName());
            skullMeta.displayName(displayName.decoration(
                    net.kyori.adventure.text.format.TextDecoration.ITALIC, false
            ));
            getLogger().info("Set display name");

            // Set lore
            java.util.List<Component> loreComponents = itemConfig.getLore().stream()
                    .map(line -> this.getMiniMessage().deserialize(line))
                    .map(component -> component.decoration(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC, false
                    ))
                    .toList();
            skullMeta.lore(loreComponents);
            getLogger().info("Set lore with " + loreComponents.size() + " lines");

            // Apply meta
            skull.setItemMeta(skullMeta);
            getLogger().info("Applied skull meta to ItemStack");

            // Verify
            getLogger().info("Final skull type: " + skull.getType().name());
            getLogger().info("Final skull amount: " + skull.getAmount());

            return skull;

        } catch (Exception e) {
            getLogger().severe("Exception in createPlayerHead: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}