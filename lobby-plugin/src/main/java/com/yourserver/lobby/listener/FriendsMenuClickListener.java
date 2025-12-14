package com.yourserver.lobby.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Listens for player head (friends menu) clicks to open friends GUI.
 * Uses reflection to avoid compile-time dependency on SocialPlugin.
 *
 * IMPORTANT: This requires SocialPlugin to be installed and enabled!
 */
public class FriendsMenuClickListener implements Listener {

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }

        // Check if it's a right-click
        if (!event.getAction().isRightClick()) {
            return;
        }

        // Check if the item has a display name (to distinguish from regular player heads)
        if (item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        event.setCancelled(true);

        // Get SocialPlugin using reflection (no compile-time dependency needed)
        Plugin socialPlugin = Bukkit.getPluginManager().getPlugin("SocialPlugin");

        if (socialPlugin == null || !socialPlugin.isEnabled()) {
            player.sendMessage("§cFriends system is currently unavailable!");
            player.sendMessage("§7The SocialPlugin is not loaded yet. Please try again in a moment.");
            return;
        }

        // Open friends GUI using reflection
        try {
            // Call: socialPlugin.getGuiManager().openFriendsGUI(player)
            Method getGuiManager = socialPlugin.getClass().getMethod("getGuiManager");
            Object guiManager = getGuiManager.invoke(socialPlugin);

            Method openFriendsGUI = guiManager.getClass().getMethod("openFriendsGUI", Player.class);
            openFriendsGUI.invoke(guiManager, player);

        } catch (Exception e) {
            player.sendMessage("§cFailed to open friends menu!");
            Bukkit.getLogger().warning("Failed to open friends GUI for " + player.getName() + ": " + e.getMessage());
        }
    }
}