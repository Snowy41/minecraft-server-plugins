package com.yourserver.gamelobby.manager;

import com.yourserver.gamelobby.GameLobbyPlugin;
import com.yourserver.gamelobby.model.GameService;
import com.yourserver.gamelobby.model.GameService.GameState;
import com.yourserver.gamelobby.model.GamemodeConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages GUIs for all gamemodes.
 *
 * Features:
 * - Dynamic GUI generation for any gamemode
 * - Real-time updates (auto-refresh every 2 seconds)
 * - Color-coded server states
 * - Click-to-join functionality
 * - Multi-page support (for many servers)
 */
public class GameMenuManager {

    private final GameLobbyPlugin plugin;
    private final GameServiceManager serviceManager;

    // Currently open menus (key: UUID, value: gamemodeId)
    private final Map<UUID, String> openMenus;

    // Menu refresh interval
    private static final long REFRESH_INTERVAL_TICKS = 40L; // 2 seconds

    public GameMenuManager(@NotNull GameLobbyPlugin plugin, @NotNull GameServiceManager serviceManager) {
        this.plugin = plugin;
        this.serviceManager = serviceManager;
        this.openMenus = new HashMap<>();

        // Start menu refresh task
        startMenuRefreshTask();
    }

    /**
     * Opens a gamemode menu for a player.
     */
    public void openGameMenu(@NotNull Player player, @NotNull String gamemodeId) {
        GamemodeConfig config = serviceManager.getGamemodeConfig(gamemodeId);

        if (config == null) {
            player.sendMessage("§cGamemode not found: " + gamemodeId);
            return;
        }

        Inventory inventory = createGameMenu(config);
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), gamemodeId);

        plugin.getLogger().fine("Opened " + gamemodeId + " menu for " + player.getName());
    }

    /**
     * Creates a gamemode menu inventory.
     */
    @NotNull
    private Inventory createGameMenu(@NotNull GamemodeConfig config) {
        String title = config.getDisplayName() + " §8| Select Server";
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        List<GameService> services = serviceManager.getServices(config.getId());

        // Add service items (slots 0-44)
        int slot = 0;
        for (GameService service : services) {
            if (slot >= 45) break; // Leave room for control buttons

            ItemStack item = createServiceItem(service);
            inventory.setItem(slot, item);
            slot++;
        }

        // Add control buttons (bottom row)
        addControlButtons(inventory);

        return inventory;
    }

    /**
     * Creates an item representing a game service.
     */
    @NotNull
    private ItemStack createServiceItem(@NotNull GameService service) {
        Material material = getStateMaterial(service.getState(), service.isOnline());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Display name
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
     * Handles clicking a service item.
     */
    public void handleServiceClick(@NotNull Player player, @NotNull ItemStack clickedItem) {
        if (clickedItem.getItemMeta() == null) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();
        if (displayName == null || displayName.isEmpty()) return;

        // Extract service name from display name
        String serviceName = displayName.replace("§f", "").trim();

        // Verify service exists and is joinable
        GameService service = serviceManager.getService(serviceName);
        if (service == null) {
            player.sendMessage("§cThat server is no longer available!");
            player.closeInventory();
            return;
        }

        // Connect player
        player.closeInventory();
        serviceManager.connectPlayer(player, serviceName);
    }

    /**
     * Handles refresh button click.
     */
    public void handleRefresh(@NotNull Player player) {
        String gamemodeId = openMenus.get(player.getUniqueId());
        if (gamemodeId != null) {
            openGameMenu(player, gamemodeId);
            player.sendMessage("§aServer list refreshed!");
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

        // In Paper 1.21.4+, we need to track menu ownership differently
        // since getTitle() is deprecated. We use the openMenus map.
        if (!openMenus.containsKey(player.getUniqueId())) {
            return;
        }

        // Clear service slots only (keep control buttons)
        for (int i = 0; i < 45; i++) {
            currentInventory.setItem(i, null);
        }

        // Re-add service items
        List<GameService> services = serviceManager.getServices(config.getId());
        int slot = 0;
        for (GameService service : services) {
            if (slot >= 45) break;
            ItemStack item = createServiceItem(service);
            currentInventory.setItem(slot, item);
            slot++;
        }

        player.updateInventory();
    }

    /**
     * Handles inventory close event.
     */
    public void handleClose(@NotNull Player player) {
        openMenus.remove(player.getUniqueId());
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