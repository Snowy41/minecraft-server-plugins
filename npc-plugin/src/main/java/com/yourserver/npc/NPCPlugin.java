package com.yourserver.npc;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.yourserver.npc.api.NPCAPI;
import com.yourserver.npc.command.NPCCommand;
import com.yourserver.npc.editor.NPCEditorManager;
import com.yourserver.npc.listener.NPCEditorListener;
import com.yourserver.npc.listener.NPCInteractListener;
import com.yourserver.npc.listener.PlayerJoinListener;
import com.yourserver.npc.manager.NPCManager;
import com.yourserver.npc.storage.NPCStorage;
import com.yourserver.npc.util.SkinFetcher;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Standalone NPC Plugin - Real player NPCs with skins.
 *
 * Features:
 * - Real player entities (not armor stands!)
 * - Actual player skins from Mojang API
 * - Persistent storage (npcs.yml)
 * - Click actions (GUI, teleport, command, message)
 * - Hologram text above NPCs
 * - Public API for other plugins
 */
public class NPCPlugin extends JavaPlugin {

    private MiniMessage miniMessage;
    private NPCStorage storage;
    private NPCManager npcManager;
    private ProtocolManager protocolManager;
    private NPCAPI npcAPI;
    private NPCEditorManager editorManager;
    private BukkitTask autoSaveTask;

    @Override
    public void onLoad() {
        getLogger().info("Loading NPCPlugin...");

        // Check for ProtocolLib
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("===========================================");
            getLogger().severe("  ProtocolLib is REQUIRED for NPCs!");
            getLogger().severe("  Download: https://ci.dmulloy2.net/job/ProtocolLib/");
            getLogger().severe("===========================================");
            throw new RuntimeException("ProtocolLib not found!");
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabling NPCPlugin...");

        try {
            // 1. Initialize MiniMessage
            miniMessage = MiniMessage.miniMessage();

            // 2. Initialize ProtocolLib
            protocolManager = ProtocolLibrary.getProtocolManager();
            getLogger().info("✓ ProtocolLib integration enabled");

            // 3. Initialize storage
            storage = new NPCStorage(this);
            getLogger().info("✓ Storage initialized");

            // 4. Initialize NPC manager
            npcManager = new NPCManager(this, storage, protocolManager);
            getLogger().info("✓ NPC manager initialized");

            // 5. Load all NPCs from storage
            npcManager.loadAllNPCs();

            // 6. Initialize public API
            npcAPI = new NPCAPI(npcManager);
            getLogger().info("✓ Public API available");
            editorManager = new NPCEditorManager(this);
            // 7. Register listeners
            protocolManager.addPacketListener(new NPCInteractListener(this, npcManager));
            getLogger().info("✓ ProtocolLib packet listener registered");

            getServer().getPluginManager().registerEvents(
                    new NPCEditorListener(this, editorManager, npcManager),
                    this
            );

            getServer().getPluginManager().registerEvents(
                    new PlayerJoinListener(npcManager), this
            );
            getLogger().info("✓ Listeners registered");

            // 8. Register commands
            NPCCommand npcCommand = new NPCCommand(this, npcManager);
            getCommand("npc").setExecutor(npcCommand);
            getCommand("npc").setTabCompleter(npcCommand);
            getLogger().info("✓ Commands registered");

            getLogger().info("NPCPlugin enabled successfully!");
            getLogger().info("✓ Real player NPCs with skins");
            getLogger().info("✓ API available for other plugins");


        } catch (Exception e) {
            getLogger().severe("Failed to enable NPCPlugin!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }

        autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    if (npcManager != null) {
                        npcManager.saveAllNPCs();
                        getLogger().info("Auto-saved NPCs");
                    }
                },
                6000L, // 5 minutes
                6000L
        );
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling NPCPlugin...");
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }

        // 1. Stop look tracking task
        if (npcManager != null) {
            npcManager.stopLookTracking();
            npcManager.removeAllNPCs();
            getLogger().info("✓ All NPCs removed");
        }

        // 2. Clear editor sessions
        if (editorManager != null) {
            editorManager.clearAllSessions();
            getLogger().info("✓ Editor sessions cleared");
        }

        // 3. Clear skin cache
        SkinFetcher.clearCache();
        getLogger().info("✓ Skin cache cleared");

        getLogger().info("NPCPlugin disabled successfully!");
    }

    // ===== Public API =====

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public NPCManager getNPCManager() {
        return npcManager;
    }

    public ProtocolManager getProtocolManager() {
        return protocolManager;
    }

    /**
     * Gets the public API for other plugins to use.
     *
     * Example usage in another plugin:
     * ```java
     * NPCPlugin npcPlugin = (NPCPlugin) Bukkit.getPluginManager().getPlugin("NPCPlugin");
     * NPCAPI api = npcPlugin.getAPI();
     *
     * api.createNPC("my_npc", "Steve", location, player -> {
     *     player.sendMessage("Hello!");
     * });
     * ```
     */
    public NPCAPI getAPI() {
        return npcAPI;
    }
}