package com.yourserver.lobby.items;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manages which players have lobby items enabled/disabled.
 * Useful for builders who need empty inventories.
 */
public class ItemToggleManager {

    // Players who have items DISABLED (builder mode)
    private final Set<UUID> disabledPlayers;

    // Callback to give items when re-enabling
    private Consumer<Player> itemGiver;

    public ItemToggleManager() {
        this.disabledPlayers = new HashSet<>();
    }

    /**
     * Sets the callback function to give items to players.
     * This is called when items are re-enabled.
     *
     * @param itemGiver The function that gives items to a player
     */
    public void setItemGiver(@NotNull Consumer<Player> itemGiver) {
        this.itemGiver = itemGiver;
    }

    /**
     * Checks if a player has lobby items enabled.
     *
     * @param player The player to check
     * @return true if items are enabled, false if disabled
     */
    public boolean hasItemsEnabled(@NotNull Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    /**
     * Toggles lobby items for a player.
     *
     * @param player The player
     */
    public void toggleItems(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        if (disabledPlayers.contains(uuid)) {
            // Enable items
            disabledPlayers.remove(uuid);

            // Give items back
            if (itemGiver != null) {
                itemGiver.accept(player);
            }
        } else {
            // Disable items
            disabledPlayers.add(uuid);
            // Clear inventory when disabling
            player.getInventory().clear();
        }
    }

    /**
     * Enables lobby items for a player.
     *
     * @param player The player
     */
    public void enableItems(@NotNull Player player) {
        disabledPlayers.remove(player.getUniqueId());
    }

    /**
     * Disables lobby items for a player.
     *
     * @param player The player
     */
    public void disableItems(@NotNull Player player) {
        disabledPlayers.add(player.getUniqueId());
        player.getInventory().clear();
    }

    /**
     * Cleans up when a player leaves.
     *
     * @param player The player
     */
    public void cleanup(@NotNull Player player) {
        disabledPlayers.remove(player.getUniqueId());
    }

    /**
     * Clears all data (for plugin shutdown).
     */
    public void shutdown() {
        disabledPlayers.clear();
    }
}