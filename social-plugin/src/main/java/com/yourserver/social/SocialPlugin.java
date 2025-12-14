package com.yourserver.social;

import com.yourserver.core.CorePlugin;
import com.yourserver.social.command.FriendCommand;
import com.yourserver.social.command.PartyCommand;
import com.yourserver.social.command.ClanCommand;
import com.yourserver.social.config.SocialConfig;
import com.yourserver.social.database.*;
import com.yourserver.social.gui.GUIManager;
import com.yourserver.social.listener.PlayerConnectionListener;
import com.yourserver.social.listener.SocialEventListener;
import com.yourserver.social.manager.ClanManager;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.manager.PartyManager;
import com.yourserver.social.messaging.SocialMessenger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Social Plugin - Friends, Parties, and Clans
 *
 * Features:
 * - Friend system (add/remove/list)
 * - Party system (invite/join/leave/warp)
 * - Clan system (create/invite/join/chat)
 * - Cross-server support via Redis Pub/Sub
 *
 * Dependencies:
 * - CorePlugin (for database/Redis access)
 * - MySQL (persistent storage for friends/clans)
 * - Redis (real-time messaging + party state)
 */
public class SocialPlugin extends JavaPlugin {

    // Core infrastructure
    private CorePlugin corePlugin;
    private MiniMessage miniMessage;
    private SocialConfig config;

    // Messaging
    private SocialMessenger messenger;

    // Managers
    private FriendManager friendManager;
    private PartyManager partyManager;
    private ClanManager clanManager;
    private GUIManager guiManager;

    @Override
    public void onLoad() {
        getLogger().info("Loading SocialPlugin...");
        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling SocialPlugin...");

        try {
            // 1. Get CorePlugin (provides Redis only, NOT database)
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! SocialPlugin requires CorePlugin.");
            }
            getLogger().info("✓ CorePlugin found");

            // 2. Initialize MiniMessage
            miniMessage = MiniMessage.miniMessage();

            // 3. Load configuration
            config = SocialConfig.load(getDataFolder());
            getLogger().info("✓ Configuration loaded");

            // 4. Initialize JSON repositories (NOT MySQL!)
            getLogger().info("Using JSON storage (storage type: " + config.getStorageType() + ")");

            JSONFriendRepository friendRepo = new JSONFriendRepository(getDataFolder(), getLogger());
            JSONClanRepository clanRepo = new JSONClanRepository(getDataFolder(), getLogger());
            RedisPartyRepository partyRepo = new RedisPartyRepository(
                    corePlugin.getRedisManager()
            );
            getLogger().info("✓ JSON repositories initialized");

            // 5. Initialize cross-server messenger
            messenger = new SocialMessenger(
                    this,
                    corePlugin.getRedisMessenger()
            );
            getLogger().info("✓ Redis messaging initialized");

            // 6. Initialize managers
            friendManager = new FriendManager(this, friendRepo, messenger);
            partyManager = new PartyManager(this, partyRepo, messenger);
            clanManager = new ClanManager(this, clanRepo, messenger);
            guiManager = new GUIManager(this, friendManager, partyManager, clanManager);
            getLogger().info("✓ Managers initialized");

            // 7. Register listeners
            getServer().getPluginManager().registerEvents(
                    new PlayerConnectionListener(this, friendManager, partyManager),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new SocialEventListener(this, friendManager, partyManager),
                    this
            );
            getServer().getPluginManager().registerEvents(guiManager, this);
            getLogger().info("✓ Event listeners registered");

            // 8. Register commands
            FriendCommand friendCmd = new FriendCommand(this, friendManager, guiManager);
            getCommand("friend").setExecutor(friendCmd);
            getCommand("friend").setTabCompleter(friendCmd);

            PartyCommand partyCmd = new PartyCommand(this, partyManager, guiManager);
            getCommand("party").setExecutor(partyCmd);
            getCommand("party").setTabCompleter(partyCmd);

            ClanCommand clanCmd = new ClanCommand(this, clanManager, guiManager);
            getCommand("clan").setExecutor(clanCmd);
            getCommand("clan").setTabCompleter(clanCmd);
            getLogger().info("✓ Commands registered");

            getLogger().info("SocialPlugin enabled successfully!");
            getLogger().info("✓ Friends, Parties, and Clans ready!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable SocialPlugin!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling SocialPlugin...");

        // 1. Shutdown managers
        if (partyManager != null) {
            partyManager.shutdown();
            getLogger().info("✓ Party manager shut down");
        }

        if (friendManager != null) {
            friendManager.shutdown();
            getLogger().info("✓ Friend manager shut down");
        }

        if (clanManager != null) {
            clanManager.shutdown();
            getLogger().info("✓ Clan manager shut down");
        }

        // 2. Shutdown messenger
        if (messenger != null) {
            messenger.shutdown();
            getLogger().info("✓ Messenger shut down");
        }

        getLogger().info("SocialPlugin disabled successfully!");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        config = SocialConfig.load(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    // ===== Public API =====

    public CorePlugin getCorePlugin() {
        return corePlugin;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public SocialConfig getSocialConfig() {
        return config;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public SocialMessenger getMessenger() {
        return messenger;
    }
}