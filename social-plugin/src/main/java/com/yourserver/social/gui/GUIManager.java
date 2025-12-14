package com.yourserver.social.gui;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.manager.ClanManager;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.manager.PartyManager;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all social GUIs.
 * FIXED: Now properly prevents ALL item interactions in GUIs.
 */
public class GUIManager implements Listener {

    private final SocialPlugin plugin;
    private final FriendManager friendManager;
    private final PartyManager partyManager;
    private final ClanManager clanManager;

    // Track open GUIs
    private final Map<UUID, FriendsGUI> openFriendsGUIs = new HashMap<>();
    private final Map<UUID, Inventory> trackedInventories = new HashMap<>();

    public GUIManager(@NotNull SocialPlugin plugin,
                      @NotNull FriendManager friendManager,
                      @NotNull PartyManager partyManager,
                      @NotNull ClanManager clanManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.partyManager = partyManager;
        this.clanManager = clanManager;
    }

    /**
     * Opens the friends GUI for a player.
     */
    public void openFriendsGUI(@NotNull Player player) {
        FriendsGUI gui = new FriendsGUI(plugin, friendManager, player);
        openFriendsGUIs.put(player.getUniqueId(), gui);
        trackedInventories.put(player.getUniqueId(), gui.getInventory());
        gui.open();
    }

    /**
     * Opens the party GUI for a player.
     */
    public void openPartyGUI(@NotNull Player player) {
        player.sendMessage("§eParty GUI coming soon! Use §f/party §ecommands for now.");
    }

    /**
     * Opens the clan GUI for a player.
     */
    public void openClanGUI(@NotNull Player player) {
        player.sendMessage("§eClan GUI coming soon! Use §f/clan §ecommands for now.");
    }

    /**
     * FIXED: Now uses HIGHEST priority and cancels ALL interactions.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if this is a tracked social GUI
        Inventory trackedInv = trackedInventories.get(player.getUniqueId());
        if (trackedInv == null) {
            return;
        }

        // CRITICAL: Cancel ALL interactions in social GUIs
        // This includes:
        // - Clicking items in the GUI
        // - Clicking items in player inventory while GUI is open
        // - Shift-clicking
        // - Number key clicks
        // - Drop key while GUI is open
        event.setCancelled(true);

        // Only process clicks if they're in the actual GUI inventory
        if (!event.getInventory().equals(trackedInv)) {
            return; // Click was in player inventory, just cancel and ignore
        }

        // Process the GUI click
        FriendsGUI friendsGUI = openFriendsGUIs.get(player.getUniqueId());
        if (friendsGUI == null) {
            return;
        }

        int slot = event.getRawSlot();

        // Ignore clicks on empty slots
        if (event.getCurrentItem() == null) {
            return;
        }

        // Handle click based on slot and item
        handleFriendsGUIClick(player, friendsGUI, slot, event.isRightClick());
    }

    /**
     * FIXED: Also prevent dragging items in GUIs.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Check if this is a tracked social GUI
        Inventory trackedInv = trackedInventories.get(player.getUniqueId());
        if (trackedInv == null) {
            return;
        }

        // Cancel ALL drag events in social GUIs
        event.setCancelled(true);
    }

    /**
     * Handles clicks in the friends GUI.
     */
    private void handleFriendsGUIClick(Player player, FriendsGUI gui, int slot, boolean rightClick) {
        // Close button (slot 49)
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Navigation buttons (slots 45, 53)
        if (slot == 45 || slot == 53) {
            // TODO: Implement pagination when FriendsGUI supports it
            return;
        }

        // Friend request slots (18-26)
        if (slot >= 18 && slot < 27) {
            friendManager.getPendingRequests(player.getUniqueId()).thenAccept(requests -> {
                int index = slot - 18;
                if (index < requests.size()) {
                    FriendRequest request = requests.get(index);

                    if (rightClick) {
                        // Deny request
                        friendManager.denyFriendRequest(player.getUniqueId(), request.getFromUuid());
                        player.closeInventory();
                        player.sendMessage("§7Friend request denied.");
                    } else {
                        // Accept request
                        friendManager.acceptFriendRequest(player, request.getFromUuid()).thenAccept(result -> {
                            player.closeInventory();

                            if (result == FriendManager.RequestResult.SUCCESS) {
                                player.sendMessage(plugin.getMiniMessage().deserialize(
                                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                                plugin.getSocialConfig().getMessagesConfig().getFriendAdded()
                                                        .replace("{player}", request.getFromName())
                                ));
                            }
                        });
                    }
                }
            });
            return;
        }

        // Friend list slots (27-44)
        if (slot >= 27 && slot < 45) {
            if (rightClick) {
                friendManager.getFriends(player.getUniqueId()).thenAccept(friends -> {
                    int index = slot - 27;
                    if (index < friends.size()) {
                        Friend friend = friends.get(index);

                        // Remove friend
                        friendManager.removeFriend(player, friend.getFriendUuid()).thenAccept(success -> {
                            player.closeInventory();

                            if (success) {
                                player.sendMessage(plugin.getMiniMessage().deserialize(
                                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                                plugin.getSocialConfig().getMessagesConfig().getFriendRemoved()
                                                        .replace("{player}", friend.getFriendName())
                                ));
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * Cleans up when inventory is closed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Clean up tracked inventories
        trackedInventories.remove(uuid);

        // Clean up specific GUI types
        FriendsGUI friendsGUI = openFriendsGUIs.get(uuid);
        if (friendsGUI != null && event.getInventory().equals(friendsGUI.getInventory())) {
            openFriendsGUIs.remove(uuid);
        }
    }
}