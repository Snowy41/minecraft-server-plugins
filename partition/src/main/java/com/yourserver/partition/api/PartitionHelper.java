package com.yourserver.partition.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Enhanced partition helper for plugins to use.
 * Uses reflection to avoid circular dependencies.
 *
 * Usage in LobbyPlugin or other plugins:
 * ```java
 * PartitionHelper helper = PartitionHelper.get();
 *
 * // Get partition-specific online count for scoreboard
 * int online = helper.getOnlineCount(player);
 *
 * // Check if player should see another player
 * if (helper.areInSamePartition(player1, player2)) {
 *     // Show them to each other
 * }
 * ```
 */
public class PartitionHelper {

    private static PartitionHelper instance;
    private final Plugin partitionPlugin;
    private final boolean available;

    private PartitionHelper(Plugin partitionPlugin, boolean available) {
        this.partitionPlugin = partitionPlugin;
        this.available = available;
    }

    /**
     * Gets the PartitionHelper instance.
     * Thread-safe singleton.
     */
    @NotNull
    public static synchronized PartitionHelper get() {
        if (instance == null) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("PartitionPlugin");
            instance = new PartitionHelper(plugin, plugin != null && plugin.isEnabled());
        }
        return instance;
    }

    /**
     * Checks if PartitionPlugin is available.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets the online count for a player's partition.
     * If partitions are disabled, returns total server count.
     *
     * USE THIS instead of Bukkit.getOnlinePlayers().size() in your scoreboard/tablist!
     */
    public int getOnlineCount(@NotNull Player player) {
        if (!available) {
            return Bukkit.getOnlinePlayers().size();
        }

        try {
            // Call PartitionPlugin.getIsolationSystem().getPartitionOnlineCount(player)
            Object isolationSystem = invokeMethod(partitionPlugin, "getIsolationSystem");
            if (isolationSystem != null) {
                Integer count = (Integer) invokeMethod(
                        isolationSystem,
                        "getPartitionOnlineCount",
                        Player.class,
                        player
                );
                return count != null ? count : Bukkit.getOnlinePlayers().size();
            }
        } catch (Exception e) {
            // Fallback to total count
        }

        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Gets the partition ID for a player.
     */
    @Nullable
    public String getPartitionId(@NotNull Player player) {
        if (!available) {
            return null;
        }

        try {
            Object partitionManager = invokeMethod(partitionPlugin, "getPartitionManager");
            if (partitionManager != null) {
                return (String) invokeMethod(
                        partitionManager,
                        "getPartitionForPlayer",
                        Player.class,
                        player
                );
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Gets the partition name (human-readable) for a player.
     */
    @Nullable
    public String getPartitionName(@NotNull Player player) {
        if (!available) {
            return null;
        }

        try {
            String partitionId = getPartitionId(player);
            if (partitionId == null) return null;

            Object partitionManager = invokeMethod(partitionPlugin, "getPartitionManager");
            if (partitionManager != null) {
                Object partition = invokeMethod(
                        partitionManager,
                        "getPartition",
                        String.class,
                        partitionId
                );
                if (partition != null) {
                    return (String) invokeMethod(partition, "getName");
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }

    /**
     * Checks if two players are in the same partition.
     */
    public boolean areInSamePartition(@NotNull Player player1, @NotNull Player player2) {
        if (!available) {
            return true; // No partitions = everyone together
        }

        String partition1 = getPartitionId(player1);
        String partition2 = getPartitionId(player2);

        return partition1 != null && partition1.equals(partition2);
    }

    /**
     * Gets all players in the same partition as the given player.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public List<Player> getPlayersInSamePartition(@NotNull Player player) {
        if (!available) {
            return List.copyOf(Bukkit.getOnlinePlayers());
        }

        try {
            String partitionId = getPartitionId(player);
            if (partitionId != null) {
                Object partitionManager = invokeMethod(partitionPlugin, "getPartitionManager");
                if (partitionManager != null) {
                    return (List<Player>) invokeMethod(
                            partitionManager,
                            "getPlayersInPartition",
                            String.class,
                            partitionId
                    );
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        return List.copyOf(Bukkit.getOnlinePlayers());
    }

    /**
     * Gets the max players for a player's partition.
     */
    public int getMaxPlayers(@NotNull Player player) {
        if (!available) {
            return Bukkit.getMaxPlayers();
        }

        try {
            String partitionId = getPartitionId(player);
            if (partitionId != null) {
                Object isolationSystem = invokeMethod(partitionPlugin, "getIsolationSystem");
                if (isolationSystem != null) {
                    Integer max = (Integer) invokeMethod(
                            isolationSystem,
                            "getPartitionMaxPlayers",
                            String.class,
                            partitionId
                    );
                    return max != null ? max : Bukkit.getMaxPlayers();
                }
            }
        } catch (Exception e) {
            // Fallback
        }

        return Bukkit.getMaxPlayers();
    }

    // ===== REFLECTION UTILITIES =====

    /**
     * Invokes a method on an object using reflection.
     */
    @Nullable
    private Object invokeMethod(@NotNull Object object, @NotNull String methodName) {
        try {
            Method method = object.getClass().getMethod(methodName);
            return method.invoke(object);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invokes a method with parameters using reflection.
     */
    @Nullable
    private Object invokeMethod(@NotNull Object object, @NotNull String methodName,
                                @NotNull Class<?> paramType, @NotNull Object param) {
        try {
            Method method = object.getClass().getMethod(methodName, paramType);
            return method.invoke(object, param);
        } catch (Exception e) {
            return null;
        }
    }
}