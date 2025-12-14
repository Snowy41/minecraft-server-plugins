package com.yourserver.lobby.listener;

import com.yourserver.social.SocialPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens for player head (friends menu) clicks to open friends GUI.
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

        // Get SocialPlugin
        SocialPlugin socialPlugin = (SocialPlugin) Bukkit.getPluginManager().getPlugin("SocialPlugin");

        if (socialPlugin == null) {
            player.sendMessage("§cFriends system is currently unavailable!");
            return;
        }

        // Open friends GUI
        try {
            socialPlugin.getGuiManager().openFriendsGUI(player);
        } catch (Exception e) {
            player.sendMessage("§cFailed to open friends menu!");
            Bukkit.getLogger().warning("Failed to open friends GUI for " + player.getName() + ": " + e.getMessage());
        }
    }
}