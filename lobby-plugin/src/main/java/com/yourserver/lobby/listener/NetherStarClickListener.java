package com.yourserver.lobby.listener;

import com.yourserver.lobby.gui.GUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for nether star clicks to open cosmetics menu.
 */
public class NetherStarClickListener implements Listener {

    private final GUIManager guiManager;

    public NetherStarClickListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.NETHER_STAR) {
            return;
        }

        // Check if it's a right-click
        if (!event.getAction().isRightClick()) {
            return;
        }

        event.setCancelled(true);

        // Open cosmetics GUI
        guiManager.openCosmetics(player);
    }
}