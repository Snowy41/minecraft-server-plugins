package com.yourserver.lobby.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base GUI class for creating custom inventory menus.
 */
public abstract class GUI {

    protected final Component title;
    protected final int size;
    protected final Inventory inventory;
    protected final Map<Integer, Consumer<Player>> clickActions;

    public GUI(@NotNull Component title, int rows) {
        this.title = title;
        this.size = rows * 9;
        this.inventory = Bukkit.createInventory(null, size, title);
        this.clickActions = new HashMap<>();
    }

    /**
     * Opens the GUI for a player.
     *
     * @param player The player
     */
    public void open(@NotNull Player player) {
        player.openInventory(inventory);
    }

    /**
     * Sets an item in the inventory with a click action.
     *
     * @param slot The slot
     * @param item The item
     * @param action The action to perform on click
     */
    protected void setItem(int slot, @NotNull ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        if (action != null) {
            clickActions.put(slot, action);
        }
    }

    /**
     * Sets an item in the inventory without a click action.
     *
     * @param slot The slot
     * @param item The item
     */
    protected void setItem(int slot, @NotNull ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Fills empty slots with a filler item.
     *
     * @param filler The filler item
     */
    protected void fillEmpty(@NotNull ItemStack filler) {
        for (int i = 0; i < size; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Handles a click in the GUI.
     *
     * @param player The player who clicked
     * @param slot The slot that was clicked
     */
    public void handleClick(@NotNull Player player, int slot) {
        Consumer<Player> action = clickActions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    /**
     * Called when the GUI is opened.
     * Override to add custom logic.
     *
     * @param player The player
     */
    public void onOpen(@NotNull Player player) {
        // Override in subclasses
    }

    /**
     * Called when the GUI is closed.
     * Override to add custom logic.
     *
     * @param player The player
     */
    public void onClose(@NotNull Player player) {
        // Override in subclasses
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Component getTitle() {
        return title;
    }
}