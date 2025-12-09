package com.yourserver.lobby.listener;

import com.yourserver.lobby.npc.CustomNPC;
import com.yourserver.lobby.npc.NPCManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Listens for NPC interactions.
 */
public class NPCInteractListener implements Listener {

    private final NPCManager npcManager;

    public NPCInteractListener(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        // Check if entity is a custom NPC
        CustomNPC npc = npcManager.getNPCByEntity(entity);
        if (npc == null) {
            return;
        }

        event.setCancelled(true);

        // Handle NPC interaction
        npcManager.handleInteraction(player, npc);
    }
}