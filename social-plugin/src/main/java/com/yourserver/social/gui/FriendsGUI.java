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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Friends management GUI.
 * Shows online/offline friends and pending requests.
 * IMPROVED: Now includes navigation to Party and Clan menus!
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

        String title = "§9§lYour Friends"; // Using legacy format for title
        this.inventory = Bukkit.createInventory(null, 54, title);

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

        // NEW: Social menu navigation
        renderSocialNavigation();

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
                meta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                prev.setItemMeta(meta);
            }
            inventory.setItem(45, prev);
        }

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("Close", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            close.setItemMeta(closeMeta);
        }
        inventory.setItem(49, close);

        // Next page
        int maxPages = (int) Math.ceil(friends.size() / 21.0);
        if (currentPage < maxPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Next Page", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                next.setItemMeta(meta);
            }
            inventory.setItem(53, next);
        }
    }

    /**
     * NEW: Renders social menu navigation buttons.
     * Allows quick access to Party and Clan menus.
     */
    private void renderSocialNavigation() {
        // Party menu button (slot 46)
        ItemStack partyButton = new ItemStack(Material.EMERALD);
        ItemMeta partyMeta = partyButton.getItemMeta();
        if (partyMeta != null) {
            partyMeta.displayName(Component.text("Party Menu", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> partyLore = new ArrayList<>();
            partyLore.add(Component.empty());
            partyLore.add(Component.text("Click to open party menu", NamedTextColor.GRAY));
            partyLore.add(Component.text("(Coming Soon)", NamedTextColor.DARK_GRAY));

            partyMeta.lore(partyLore);
            partyMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            partyButton.setItemMeta(partyMeta);
        }
        inventory.setItem(46, partyButton);

        // Clan menu button (slot 52)
        ItemStack clanButton = new ItemStack(Material.DIAMOND);
        ItemMeta clanMeta = clanButton.getItemMeta();
        if (clanMeta != null) {
            clanMeta.displayName(Component.text("Clan Menu", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> clanLore = new ArrayList<>();
            clanLore.add(Component.empty());
            clanLore.add(Component.text("Click to open clan menu", NamedTextColor.GRAY));
            clanLore.add(Component.text("(Coming Soon)", NamedTextColor.DARK_GRAY));

            clanMeta.lore(clanLore);
            clanMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            clanButton.setItemMeta(clanMeta);
        }
        inventory.setItem(52, clanButton);
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