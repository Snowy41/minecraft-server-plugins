package com.yourserver.partition.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a server partition with isolated worlds and plugins.
 */
public class Partition {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> worlds;
    private final List<String> plugins;
    private final String spawnWorld;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final float spawnYaw;
    private final float spawnPitch;
    private final boolean persistent;
    private final Instant createdAt;
    private Instant lastRestart;

    public Partition(
            @NotNull String id,
            @NotNull String name,
            @NotNull String description,
            @NotNull List<String> worlds,
            @NotNull List<String> plugins,
            @NotNull String spawnWorld,
            double spawnX,
            double spawnY,
            double spawnZ,
            float spawnYaw,
            float spawnPitch,
            boolean persistent
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.worlds = new ArrayList<>(worlds);
        this.plugins = new ArrayList<>(plugins);
        this.spawnWorld = spawnWorld;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.spawnYaw = spawnYaw;
        this.spawnPitch = spawnPitch;
        this.persistent = persistent;
        this.createdAt = Instant.now();
        this.lastRestart = Instant.now();
    }

    /**
     * Gets the spawn location for this partition.
     *
     * @return The spawn location, or null if world doesn't exist
     */
    @Nullable
    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(spawnWorld);
        if (world == null) {
            return null;
        }

        return new Location(world, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch);
    }

    /**
     * Checks if a world belongs to this partition.
     */
    public boolean containsWorld(@NotNull String worldName) {
        return worlds.contains(worldName);
    }

    /**
     * Checks if a plugin is assigned to this partition.
     */
    public boolean hasPlugin(@NotNull String pluginName) {
        return plugins.contains(pluginName);
    }

    /**
     * Marks this partition as restarted.
     */
    public void markRestarted() {
        this.lastRestart = Instant.now();
    }

    // Getters
    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getDescription() {
        return description;
    }

    @NotNull
    public List<String> getWorlds() {
        return new ArrayList<>(worlds);
    }

    @NotNull
    public List<String> getPlugins() {
        return new ArrayList<>(plugins);
    }

    @NotNull
    public String getSpawnWorld() {
        return spawnWorld;
    }

    public double getSpawnX() {
        return spawnX;
    }

    public double getSpawnY() {
        return spawnY;
    }

    public double getSpawnZ() {
        return spawnZ;
    }

    public float getSpawnYaw() {
        return spawnYaw;
    }

    public float getSpawnPitch() {
        return spawnPitch;
    }

    public boolean isPersistent() {
        return persistent;
    }

    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @NotNull
    public Instant getLastRestart() {
        return lastRestart;
    }

    @Override
    public String toString() {
        return "Partition{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", worlds=" + worlds.size() +
                ", plugins=" + plugins.size() +
                '}';
    }
}