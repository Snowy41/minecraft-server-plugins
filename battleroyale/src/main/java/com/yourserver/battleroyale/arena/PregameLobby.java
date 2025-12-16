package com.yourserver.battleroyale.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-game lobby above the map.
 * Players spawn here and can see the map below before the game starts.
 *
 * Features:
 * - Elevated platform (Y=320) above build limit
 * - Transparent floor (glass/barriers) to see map below
 * - Circular spawn platforms for players
 * - Countdown display
 * - Jump pads to enter game when it starts
 */
public class PregameLobby {

    private final Location center;
    private final int height; // Y level (default 320)
    private final int platformCount; // Number of spawn platforms
    private final int platformRadius; // Radius of platform circle
    private final boolean transparentFloor;

    private final List<Location> spawnLocations;
    private boolean built = false;

    public PregameLobby(@NotNull Location mapCenter, int height, int platformCount, boolean transparentFloor) {
        this.center = mapCenter.clone();
        this.center.setY(height);
        this.height = height;
        this.platformCount = platformCount;
        this.platformRadius = 50; // 50 blocks from center
        this.transparentFloor = transparentFloor;
        this.spawnLocations = new ArrayList<>();
    }

    /**
     * Creates default pre-game lobby configuration.
     */
    @NotNull
    public static PregameLobby createDefault(@NotNull Location mapCenter) {
        return new PregameLobby(mapCenter, 320, 8, true);
    }

    /**
     * Builds the pre-game lobby structure in the world.
     * Creates platforms, floors, and spawn points.
     */
    public void build() {
        if (built) {
            return;
        }

        World world = center.getWorld();
        if (world == null) {
            throw new IllegalStateException("World cannot be null");
        }

        // 1. Build main platform (center)
        buildMainPlatform(world);

        // 2. Build spawn platforms in circle
        buildSpawnPlatforms(world);

        // 3. Build transparent floor
        if (transparentFloor) {
            buildTransparentFloor(world);
        }

        built = true;
    }

    /**
     * Builds the main central platform.
     */
    private void buildMainPlatform(@NotNull World world) {
        int radius = 15; // 15 block radius central platform

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) {
                    // Create solid floor
                    Block block = world.getBlockAt(
                            (int) center.getX() + x,
                            (int) center.getY() - 1,
                            (int) center.getZ() + z
                    );
                    block.setType(Material.QUARTZ_BLOCK);

                    // Create decorative border
                    if (x * x + z * z >= (radius - 1) * (radius - 1)) {
                        Block borderBlock = world.getBlockAt(
                                (int) center.getX() + x,
                                (int) center.getY(),
                                (int) center.getZ() + z
                        );
                        borderBlock.setType(Material.QUARTZ_STAIRS);
                    }
                }
            }
        }
    }

    /**
     * Builds spawn platforms in a circle around the center.
     */
    private void buildSpawnPlatforms(@NotNull World world) {
        spawnLocations.clear();

        for (int i = 0; i < platformCount; i++) {
            double angle = 2 * Math.PI * i / platformCount;
            int x = (int) (center.getX() + platformRadius * Math.cos(angle));
            int z = (int) (center.getZ() + platformRadius * Math.sin(angle));

            // Build small platform (5x5)
            int platformSize = 2;
            for (int px = -platformSize; px <= platformSize; px++) {
                for (int pz = -platformSize; pz <= platformSize; pz++) {
                    Block block = world.getBlockAt(x + px, (int) center.getY() - 1, z + pz);
                    block.setType(Material.STONE_BRICKS);
                }
            }

            // Add spawn location (center of platform)
            Location spawnLoc = new Location(world, x + 0.5, center.getY(), z + 0.5);
            spawnLoc.setYaw((float) (Math.toDegrees(angle) + 180)); // Face center
            spawnLocations.add(spawnLoc);
        }
    }

    /**
     * Builds transparent floor connecting platforms to center.
     */
    private void buildTransparentFloor(@NotNull World world) {
        int radius = platformRadius + 10; // Extends beyond platforms

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= radius && distance >= 15) { // Outside center platform
                    Block block = world.getBlockAt(
                            (int) center.getX() + x,
                            (int) center.getY() - 1,
                            (int) center.getZ() + z
                    );

                    // Use glass or barrier based on distance
                    if (distance < 30) {
                        block.setType(Material.GLASS);
                    } else {
                        block.setType(Material.BARRIER); // Invisible outer ring
                    }
                }
            }
        }
    }

    /**
     * Teleports a player to the pre-game lobby.
     *
     * @param player The player to teleport
     * @param index The spawn index (0 to platformCount-1)
     */
    public void teleportPlayer(@NotNull Player player, int index) {
        if (!built) {
            build();
        }

        if (index < 0 || index >= spawnLocations.size()) {
            // Default to center if index invalid
            player.teleport(center);
        } else {
            player.teleport(spawnLocations.get(index));
        }

        // Set player flight for lobby
        player.setAllowFlight(true);
        player.setFlying(false);
    }

    /**
     * Teleports a player to a random spawn location.
     */
    public void teleportPlayerRandom(@NotNull Player player) {
        int index = (int) (Math.random() * spawnLocations.size());
        teleportPlayer(player, index);
    }

    /**
     * Checks if a location is within the lobby bounds.
     */
    public boolean isInLobby(@NotNull Location location) {
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }

        double distance = location.distance(center);
        return distance <= platformRadius + 15 &&
                Math.abs(location.getY() - center.getY()) <= 10;
    }

    /**
     * Removes the lobby structure from the world.
     */
    public void remove() {
        if (!built) {
            return;
        }

        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int radius = platformRadius + 15;

        // Clear all blocks
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 5; y++) {
                    Block block = world.getBlockAt(
                            (int) center.getX() + x,
                            (int) center.getY() + y,
                            (int) center.getZ() + z
                    );
                    block.setType(Material.AIR);
                }
            }
        }

        built = false;
    }

    // ===== GETTERS =====

    @NotNull
    public Location getCenter() {
        return center.clone();
    }

    public int getHeight() {
        return height;
    }

    public int getPlatformCount() {
        return platformCount;
    }

    @NotNull
    public List<Location> getSpawnLocations() {
        return new ArrayList<>(spawnLocations);
    }

    public boolean isBuilt() {
        return built;
    }
}