package com.yourserver.lobby.spawn;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.config.SpawnLocation;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages spawn location and teleportation.
 */
public class SpawnManager {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private SpawnLocation spawnLocation;

    public SpawnManager(LobbyPlugin plugin, LobbyConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.spawnLocation = config.getSpawnLocation();
    }

    /**
     * Teleports a player to spawn.
     *
     * @param player The player to teleport
     * @return true if successful, false if spawn not set
     */
    public boolean teleportToSpawn(@NotNull Player player) {
        Location location = spawnLocation.toBukkitLocation();
        if (location == null) {
            return false;
        }

        player.teleport(location);
        return true;
    }

    /**
     * Sets the spawn location to the player's current location.
     *
     * @param player The player whose location to use
     */
    public void setSpawn(@NotNull Player player) {
        this.spawnLocation = SpawnLocation.fromBukkitLocation(player.getLocation());
        saveSpawnToConfig();
    }

    /**
     * Gets the current spawn location.
     *
     * @return The spawn location, or null if world doesn't exist
     */
    @Nullable
    public Location getSpawnLocation() {
        return spawnLocation.toBukkitLocation();
    }

    /**
     * Checks if spawn is set and the world exists.
     *
     * @return true if spawn is valid
     */
    public boolean isSpawnSet() {
        return spawnLocation.toBukkitLocation() != null;
    }

    /**
     * Saves the current spawn location to config.yml
     */
    private void saveSpawnToConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("spawn.world", spawnLocation.getWorldName());
        config.set("spawn.x", spawnLocation.getX());
        config.set("spawn.y", spawnLocation.getY());
        config.set("spawn.z", spawnLocation.getZ());
        config.set("spawn.yaw", spawnLocation.getYaw());
        config.set("spawn.pitch", spawnLocation.getPitch());
        plugin.saveConfig();
    }
}