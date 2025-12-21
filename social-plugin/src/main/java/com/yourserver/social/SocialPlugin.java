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
 * CloudNet 4.0.0 + MySQL Edition
 *
 * Features:
 * - Friend system (MySQL storage)
 * - Party system (Redis temporary storage)
 * - Clan system (MySQL storage)
 * - Cross-server support via Redis Pub/Sub
 *
 * Dependencies:
 * - CorePlugin (for MySQL database + Redis access)
 *
 * @author MCBZH
 * @version 2.0.0 - CloudNet 4.0.0 Edition
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
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  SocialPlugin v" + getDescription().getVersion() + "                ║");
        getLogger().info("║  CloudNet 4.0.0 + MySQL Edition        ║");
        getLogger().info("╚════════════════════════════════════════╝");

        saveDefaultConfig();
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        try {
            // === PHASE 1: CORE PLUGIN ===
            corePlugin = (CorePlugin) getServer().getPluginManager().getPlugin("CorePlugin");
            if (corePlugin == null) {
                throw new IllegalStateException("CorePlugin not found! SocialPlugin requires CorePlugin.");
            }
            getLogger().info("✓ CorePlugin found");

            // === PHASE 2: CONFIGURATION ===
            miniMessage = MiniMessage.miniMessage();
            config = SocialConfig.load(getDataFolder());
            getLogger().info("✓ Configuration loaded");

            // === PHASE 3: DATABASE ===
            getLogger().info("Initializing MySQL repositories...");

            MySQLFriendRepository friendRepo = new MySQLFriendRepository(
                    corePlugin.getDatabaseManager().getDataSource(),
                    corePlugin.getAsyncExecutor(),
                    getLogger()
            );

            MySQLClanRepository clanRepo = new MySQLClanRepository(
                    corePlugin.getDatabaseManager().getDataSource(),
                    corePlugin.getAsyncExecutor(),
                    getLogger()
            );

            RedisPartyRepository partyRepo = new RedisPartyRepository(
                    corePlugin.getRedisManager()
            );

            getLogger().info("✓ MySQL repositories initialized");

            // === PHASE 4: MESSAGING ===
            messenger = new SocialMessenger(
                    this,
                    corePlugin.getRedisMessenger()
            );
            getLogger().info("✓ Redis messaging initialized");

            // === PHASE 5: MANAGERS ===
            friendManager = new FriendManager(this, friendRepo, messenger);
            partyManager = new PartyManager(this, partyRepo, messenger);
            clanManager = new ClanManager(this, clanRepo, messenger);
            guiManager = new GUIManager(this, friendManager, partyManager, clanManager);
            getLogger().info("✓ Managers initialized");

            // === PHASE 6: LISTENERS ===
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

            // === PHASE 7: COMMANDS ===
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

            // === SUCCESS ===
            long elapsed = System.currentTimeMillis() - startTime;
            getLogger().info("╔════════════════════════════════════════╗");
            getLogger().info("║  ✓ SOCIALPLUGIN ENABLED SUCCESSFULLY   ║");
            getLogger().info("╠════════════════════════════════════════╣");
            getLogger().info("║  Storage: MySQL + Redis                ║");
            getLogger().info("║  CloudNet: 4.0.0                       ║");
            getLogger().info("║  Features: Friends, Parties, Clans     ║");
            getLogger().info("║  Startup: " + elapsed + "ms" + " ".repeat(25 - String.valueOf(elapsed).length()) + "║");
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
        getLogger().info("║  Shutting down SocialPlugin...         ║");
        getLogger().info("╚════════════════════════════════════════╝");

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

        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║  ✓ SocialPlugin disabled successfully  ║");
        getLogger().info("╚════════════════════════════════════════╝");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reloadConfiguration() {
        reloadConfig();
        config = SocialConfig.load(getDataFolder());
        getLogger().info("Configuration reloaded");
    }

    // ===== PUBLIC API =====

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