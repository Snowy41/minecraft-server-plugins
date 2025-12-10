package com.yourserver.npc.listener;

import com.yourserver.npc.manager.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final NPCManager npcManager;

    public PlayerJoinListener(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Spawn all NPCs for the joining player (with delay for world load)
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("NPCPlugin"),
                () -> {
                    npcManager.getAllNPCs().forEach(npc ->
                            npcManager.spawnNPCForPlayer(event.getPlayer(), npc)
                    );
                },
                20L // 1 second delay
        );
    }
}