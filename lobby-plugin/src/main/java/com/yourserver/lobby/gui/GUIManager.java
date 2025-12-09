package com.yourserver.lobby.gui;

import com.yourserver.core.CorePlugin;
import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.gui.menu.CosmeticsGUI;
import com.yourserver.lobby.gui.menu.GameSelectorGUI;
import com.yourserver.lobby.gui.menu.StatsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all GUI instances and interactions.
 */
public class GUIManager implements Listener {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final CorePlugin corePlugin;
    private final Map<UUID, GUI> openGUIs;

    public GUIManager(LobbyPlugin plugin, LobbyConfig config, CorePlugin corePlugin) {
        this.plugin = plugin;
        this.config = config;
        this.corePlugin = corePlugin;
        this.openGUIs = new HashMap<>();
    }

    /**
     * Opens the game selector GUI for a player.
     *
     * @param player The player
     */
    public void openGameSelector(@NotNull Player player) {
        GameSelectorGUI gui = new GameSelectorGUI(plugin, config, corePlugin);
        openGUI(player, gui);
    }

    /**
     * Opens the cosmetics GUI for a player.
     *
     * @param player The player
     */
    public void openCosmetics(@NotNull Player player) {
        CosmeticsGUI gui = new CosmeticsGUI(plugin, config);
        openGUI(player, gui);
    }

    /**
     * Opens the stats GUI for a player.
     *
     * @param player The player
     */
    public void openStats(@NotNull Player player) {
        StatsGUI gui = new StatsGUI(plugin, config, corePlugin, player);
        openGUI(player, gui);
    }

    /**
     * Opens a GUI for a player.
     *
     * @param player The player
     * @param gui The GUI to open
     */
    private void openGUI(@NotNull Player player, @NotNull GUI gui) {
        openGUIs.put(player.getUniqueId(), gui);
        gui.onOpen(player);
        gui.open(player);
    }

    /**
     * Gets the GUI a player currently has open.
     *
     * @param player The player
     * @return The GUI, or null if none
     */
    public GUI getOpenGUI(@NotNull Player player) {
        return openGUIs.get(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GUI gui = openGUIs.get(player.getUniqueId());
        if (gui == null) {
            return;
        }

        // Check if clicked in the GUI inventory
        if (!event.getInventory().equals(gui.getInventory())) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < gui.getInventory().getSize()) {
            gui.handleClick(player, slot);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        GUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            gui.onClose(player);
        }
    }
}