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
    private final WorldManager worldManager;
    private final Map<String, Partition> partitions;
    private final Map<UUID, String> playerPartitions; // Player UUID -> Partition ID
    private final Map<String, String> worldPartitions; // World name -> Partition ID

    public PartitionManager(PartitionPlugin plugin, PartitionConfig config, WorldManager worldManager) {
        this.plugin = plugin;
        this.config = config;
        this.partitions = new ConcurrentHashMap<>();
        this.playerPartitions = new ConcurrentHashMap<>();
        this.worldPartitions = new ConcurrentHashMap<>();
        this.worldManager = worldManager;
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

        // IMPORTANT: Ensure all worlds exist and are loaded
        plugin.getLogger().info("Ensuring worlds are loaded for partition: " + partitionId);
        for (String worldName : settings.getWorlds()) {
            World world = worldManager.ensureWorldLoaded(worldName);
            if (world == null) {
                plugin.getLogger().severe("Failed to load/create world: " + worldName);
                plugin.getLogger().severe("Partition load aborted: " + partitionId);
                return false;
            }
        }

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
        plugin.getPluginHotLoader().loadPluginsForPartition(partition);

        partitions.put(partitionId, partition);
        plugin.getLogger().info("Partition loaded: " + partitionId + " (" + partition.getName() + ")");

        return true;
    }

    /**
     * Unloads a partition (saves worlds, unloads plugins).
     * CRITICAL FIX: Check if players are actually in THIS partition before moving them!
     */
    public boolean unloadPartition(@NotNull String partitionId) {
        Partition partition = partitions.get(partitionId);
        if (partition == null) {
            plugin.getLogger().warning("Partition not loaded: " + partitionId);
            return false;
        }

        plugin.getLogger().info("Unloading partition: " + partitionId);

        // CRITICAL FIX: Only get players who are ACTUALLY in this partition!
        List<Player> playersInPartition = getPlayersInPartition(partitionId);

        plugin.getLogger().info("Found " + playersInPartition.size() +
                " players in partition '" + partitionId + "'");

        // Only move players if there are any AND we can find a default partition
        if (!playersInPartition.isEmpty()) {
            plugin.getLogger().info("Moving " + playersInPartition.size() +
                    " players out of partition");

            // Find a default partition to move them to
            Partition defaultPartition = getDefaultPartition(partitionId);

            if (defaultPartition == null) {
                plugin.getLogger().severe("No default partition found! Cannot move players!");
                plugin.getLogger().severe("Create a 'lobby' partition before restarting others!");
                return false;
            }

            plugin.getLogger().info("Moving players to: " + defaultPartition.getId());

            // CRITICAL: Check if we're on the main thread
            boolean isMainThread = Bukkit.isPrimaryThread();

            if (isMainThread) {
                // Already on main thread - teleport directly
                for (Player player : playersInPartition) {
                    plugin.getLogger().info("  - Moving " + player.getName() +
                            " to " + defaultPartition.getId());
                    movePlayerToPartition(player, defaultPartition.getId());
                }
            } else {
                // Not on main thread - schedule sync task
                // Use a CountDownLatch to wait for completion
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(1);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        for (Player player : playersInPartition) {
                            plugin.getLogger().info("  - Moving " + player.getName() +
                                    " to " + defaultPartition.getId());
                            movePlayerToPartition(player, defaultPartition.getId());
                        }
                    } finally {
                        latch.countDown();
                    }
                });

                // Wait for teleports to complete (max 5 seconds)
                try {
                    if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        plugin.getLogger().warning("Timeout waiting for player teleports!");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().warning("Interrupted while waiting for teleports!");
                }
            }

            // Small delay to ensure teleports are processed
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            plugin.getLogger().info("No players in partition '" + partitionId +
                    "' - skipping player movement");
        }

        // Unload plugins
        plugin.getPluginHotLoader().unloadPluginsForPartition(partition);

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
     * MUST be called synchronously on main thread!
     * FIXED: Proper thread handling and player detection
     */
    public boolean restartPartition(@NotNull String partitionId) {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("RESTARTING PARTITION: " + partitionId);
        plugin.getLogger().info("========================================");

        if (!partitions.containsKey(partitionId)) {
            plugin.getLogger().warning("Cannot restart partition that isn't loaded: " + partitionId);
            return false;
        }

        // CRITICAL: Must be on main thread
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().severe("restartPartition() called from async thread! This will fail!");
            plugin.getLogger().severe("Stack trace:");
            Thread.dumpStack();
            return false;
        }

        // Unload
        if (!unloadPartition(partitionId)) {
            plugin.getLogger().severe("Failed to unload partition: " + partitionId);
            return false;
        }

        // Wait a bit for cleanup
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Load
        boolean success = loadPartition(partitionId);

        if (success) {
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("✓ PARTITION RESTART COMPLETE: " + partitionId);
            plugin.getLogger().info("========================================");
        } else {
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("✗ PARTITION RESTART FAILED: " + partitionId);
            plugin.getLogger().severe("========================================");
        }

        return success;
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
     * DIAGNOSTIC: Gets all players in a partition with detailed logging.
     */
    @NotNull
    public List<Player> getPlayersInPartition(@NotNull String partitionId) {
        List<Player> players = new ArrayList<>();

        plugin.getLogger().fine("=== Checking players for partition: " + partitionId + " ===");

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerWorld = player.getWorld().getName();
            String playerPartition = getPartitionForPlayer(player);

            plugin.getLogger().fine("  Player: " + player.getName());
            plugin.getLogger().fine("    World: " + playerWorld);
            plugin.getLogger().fine("    Partition: " + playerPartition);
            plugin.getLogger().fine("    Match: " + partitionId.equals(playerPartition));

            if (partitionId.equals(playerPartition)) {
                players.add(player);
                plugin.getLogger().info("  ✓ " + player.getName() +
                        " is in partition '" + partitionId + "' (world: " + playerWorld + ")");
            }
        }

        plugin.getLogger().fine("=== Found " + players.size() + " players ===");

        return players;
    }

    /**
     * Moves a player to a specific partition.
     * MUST be called from main thread!
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

        // Update cached partition BEFORE teleporting
        playerPartitions.put(player.getUniqueId(), partitionId);

        // Teleport player (MUST be on main thread!)
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
     * Gets the default partition (first enabled partition that isn't the one being unloaded).
     */
    @Nullable
    public Partition getDefaultPartition(String excludePartitionId) {
        return partitions.values().stream()
                .filter(p -> !p.getId().equals(excludePartitionId))
                .findFirst()
                .orElse(null);
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