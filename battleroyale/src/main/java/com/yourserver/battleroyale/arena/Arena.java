package com.yourserver.battleroyale.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a battle royale arena/map.
 * Contains spawn points, loot locations, and arena boundaries.
 */
public class Arena {

    private final String id;
    private final String name;
    private final World world;
    private final Location center;
    private final int size; // radius in blocks

    private final List<Location> spawnPoints;
    private final List<Location> lootChestLocations;
    private final Location pregameLobbyCenter;
    private final Location deathmatchCenter;

    private final ArenaConfig config;

    public Arena(@NotNull String id, @NotNull String name, @NotNull World world,
                 @NotNull Location center, int size, @NotNull ArenaConfig config) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.world = Objects.requireNonNull(world);
        this.center = Objects.requireNonNull(center);
        this.size = size;
        this.config = Objects.requireNonNull(config);

        this.spawnPoints = new ArrayList<>();
        this.lootChestLocations = new ArrayList<>();
        this.pregameLobbyCenter = center.clone().add(0, config.getPregameLobbyHeight(), 0);
        this.deathmatchCenter = center.clone();
    }

    /**
     * Adds a spawn point to the arena.
     */
    public void addSpawnPoint(@NotNull Location location) {
        if (location.getWorld().equals(world)) {
            spawnPoints.add(location.clone());
        }
    }

    /**
     * Adds a loot chest location to the arena.
     */
    public void addLootChestLocation(@NotNull Location location) {
        if (location.getWorld().equals(world)) {
            lootChestLocations.add(location.clone());
        }
    }

    /**
     * Gets a random spawn point.
     *
     * @return A random spawn location, or center if no spawns defined
     */
    @NotNull
    public Location getRandomSpawnPoint() {
        if (spawnPoints.isEmpty()) {
            return center.clone();
        }

        Random random = new Random();
        return spawnPoints.get(random.nextInt(spawnPoints.size())).clone();
    }

    /**
     * Gets multiple unique spawn points (one per player).
     *
     * @param count Number of spawn points needed
     * @return List of spawn points (may be less than count if not enough)
     */
    @NotNull
    public List<Location> getSpawnPoints(int count) {
        if (spawnPoints.isEmpty()) {
            return generateCircularSpawns(count);
        }

        List<Location> available = new ArrayList<>(spawnPoints);
        Collections.shuffle(available);

        return available.subList(0, Math.min(count, available.size()));
    }

    /**
     * Generates spawn points in a circle around the center.
     */
    @NotNull
    private List<Location> generateCircularSpawns(int count) {
        List<Location> spawns = new ArrayList<>();
        double radius = size * 0.7; // 70% of arena size

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location spawn = new Location(world, x, center.getY() + 100, z);
            spawns.add(spawn);
        }

        return spawns;
    }

    /**
     * Gets all loot chest locations (shuffled).
     */
    @NotNull
    public List<Location> getLootChestLocations() {
        List<Location> locations = new ArrayList<>(lootChestLocations);
        Collections.shuffle(locations);
        return locations;
    }

    /**
     * Gets a subset of loot chest locations based on spawn rate.
     *
     * @param spawnRate Percentage of chests to spawn (0.0 to 1.0)
     * @return List of chest locations to spawn
     */
    @NotNull
    public List<Location> getLootChestLocations(double spawnRate) {
        List<Location> all = getLootChestLocations();
        int count = (int) (all.size() * Math.min(1.0, Math.max(0.0, spawnRate)));
        return all.subList(0, Math.min(count, all.size()));
    }

    /**
     * Checks if a location is within the arena boundaries.
     */
    public boolean isInArena(@NotNull Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }

        double distance = center.distance(location);
        return distance <= size;
    }

    /**
     * Gets the distance from a location to the arena edge.
     */
    public double getDistanceToEdge(@NotNull Location location) {
        if (!location.getWorld().equals(world)) {
            return -1000;
        }

        double distance = center.distance(location);
        return size - distance;
    }

    // ===== GETTERS =====

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    @NotNull
    public Location getCenter() {
        return center.clone();
    }

    public int getSize() {
        return size;
    }

    @NotNull
    public List<Location> getSpawnPoints() {
        return Collections.unmodifiableList(spawnPoints);
    }

    @NotNull
    public Location getPregameLobbyCenter() {
        return pregameLobbyCenter.clone();
    }

    @NotNull
    public Location getDeathmatchCenter() {
        return deathmatchCenter.clone();
    }

    @NotNull
    public ArenaConfig getConfig() {
        return config;
    }

    @Override
    public String toString() {
        return "Arena{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                ", spawns=" + spawnPoints.size() +
                ", lootChests=" + lootChestLocations.size() +
                '}';
    }

    // ===== ARENA CONFIG =====

    public static class ArenaConfig {
        private final int pregameLobbyHeight;
        private final int deathmatchArenaSize;
        private final double lootChestSpawnRate;

        public ArenaConfig(int pregameLobbyHeight, int deathmatchArenaSize,
                           double lootChestSpawnRate) {
            this.pregameLobbyHeight = pregameLobbyHeight;
            this.deathmatchArenaSize = deathmatchArenaSize;
            this.lootChestSpawnRate = lootChestSpawnRate;
        }

        public static ArenaConfig createDefault() {
            return new ArenaConfig(320, 50, 0.7);
        }

        public int getPregameLobbyHeight() {
            return pregameLobbyHeight;
        }

        public int getDeathmatchArenaSize() {
            return deathmatchArenaSize;
        }

        public double getLootChestSpawnRate() {
            return lootChestSpawnRate;
        }
    }
}