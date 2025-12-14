package com.yourserver.social.gui;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Friends management GUI.
 * Shows online/offline friends and pending requests.
 */
public class FriendsGUI {

    private final SocialPlugin plugin;
    private final FriendManager friendManager;
    private final Player player;
    private final Inventory inventory;

    private int currentPage = 0;
    private List<Friend> friends = new ArrayList<>();
    private List<FriendRequest> requests = new ArrayList<>();

    public FriendsGUI(SocialPlugin plugin, FriendManager friendManager, Player player) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.player = player;

        String title = plugin.getSocialConfig().getMessagesConfig().getPrefix() + "Your Friends";
        this.inventory = Bukkit.createInventory(null, 54, Component.text(title));

        loadData();
    }

    /**
     * Loads friend data asynchronously.
     */
    private void loadData() {
        friendManager.getFriends(player.getUniqueId()).thenAccept(friendsList -> {
            this.friends = friendsList;

            friendManager.getPendingRequests(player.getUniqueId()).thenAccept(requestsList -> {
                this.requests = requestsList;

                Bukkit.getScheduler().runTask(plugin, this::render);
            });
        });
    }

    /**
     * Renders the GUI.
     */
    private void render() {
        inventory.clear();

        // Header
        setHeader();

        // Friend requests section (if any)
        if (!requests.isEmpty()) {
            renderRequests();
        }

        // Friends list
        renderFriends();

        // Navigation
        renderNavigation();

        // Fill empty slots
        fillEmpty();
    }

    /**
     * Sets the header items.
     */
    private void setHeader() {
        // Friends count (slot 4)
        ItemStack friendsCount = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = friendsCount.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Your Friends", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Total Friends: " + friends.size(), NamedTextColor.WHITE));

            long onlineCount = friends.stream()
                    .filter(f -> Bukkit.getPlayer(f.getFriendUuid()) != null)
                    .count();
            lore.add(Component.text("Online: " + onlineCount, NamedTextColor.GREEN));
            lore.add(Component.text("Offline: " + (friends.size() - onlineCount), NamedTextColor.GRAY));

            meta.lore(lore);
            friendsCount.setItemMeta(meta);
        }
        inventory.setItem(4, friendsCount);

        // Pending requests (slot 8)
        if (!requests.isEmpty()) {
            ItemStack requestsItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta requestsMeta = requestsItem.getItemMeta();
            if (requestsMeta != null) {
                requestsMeta.displayName(Component.text("Pending Requests (" + requests.size() + ")",
                                NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Click to view requests!", NamedTextColor.GRAY));
                requestsMeta.lore(lore);
                requestsItem.setItemMeta(requestsMeta);
            }
            inventory.setItem(8, requestsItem);
        }
    }

    /**
     * Renders friend requests.
     */
    private void renderRequests() {
        int slot = 18;
        for (FriendRequest request : requests) {
            if (slot >= 27) break;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            if (skull.getItemMeta() instanceof SkullMeta skullMeta) {
                Player requester = Bukkit.getPlayer(request.getFromUuid());
                if (requester != null) {
                    skullMeta.setOwningPlayer(requester);
                }

                skullMeta.displayName(Component.text(request.getFromName(), NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.empty());
                lore.add(Component.text("Friend Request", NamedTextColor.GRAY));
                lore.add(Component.empty());
                lore.add(Component.text("Left Click: Accept", NamedTextColor.GREEN));
                lore.add(Component.text("Right Click: Deny", NamedTextColor.RED));

                skullMeta.lore(lore);
                skull.setItemMeta(skullMeta);
            }

            inventory.setItem(slot++, skull);
        }
    }

    /**
     * Renders friends list.
     */
    private void renderFriends() {
        int startSlot = requests.isEmpty() ? 18 : 27;
        int slot = startSlot;
        int startIndex = currentPage * 21;

        for (int i = startIndex; i < friends.size() && i < startIndex + 21; i++) {
            if (slot >= 45) break;

            Friend friend = friends.get(i);
            ItemStack skull = createFriendItem(friend);
            inventory.setItem(slot++, skull);
        }
    }

    /**
     * Creates a friend item.
     */
    private ItemStack createFriendItem(Friend friend) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        if (skull.getItemMeta() instanceof SkullMeta skullMeta) {
            Player friendPlayer = Bukkit.getPlayer(friend.getFriendUuid());
            boolean online = friendPlayer != null;

            if (online) {
                skullMeta.setOwningPlayer(friendPlayer);
            }

            skullMeta.displayName(Component.text(friend.getFriendName(),
                            online ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                            TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Status: " + (online ? "Online" : "Offline"),
                    online ? NamedTextColor.GREEN : NamedTextColor.GRAY));

            if (online) {
                String server = plugin.getMessenger().getPlayerServer(friend.getFriendUuid());
                if (server != null) {
                    lore.add(Component.text("Server: " + server, NamedTextColor.YELLOW));
                }
            }

            lore.add(Component.empty());
            lore.add(Component.text("Friends since: " + formatDate(friend.getSince()), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Right Click: Remove Friend", NamedTextColor.RED));

            skullMeta.lore(lore);
            skull.setItemMeta(skullMeta);
        }

        return skull;
    }

    /**
     * Renders navigation buttons.
     */
    private void renderNavigation() {
        // Previous page
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW));
                prev.setItemMeta(meta);
            }
            inventory.setItem(45, prev);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("Close", NamedTextColor.RED));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(49, close);

        // Next page
        int maxPages = (int) Math.ceil(friends.size() / 21.0);
        if (currentPage < maxPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Next Page", NamedTextColor.YELLOW));
                next.setItemMeta(meta);
            }
            inventory.setItem(53, next);
        }
    }

    /**
     * Fills empty slots with glass pane.
     */
    private void fillEmpty() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
        }

        // Fill top and bottom rows
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
            if (inventory.getItem(45 + i) == null) {
                inventory.setItem(45 + i, filler);
            }
        }
    }

    /**
     * Formats an instant to a readable date.
     */
    private String formatDate(java.time.Instant instant) {
        java.time.LocalDateTime date = java.time.LocalDateTime.ofInstant(
                instant, java.time.ZoneId.systemDefault());
        return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }

    /**
     * Opens the GUI for the player.
     */
    public void open() {
        player.openInventory(inventory);
    }

    public Inventory getInventory() {
        return inventory;
    }
}