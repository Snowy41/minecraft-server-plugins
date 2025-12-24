package com.yourserver.gamelobby.manager;

import com.yourserver.gamelobby.GameLobbyPlugin;
import com.yourserver.gamelobby.model.GameService;
import com.yourserver.gamelobby.model.GameService.GameState;
import com.yourserver.gamelobby.model.GamemodeConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * FIXED GameMenuManager
 *
 * Key Fixes:
 * 1. ✅ Store service name in item NBT/lore for reliable extraction
 * 2. ✅ Better click handler with fallback service lookup
 * 3. ✅ Clear error messages for debugging
 */
public class GameMenuManager {

    private final GameLobbyPlugin plugin;
    private final GameServiceManager serviceManager;

    // Currently open menus (key: UUID, value: gamemodeId)
    private final Map<UUID, String> openMenus;

    // Store service names by slot (key: playerUUID + slot, value: serviceName)
    private final Map<String, String> slotServiceMap;

    // Menu refresh interval
    private static final long REFRESH_INTERVAL_TICKS = 40L; // 2 seconds

    public GameMenuManager(@NotNull GameLobbyPlugin plugin, @NotNull GameServiceManager serviceManager) {
        this.plugin = plugin;
        this.serviceManager = serviceManager;
        this.openMenus = new HashMap<>();
        this.slotServiceMap = new HashMap<>();

        // Start menu refresh task
        startMenuRefreshTask();
    }

    /**
     * Opens a gamemode menu for a player.
     */
    public void openGameMenu(@NotNull Player player, @NotNull String gamemodeId) {
        GamemodeConfig config = serviceManager.getGamemodeConfig(gamemodeId);

        if (config == null) {
            player.sendMessage(Component.text("Gamemode not found: " + gamemodeId, NamedTextColor.RED));
            return;
        }

        Inventory inventory = createGameMenu(player, config);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), gamemodeId);

        plugin.getLogger().info("Opened " + gamemodeId + " menu for " + player.getName());
    }

    /**
     * Creates a gamemode menu inventory.
     * FIXED: Now stores service names for reliable click handling.
     */
    @NotNull
    private Inventory createGameMenu(@NotNull Player player, @NotNull GamemodeConfig config) {
        String title = config.getDisplayName() + " §8| Select Server";
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        List<GameService> services = serviceManager.getServices(config.getId());

        plugin.getLogger().info("Creating menu for " + player.getName() +
                " with " + services.size() + " services");

        // Add service items (slots 0-44)
        int slot = 0;
        for (GameService service : services) {
            if (slot >= 45) break; // Leave room for control buttons

            ItemStack item = createServiceItem(service);
            inventory.setItem(slot, item);

            // Store service name for this slot
            String slotKey = player.getUniqueId() + ":" + slot;
            slotServiceMap.put(slotKey, service.getServiceName());

            plugin.getLogger().fine("Slot " + slot + " -> " + service.getServiceName());

            slot++;
        }

        // Add control buttons (bottom row)
        addControlButtons(inventory);

        return inventory;
    }

    /**
     * Creates an item representing a game service.
     * FIXED: Service name now in display name (no color codes to strip).
     */
    @NotNull
    private ItemStack createServiceItem(@NotNull GameService service) {
        Material material = getStateMaterial(service.getState(), service.isOnline());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // FIXED: Simple display name with service name
            meta.setDisplayName("§f" + service.getServiceName());

            // Lore
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Status: " + service.getState().getColoredName());
            lore.add("§7Players: §f" + service.getCurrentPlayers() + "/" + service.getMaxPlayers());

            if (service.getAlivePlayers() > 0) {
                lore.add("§7Alive: §f" + service.getAlivePlayers());
            }

            lore.add("");

            if (!service.isOnline()) {
                lore.add("§c✖ Offline");
            } else if (service.isJoinable()) {
                lore.add("§a✔ Click to join!");
            } else if (service.isFull()) {
                lore.add("§c✖ Server is full");
            } else {
                lore.add("§c✖ Cannot join (game in progress)");
            }

            // HIDDEN: Store service name in last lore line for backup lookup
            lore.add("§0§r" + service.getServiceName());

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Gets the material for a service based on its state.
     */
    @NotNull
    private Material getStateMaterial(@NotNull GameState state, boolean online) {
        if (!online) {
            return Material.GRAY_CONCRETE;
        }

        switch (state) {
            case WAITING:
                return Material.LIME_CONCRETE;
            case STARTING:
                return Material.YELLOW_CONCRETE;
            case ACTIVE:
            case DEATHMATCH:
                return Material.RED_CONCRETE;
            case ENDING:
            case RESTARTING:
                return Material.ORANGE_CONCRETE;
            default:
                return Material.LIGHT_GRAY_CONCRETE;
        }
    }

    /**
     * Adds control buttons to the menu (bottom row).
     */
    private void addControlButtons(@NotNull Inventory inventory) {
        // Refresh button (slot 49)
        ItemStack refresh = new ItemStack(Material.EMERALD);
        ItemMeta refreshMeta = refresh.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName("§a§lRefresh");
            List<String> refreshLore = new ArrayList<>();
            refreshLore.add("");
            refreshLore.add("§7Click to refresh server list");
            refreshMeta.setLore(refreshLore);
            refresh.setItemMeta(refreshMeta);
        }
        inventory.setItem(49, refresh);

        // Back button (slot 45)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lBack");
            List<String> backLore = new ArrayList<>();
            backLore.add("");
            backLore.add("§7Click to close menu");
            backMeta.setLore(backLore);
            back.setItemMeta(backMeta);
        }
        inventory.setItem(45, back);
    }

    /**
     * FIXED: Handles clicking a service item with improved service lookup.
     */
    public void handleServiceClick(@NotNull Player player, int slot, @NotNull ItemStack clickedItem) {
        // Method 1: Lookup by slot
        String slotKey = player.getUniqueId() + ":" + slot;
        String serviceName = slotServiceMap.get(slotKey);

        plugin.getLogger().info("Click at slot " + slot + " -> service: " + serviceName);

        // Method 2: Extract from display name (backup)
        if (serviceName == null && clickedItem.getItemMeta() != null) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                serviceName = displayName.replace("§f", "").trim();
                plugin.getLogger().info("Extracted from display name: " + serviceName);
            }
        }

        // Method 3: Extract from hidden lore (last resort)
        if (serviceName == null && clickedItem.getItemMeta() != null) {
            List<String> lore = clickedItem.getItemMeta().getLore();
            if (lore != null && !lore.isEmpty()) {
                String lastLine = lore.get(lore.size() - 1);
                if (lastLine.startsWith("§0§r")) {
                    serviceName = lastLine.substring(4);
                    plugin.getLogger().info("Extracted from hidden lore: " + serviceName);
                }
            }
        }

        if (serviceName == null || serviceName.isEmpty()) {
            player.sendMessage(Component.text("Could not determine server name!", NamedTextColor.RED));
            plugin.getLogger().warning("Failed to extract service name from item at slot " + slot);
            return;
        }

        // Verify service exists and is joinable
        GameService service = serviceManager.getService(serviceName);
        if (service == null) {
            player.sendMessage(Component.text("That server is no longer available!", NamedTextColor.RED));
            plugin.getLogger().warning("Service not found: " + serviceName);
            player.closeInventory();
            return;
        }

        // Check if joinable
        if (!service.isOnline()) {
            player.sendMessage(Component.text("That server is offline!", NamedTextColor.RED));
            return;
        }

        if (!service.isJoinable()) {
            player.sendMessage(Component.text("That server is not joinable right now!", NamedTextColor.YELLOW));
            return;
        }

        // Connect player
        player.closeInventory();
        player.sendMessage(Component.text("Connecting to " + serviceName + "...", NamedTextColor.GREEN));
        serviceManager.connectPlayer(player, serviceName);
    }

    /**
     * Handles refresh button click.
     */
    public void handleRefresh(@NotNull Player player) {
        String gamemodeId = openMenus.get(player.getUniqueId());
        if (gamemodeId != null) {
            openGameMenu(player, gamemodeId);
            player.sendMessage(Component.text("Server list refreshed!", NamedTextColor.GREEN));
        }
    }

    /**
     * Handles back button click.
     */
    public void handleBack(@NotNull Player player) {
        player.closeInventory();
        openMenus.remove(player.getUniqueId());
    }

    /**
     * Starts periodic menu refresh task.
     */
    private void startMenuRefreshTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                String gamemodeId = openMenus.get(uuid);

                if (gamemodeId != null && player.getOpenInventory().getTopInventory().getHolder() == null) {
                    // Player has menu open, refresh it
                    refreshMenu(player, gamemodeId);
                }
            }
        }, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS);
    }

    /**
     * Refreshes a player's open menu.
     */
    private void refreshMenu(@NotNull Player player, @NotNull String gamemodeId) {
        GamemodeConfig config = serviceManager.getGamemodeConfig(gamemodeId);
        if (config == null) return;

        Inventory currentInventory = player.getOpenInventory().getTopInventory();

        if (!openMenus.containsKey(player.getUniqueId())) {
            return;
        }

        // Clear service slots only (keep control buttons)
        for (int i = 0; i < 45; i++) {
            currentInventory.setItem(i, null);
        }

        // Re-add service items with updated slot mappings
        List<GameService> services = serviceManager.getServices(config.getId());
        int slot = 0;
        for (GameService service : services) {
            if (slot >= 45) break;
            ItemStack item = createServiceItem(service);
            currentInventory.setItem(slot, item);

            // Update slot mapping
            String slotKey = player.getUniqueId() + ":" + slot;
            slotServiceMap.put(slotKey, service.getServiceName());

            slot++;
        }

        player.updateInventory();
    }

    /**
     * Handles inventory close event.
     */
    public void handleClose(@NotNull Player player) {
        openMenus.remove(player.getUniqueId());

        // Clean up slot mappings for this player
        String uuidPrefix = player.getUniqueId() + ":";
        slotServiceMap.keySet().removeIf(key -> key.startsWith(uuidPrefix));
    }

    /**
     * Checks if a player has a menu open.
     */
    public boolean hasMenuOpen(@NotNull Player player) {
        return openMenus.containsKey(player.getUniqueId());
    }

    /**
     * Gets the gamemode ID of a player's open menu.
     */
    @NotNull
    public Optional<String> getOpenGamemode(@NotNull Player player) {
        return Optional.ofNullable(openMenus.get(player.getUniqueId()));
    }
}