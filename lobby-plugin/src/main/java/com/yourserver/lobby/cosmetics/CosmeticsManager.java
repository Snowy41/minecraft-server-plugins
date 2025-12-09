package com.yourserver.lobby.cosmetics;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.cosmetics.trail.ParticleTrail;
import com.yourserver.lobby.cosmetics.trail.TrailTask;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages player cosmetics (trails, effects, etc.)
 */
public class CosmeticsManager {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final Map<UUID, ParticleTrail> activeTrails;
    private TrailTask trailTask;

    public CosmeticsManager(LobbyPlugin plugin, LobbyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.activeTrails = new HashMap<>();
    }

    /**
     * Sets a player's particle trail.
     *
     * @param player The player
     * @param trailId The trail ID
     */
    public void setTrail(@NotNull Player player, @NotNull String trailId) {
        LobbyConfig.TrailConfig trailConfig = config.getCosmeticsConfig().getTrails().get(trailId);
        if (trailConfig == null) {
            return;
        }

        // Remove existing trail
        removeTrail(player);

        // Create new trail
        ParticleTrail trail = new ParticleTrail(
                player,
                trailConfig.getParticle(),
                config.getCosmeticsConfig().getSpawnRate()
        );

        activeTrails.put(player.getUniqueId(), trail);
    }

    /**
     * Removes a player's particle trail.
     *
     * @param player The player
     */
    public void removeTrail(@NotNull Player player) {
        activeTrails.remove(player.getUniqueId());
    }

    /**
     * Gets a player's active trail.
     *
     * @param player The player
     * @return The trail, or null if none
     */
    @Nullable
    public ParticleTrail getTrail(@NotNull Player player) {
        return activeTrails.get(player.getUniqueId());
    }

    /**
     * Updates all active trails.
     * Called by TrailTask.
     */
    public void updateTrails() {
        activeTrails.values().forEach(ParticleTrail::spawn);
    }

    /**
     * Starts the trail update task.
     */
    public void startTrailTask() {
        if (trailTask != null) {
            trailTask.cancel();
        }

        trailTask = new TrailTask(this);
        trailTask.runTaskTimer(plugin, 0L, 1L); // Update every tick
    }

    /**
     * Shuts down the cosmetics manager.
     */
    public void shutdown() {
        if (trailTask != null) {
            trailTask.cancel();
            trailTask = null;
        }

        activeTrails.clear();
    }
}