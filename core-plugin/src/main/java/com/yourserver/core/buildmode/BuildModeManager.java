package com.yourserver.core.buildmode;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages build mode state for players.
 * Tracks previous state so players can toggle on/off.
 */
public class BuildModeManager {

    private final Map<UUID, PlayerBuildState> savedStates;

    public BuildModeManager() {
        this.savedStates = new HashMap<>();
    }

    /**
     * Checks if a player is in build mode.
     */
    public boolean isInBuildMode(@NotNull Player player) {
        return savedStates.containsKey(player.getUniqueId());
    }

    /**
     * Saves a player's current state before entering build mode.
     */
    public void saveState(@NotNull Player player) {
        PlayerBuildState state = new PlayerBuildState(
                player.getGameMode(),
                player.getInventory().getContents().clone(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation()
        );

        savedStates.put(player.getUniqueId(), state);
    }

    /**
     * Restores a player's previous state.
     */
    public boolean restoreState(@NotNull Player player) {
        PlayerBuildState state = savedStates.remove(player.getUniqueId());

        if (state == null) {
            return false;
        }

        player.setGameMode(state.gameMode);
        player.getInventory().setContents(state.inventory);
        player.setHealth(state.health);
        player.setFoodLevel(state.foodLevel);
        player.setSaturation(state.saturation);

        return true;
    }

    /**
     * Clears saved state for a player (on logout).
     */
    public void clearState(@NotNull Player player) {
        savedStates.remove(player.getUniqueId());
    }

    /**
     * Clears all saved states.
     */
    public void shutdown() {
        savedStates.clear();
    }

    /**
     * Stores a player's state before build mode.
     */
    private static class PlayerBuildState {
        final GameMode gameMode;
        final ItemStack[] inventory;
        final double health;
        final int foodLevel;
        final float saturation;

        PlayerBuildState(GameMode gameMode, ItemStack[] inventory,
                         double health, int foodLevel, float saturation) {
            this.gameMode = gameMode;
            this.inventory = inventory;
            this.health = health;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
        }
    }
}