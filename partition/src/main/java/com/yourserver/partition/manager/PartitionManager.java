package com.yourserver.partition.manager;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.config.PartitionConfig;
import com.yourserver.partition.model.Partition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all server partitions and their lifecycle.
 */
public class PartitionManager {

    private final PartitionPlugin plugin;
    private final PartitionConfig config;
    private final Map<String, Partition> partitions;
    private final Map<UUID, String> playerPartitions; // Player UUID -> Partition ID
    private final Map<String, String> worldPartitions; // World name -> Partition ID

    public PartitionManager(PartitionPlugin plugin, PartitionConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.partitions = new ConcurrentHashMap<>();
        this.playerPartitions = new ConcurrentHashMap<>();
        this.worldPartitions = new ConcurrentHashMap<>();
    }

    /**
     * Loads all partitions from configuration.
     */
    public void loadAllPartitions() {
        plugin.getLogger().info("Loading partitions...");

        for (var entry : config.getPartitions().entrySet()) {
            String id = entry.getKey();
            PartitionConfig.PartitionSettings settings = entry.getValue();

            if (!settings.isEnabled()) {
                plugin.getLogger().info("Skipping disabled partition: " + id);
                continue;
            }

            if (!settings.isAutoLoad()) {
                plugin.getLogger().info("Skipping non-auto-load partition: " + id);
                continue;
            }

            loadPartition(id);
        }

        plugin.getLogger().info("Loaded " + partitions.size() + " partitions");
    }

    /**
     * Loads a specific partition.
     */
    public boolean loadPartition(@NotNull String partitionId) {
        PartitionConfig.PartitionSettings settings = config.getPartition(partitionId);
        if (settings == null) {
            plugin.getLogger().warning("Partition not found in config: " + partitionId);
            return false;
        }

        if (!settings.isEnabled()) {
            plugin.getLogger().warning("Cannot load disabled partition: " + partitionId);
            return false;
        }

        // Check if already loaded
        if (partitions.containsKey(partitionId)) {
            plugin.getLogger().warning("Partition already loaded: " + partitionId);
            return false;
        }

        plugin.getLogger().info("Loading partition: " + partitionId);

        // Create partition instance
        Partition partition = new Partition(
                settings.getId(),
                settings.getName(),
                settings.getDescription(),
                settings.getWorlds(),
                settings.getPlugins(),
                settings.getSpawnWorld(),
                settings.getSpawnX(),
                settings.getSpawnY(),
                settings.getSpawnZ(),
                settings.getSpawnYaw(),
                settings.getSpawnPitch(),
                settings.isPersistent()
        );

        // Register world mappings
        for (String worldName : settings.getWorlds()) {
            worldPartitions.put(worldName, partitionId);
            plugin.getLogger().info("  - Mapped world '" + worldName + "' to partition '" + partitionId + "'");
        }

        // Load plugins for this partition
        plugin.getPluginIsolationManager().loadPluginsForPartition(partition);

        partitions.put(partitionId, partition);
        plugin.getLogger().info("Partition loaded: " + partitionId + " (" + partition.getName() + ")");

        return true;
    }

    /**
     * Unloads a partition (saves worlds, unloads plugins).
     */
    public boolean unloadPartition(@NotNull String partitionId) {
        Partition partition = partitions.get(partitionId);
        if (partition == null) {
            plugin.getLogger().warning("Partition not loaded: " + partitionId);
            return false;
        }

        plugin.getLogger().info("Unloading partition: " + partitionId);

        // Move all players out of this partition
        List<Player> playersInPartition = getPlayersInPartition(partitionId);
        if (!playersInPartition.isEmpty()) {
            plugin.getLogger().info("Moving " + playersInPartition.size() + " players out of partition");

            // Find a default partition to move them to
            Partition defaultPartition = getDefaultPartition();
            if (defaultPartition != null) {
                for (Player player : playersInPartition) {
                    movePlayerToPartition(player, defaultPartition.getId());
                }
            } else {
                // No default partition, kick players
                for (Player player : playersInPartition) {
                    player.kick(plugin.getMiniMessage().deserialize(
                            "<red>The partition you were in has been unloaded!"
                    ));
                }
            }
        }

        // Unload plugins
        plugin.getPluginIsolationManager().unloadPluginsForPartition(partition);

        // Remove world mappings
        for (String worldName : partition.getWorlds()) {
            worldPartitions.remove(worldName);
        }

        // Remove partition
        partitions.remove(partitionId);
        plugin.getLogger().info("Partition unloaded: " + partitionId);

        return true;
    }

    /**
     * Restarts a partition (unload then load).
     */
    public boolean restartPartition(@NotNull String partitionId) {
        plugin.getLogger().info("Restarting partition: " + partitionId);

        if (!partitions.containsKey(partitionId)) {
            plugin.getLogger().warning("Cannot restart partition that isn't loaded: " + partitionId);
            return false;
        }

        // Unload
        if (!unloadPartition(partitionId)) {
            return false;
        }

        // Wait a bit for cleanup
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Load
        return loadPartition(partitionId);
    }

    /**
     * Gets the partition ID for a world.
     */
    @Nullable
    public String getPartitionForWorld(@NotNull String worldName) {
        return worldPartitions.get(worldName);
    }

    /**
     * Gets the partition ID for a player.
     */
    @Nullable
    public String getPartitionForPlayer(@NotNull Player player) {
        // First check cached value
        String cached = playerPartitions.get(player.getUniqueId());
        if (cached != null) {
            return cached;
        }

        // Determine from current world
        String worldPartition = getPartitionForWorld(player.getWorld().getName());
        if (worldPartition != null) {
            playerPartitions.put(player.getUniqueId(), worldPartition);
            return worldPartition;
        }

        return null;
    }

    /**
     * Gets all players in a partition.
     */
    @NotNull
    public List<Player> getPlayersInPartition(@NotNull String partitionId) {
        List<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerPartition = getPartitionForPlayer(player);
            if (partitionId.equals(playerPartition)) {
                players.add(player);
            }
        }

        return players;
    }

    /**
     * Moves a player to a specific partition.
     */
    public boolean movePlayerToPartition(@NotNull Player player, @NotNull String partitionId) {
        Partition partition = partitions.get(partitionId);
        if (partition == null) {
            plugin.getLogger().warning("Cannot move player to unloaded partition: " + partitionId);
            return false;
        }

        Location spawnLocation = partition.getSpawnLocation();
        if (spawnLocation == null) {
            plugin.getLogger().warning("Partition spawn location not available: " + partitionId);
            return false;
        }

        // Update cached partition
        playerPartitions.put(player.getUniqueId(), partitionId);

        // Teleport player
        player.teleport(spawnLocation);

        plugin.getLogger().info("Moved player " + player.getName() + " to partition: " + partitionId);
        return true;
    }

    /**
     * Updates a player's partition when they change worlds.
     */
    public void updatePlayerPartition(@NotNull Player player, @NotNull World newWorld) {
        String worldPartition = getPartitionForWorld(newWorld.getName());

        if (worldPartition != null) {
            String oldPartition = playerPartitions.get(player.getUniqueId());

            if (!worldPartition.equals(oldPartition)) {
                playerPartitions.put(player.getUniqueId(), worldPartition);

                plugin.getLogger().info("Player " + player.getName() +
                        " moved from partition '" + oldPartition +
                        "' to '" + worldPartition + "'");
            }
        } else {
            // World not in any partition
            playerPartitions.remove(player.getUniqueId());
        }
    }

    /**
     * Removes a player's partition cache on disconnect.
     */
    public void clearPlayerPartition(@NotNull Player player) {
        playerPartitions.remove(player.getUniqueId());
    }

    /**
     * Gets a partition by ID.
     */
    @Nullable
    public Partition getPartition(@NotNull String partitionId) {
        return partitions.get(partitionId);
    }

    /**
     * Gets all loaded partitions.
     */
    @NotNull
    public Collection<Partition> getAllPartitions() {
        return new ArrayList<>(partitions.values());
    }

    /**
     * Gets the default partition (first enabled partition).
     */
    @Nullable
    public Partition getDefaultPartition() {
        return partitions.values().stream().findFirst().orElse(null);
    }

    /**
     * Checks if two players are in the same partition.
     */
    public boolean arePlayersInSamePartition(@NotNull Player player1, @NotNull Player player2) {
        String partition1 = getPartitionForPlayer(player1);
        String partition2 = getPartitionForPlayer(player2);

        return partition1 != null && partition1.equals(partition2);
    }

    /**
     * Shuts down the partition manager.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down partition manager...");

        // Unload all partitions
        new ArrayList<>(partitions.keySet()).forEach(this::unloadPartition);

        partitions.clear();
        playerPartitions.clear();
        worldPartitions.clear();

        plugin.getLogger().info("Partition manager shut down");
    }
}