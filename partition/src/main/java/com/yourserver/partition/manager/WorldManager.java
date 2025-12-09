package com.yourserver.partition.manager;

import com.yourserver.partition.PartitionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;

/**
 * Manages world creation and loading for partitions.
 * Automatically creates missing worlds when a partition is loaded.
 */
public class WorldManager {

    private final PartitionPlugin plugin;

    public WorldManager(PartitionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ensures a world exists and is loaded.
     * If the world doesn't exist, it will be created.
     *
     * @param worldName The world name
     * @return The loaded world, or null if creation failed
     */
    public World ensureWorldLoaded(@NotNull String worldName) {
        // Check if world is already loaded
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            plugin.getLogger().info("World already loaded: " + worldName);
            return world;
        }

        // Check if world folder exists
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            plugin.getLogger().info("Loading existing world: " + worldName);
            return loadWorld(worldName);
        }

        // World doesn't exist - create it
        plugin.getLogger().info("Creating new world: " + worldName);
        return createWorld(worldName);
    }

    /**
     * Loads an existing world.
     *
     * @param worldName The world name
     * @return The loaded world, or null if loading failed
     */
    private World loadWorld(@NotNull String worldName) {
        try {
            WorldCreator creator = new WorldCreator(worldName);

            // Detect world environment based on name
            if (worldName.endsWith("_nether")) {
                creator.environment(World.Environment.NETHER);
            } else if (worldName.endsWith("_the_end")) {
                creator.environment(World.Environment.THE_END);
            } else {
                creator.environment(World.Environment.NORMAL);
            }

            World world = creator.createWorld();

            if (world != null) {
                plugin.getLogger().info("✓ Successfully loaded world: " + worldName);
            } else {
                plugin.getLogger().warning("✗ Failed to load world: " + worldName);
            }

            return world;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading world: " + worldName, e);
            return null;
        }
    }

    /**
     * Creates a new world.
     *
     * @param worldName The world name
     * @return The created world, or null if creation failed
     */
    private World createWorld(@NotNull String worldName) {
        try {
            WorldCreator creator = new WorldCreator(worldName);

            // Detect world environment based on name
            if (worldName.endsWith("_nether")) {
                creator.environment(World.Environment.NETHER);
                plugin.getLogger().info("Creating Nether world: " + worldName);
            } else if (worldName.endsWith("_the_end")) {
                creator.environment(World.Environment.THE_END);
                plugin.getLogger().info("Creating End world: " + worldName);
            } else {
                creator.environment(World.Environment.NORMAL);
                creator.type(WorldType.NORMAL);
                plugin.getLogger().info("Creating Normal world: " + worldName);
            }

            // Generate structures
            creator.generateStructures(true);

            // Create the world
            World world = creator.createWorld();

            if (world != null) {
                plugin.getLogger().info("✓ Successfully created world: " + worldName);

                // Set world properties
                world.setAutoSave(true);
                world.setKeepSpawnInMemory(false); // Save memory for partition worlds

                // Set spawn location to 0, highest block, 0
                if (world.getEnvironment() == World.Environment.NORMAL) {
                    int highestY = world.getHighestBlockYAt(0, 0);
                    world.setSpawnLocation(0, highestY, 0);
                }

            } else {
                plugin.getLogger().warning("✗ Failed to create world: " + worldName);
            }

            return world;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating world: " + worldName, e);
            return null;
        }
    }

    /**
     * Unloads a world from memory.
     *
     * @param worldName The world name
     * @return true if successfully unloaded
     */
    public boolean unloadWorld(@NotNull String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Cannot unload world (not loaded): " + worldName);
            return false;
        }

        // Move all players out of this world first
        if (!world.getPlayers().isEmpty()) {
            plugin.getLogger().warning("Cannot unload world with players in it: " + worldName);
            return false;
        }

        try {
            boolean success = Bukkit.unloadWorld(world, true); // Save chunks before unloading

            if (success) {
                plugin.getLogger().info("✓ Unloaded world: " + worldName);
            } else {
                plugin.getLogger().warning("✗ Failed to unload world: " + worldName);
            }

            return success;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error unloading world: " + worldName, e);
            return false;
        }
    }

    /**
     * Checks if a world is loaded.
     *
     * @param worldName The world name
     * @return true if loaded
     */
    public boolean isWorldLoaded(@NotNull String worldName) {
        return Bukkit.getWorld(worldName) != null;
    }

    /**
     * Gets a loaded world.
     *
     * @param worldName The world name
     * @return The world, or null if not loaded
     */
    public World getWorld(@NotNull String worldName) {
        return Bukkit.getWorld(worldName);
    }
}