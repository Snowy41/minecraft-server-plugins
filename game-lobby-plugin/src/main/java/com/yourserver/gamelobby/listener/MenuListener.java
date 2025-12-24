package com.yourserver.gamelobby.listener;

import com.yourserver.gamelobby.manager.GameMenuManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * FIXED MenuListener
 *
 * Key Fix: Pass slot number to handleServiceClick for reliable service lookup
 */
public class MenuListener implements Listener {

    private final GameMenuManager menuManager;

    public MenuListener(@NotNull GameMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        // Check if player has a menu open
        if (!menuManager.hasMenuOpen(player)) return;

        // Cancel event (prevent item movement)
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Material material = clickedItem.getType();
        int slot = event.getSlot();

        // Handle control buttons
        if (material == Material.EMERALD) {
            // Refresh button
            menuManager.handleRefresh(player);
            return;
        }

        if (material == Material.ARROW) {
            // Back button
            menuManager.handleBack(player);
            return;
        }

        // Handle concrete blocks (service items)
        if (material.name().endsWith("_CONCRETE")) {
            // FIXED: Pass slot number for reliable service lookup
            menuManager.handleServiceClick(player, slot, clickedItem);
            return;
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        menuManager.handleClose(player);
    }
}