package com.yourserver.lobby.listener;

import com.yourserver.lobby.gui.GUIManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for compass clicks to open game selector GUI.
 */
public class CompassClickListener implements Listener {

    private final GUIManager guiManager;

    public CompassClickListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.COMPASS) {
            return;
        }

        // Check if it's a right-click
        if (!event.getAction().isRightClick()) {
            return;
        }

        event.setCancelled(true);

        // Open game selector GUI
        guiManager.openGameSelector(player);
    }
}