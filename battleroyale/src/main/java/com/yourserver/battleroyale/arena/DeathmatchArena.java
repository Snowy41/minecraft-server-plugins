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
 * Deathmatch arena for final combat phase.
 * Small confined arena where remaining players fight to the death.
 *
 * Features:
 * - Small size (50x50 default) - no hiding
 * - Flat terrain with minimal cover
 * - Barriers to prevent escape
 * - Always daytime
 * - No hunger damage
 * - Forced PvP combat
 *
 * Triggered by:
 * - Time limit (1 hour default)
 * - Zone reaching very small size (20 blocks)
 */
public class DeathmatchArena {

    private final Location center;
    private final int size; // Arena size (radius)
    private final World world;
    private final List<Location> spawnPoints;
    private boolean built = false;

    public DeathmatchArena(@NotNull Location center, int size) {
        this.center = center.clone();
        this.size = size;
        this.world = center.getWorld();
        this.spawnPoints = new ArrayList<>();
    }

    /**
     * Creates default deathmatch arena.
     */
    @NotNull
    public static DeathmatchArena createDefault(@NotNull Location mapCenter) {
        // Create arena at map center, elevated
        Location arenaCenter = mapCenter.clone();
        arenaCenter.setY(100);
        return new DeathmatchArena(arenaCenter, 25); // 50x50 arena
    }

    /**
     * Builds the deathmatch arena structure.
     */
    public void build() {
        if (built || world == null) {
            return;
        }

        // 1. Clear area
        clearArea();

        // 2. Build floor
        buildFloor();

        // 3. Build barriers
        buildBarriers();

        // 4. Add minimal cover
        addCover();

        // 5. Generate spawn points
        generateSpawnPoints();

        built = true;
    }

    /**
     * Clears the arena area of all blocks.
     */
    private void clearArea() {
        for (int x = -size - 5; x <= size + 5; x++) {
            for (int z = -size - 5; z <= size + 5; z++) {
                for (int y = -5; y <= 20; y++) {
                    Block block = world.getBlockAt(
                            (int) center.getX() + x,
                            (int) center.getY() + y,
                            (int) center.getZ() + z
                    );
                    block.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Builds flat floor for the arena.
     */
    private void buildFloor() {
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                if (x * x + z * z <= size * size) {
                    // Create floor
                    Block floor = world.getBlockAt(
                            (int) center.getX() + x,
                            (int) center.getY() - 1,
                            (int) center.getZ() + z
                    );
                    floor.setType(Material.STONE);

                    // Add decorative pattern
                    if ((x + z) % 4 == 0) {
                        floor.setType(Material.POLISHED_ANDESITE);
                    }
                }
            }
        }
    }

    /**
     * Builds invisible barriers around the arena to prevent escape.
     */
    private void buildBarriers() {
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 32) {
            int x = (int) (center.getX() + size * Math.cos(angle));
            int z = (int) (center.getZ() + size * Math.sin(angle));

            // Build barrier wall (height 10)
            for (int y = 0; y < 10; y++) {
                Block block = world.getBlockAt(x, (int) center.getY() + y, z);
                block.setType(Material.BARRIER); // Invisible barrier
            }
        }
    }

    /**
     * Adds minimal cover (small obstacles).
     */
    private void addCover() {
        // Add 4 small stone pillars for minimal cover
        int[] offsets = {-10, 10};

        for (int xOffset : offsets) {
            for (int zOffset : offsets) {
                int x = (int) center.getX() + xOffset;
                int z = (int) center.getZ() + zOffset;

                // Build 2-block high pillar (2x2)
                for (int px = 0; px <= 1; px++) {
                    for (int pz = 0; pz <= 1; pz++) {
                        for (int y = 0; y <= 1; y++) {
                            Block block = world.getBlockAt(
                                    x + px,
                                    (int) center.getY() + y,
                                    z + pz
                            );
                            block.setType(Material.STONE_BRICKS);
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates spawn points in a circle around the center.
     */
    private void generateSpawnPoints() {
        spawnPoints.clear();

        int spawnRadius = size / 2; // Spawn halfway from center
        int spawnCount = 16; // Support up to 16 players in deathmatch

        for (int i = 0; i < spawnCount; i++) {
            double angle = 2 * Math.PI * i / spawnCount;
            double x = center.getX() + spawnRadius * Math.cos(angle);
            double z = center.getZ() + spawnRadius * Math.sin(angle);

            Location spawn = new Location(world, x, center.getY(), z);
            spawn.setYaw((float) (Math.toDegrees(angle) + 180)); // Face center
            spawnPoints.add(spawn);
        }
    }

    /**
     * Teleports a player to the deathmatch arena.
     *
     * @param player The player to teleport
     * @param index Spawn point index
     */
    public void teleportPlayer(@NotNull Player player, int index) {
        if (!built) {
            build();
        }

        if (index < 0 || index >= spawnPoints.size()) {
            player.teleport(center);
        } else {
            player.teleport(spawnPoints.get(index));
        }

        // Apply deathmatch effects
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
    }

    /**
     * Checks if a location is within the arena.
     */
    public boolean isInArena(@NotNull Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }

        double distance = Math.sqrt(
                Math.pow(location.getX() - center.getX(), 2) +
                        Math.pow(location.getZ() - center.getZ(), 2)
        );

        return distance <= size;
    }

    /**
     * Teleports player back if they try to escape.
     */
    public void enforceBoundaries(@NotNull Player player) {
        if (!isInArena(player.getLocation())) {
            // Teleport to nearest point inside arena
            Location loc = player.getLocation();
            double angle = Math.atan2(
                    loc.getZ() - center.getZ(),
                    loc.getX() - center.getX()
            );

            double x = center.getX() + (size - 2) * Math.cos(angle);
            double z = center.getZ() + (size - 2) * Math.sin(angle);

            Location safeLocation = new Location(world, x, center.getY(), z);
            player.teleport(safeLocation);

            // Apply small damage as penalty
            player.damage(2.0);
        }
    }

    /**
     * Removes the arena structure.
     */
    public void remove() {
        if (!built) {
            return;
        }

        // Clear entire area
        for (int x = -size - 5; x <= size + 5; x++) {
            for (int z = -size - 5; z <= size + 5; z++) {
                for (int y = -5; y <= 20; y++) {
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

    public int getSize() {
        return size;
    }

    @NotNull
    public List<Location> getSpawnPoints() {
        return new ArrayList<>(spawnPoints);
    }

    public boolean isBuilt() {
        return built;
    }
}