package com.yourserver.lobby.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Helper to access partition data WITHOUT importing PartitionPlugin.
 * Uses reflection to avoid circular dependencies.
 *
 * This is the ONLY class you need in LobbyPlugin for partition support!
 * No imports, no dependencies, just reflection.
 *
 * Usage in ScoreboardManager:
 * ```java
 * private String parsePlaceholders(String text, Player player) {
 *     // Use partition-aware counts instead of Bukkit.getOnlinePlayers().size()
 *     int online = PartitionHelper.getOnlineCount(player);
 *     int maxPlayers = PartitionHelper.getMaxPlayers(player);
 *
 *     return text
 *         .replace("{online}", String.valueOf(online))
 *         .replace("{max_players}", String.valueOf(maxPlayers))
 *         .replace("{player}", player.getName());
 * }
 * ```
 */
public class PartitionHelper {

    private static Plugin partitionPlugin = null;
    private static Boolean available = null;

    /**
     * Checks if PartitionPlugin is available.
     */
    public static boolean isAvailable() {
        if (available == null) {
            partitionPlugin = Bukkit.getPluginManager().getPlugin("PartitionPlugin");
            available = partitionPlugin != null && partitionPlugin.isEnabled();
        }
        return available;
    }

    /**
     * Gets the online count for a player's partition.
     * Falls back to total server count if partitions disabled.
     *
     * USE THIS instead of Bukkit.getOnlinePlayers().size()
     */
    public static int getOnlineCount(@NotNull Player player) {
        if (!isAvailable()) {
            return Bukkit.getOnlinePlayers().size();
        }

        try {
            // Call: PartitionPlugin.getIsolationSystem().getPartitionOnlineCount(player)
            Object isolationSystem = invoke(partitionPlugin, "getIsolationSystem");
            if (isolationSystem != null) {
                Integer count = (Integer) invoke(isolationSystem, "getPartitionOnlineCount",
                        new Class<?>[]{Player.class}, player);
                if (count != null) {
                    return count;
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }

        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Gets the max players for a player's partition.
     * Falls back to server max if partitions disabled.
     *
     * USE THIS instead of Bukkit.getMaxPlayers()
     */
    public static int getMaxPlayers(@NotNull Player player) {
        if (!isAvailable()) {
            return Bukkit.getMaxPlayers();
        }

        try {
            // Get partition ID first
            String partitionId = getPartitionId(player);
            if (partitionId != null) {
                Object isolationSystem = invoke(partitionPlugin, "getIsolationSystem");
                if (isolationSystem != null) {
                    Integer max = (Integer) invoke(isolationSystem, "getPartitionMaxPlayers",
                            new Class<?>[]{String.class}, partitionId);
                    if (max != null) {
                        return max;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback silently
        }

        return Bukkit.getMaxPlayers();
    }

    /**
     * Gets the partition ID for a player.
     * Returns null if partitions disabled or player not in partition.
     */
    @Nullable
    public static String getPartitionId(@NotNull Player player) {
        if (!isAvailable()) {
            return null;
        }

        try {
            Object partitionManager = invoke(partitionPlugin, "getPartitionManager");
            if (partitionManager != null) {
                return (String) invoke(partitionManager, "getPartitionForPlayer",
                        new Class<?>[]{Player.class}, player);
            }
        } catch (Exception e) {
            // Return null silently
        }

        return null;
    }

    /**
     * Gets the partition name (human-readable) for a player.
     */
    @Nullable
    public static String getPartitionName(@NotNull Player player) {
        if (!isAvailable()) {
            return null;
        }

        try {
            String partitionId = getPartitionId(player);
            if (partitionId == null) return null;

            Object partitionManager = invoke(partitionPlugin, "getPartitionManager");
            if (partitionManager != null) {
                Object partition = invoke(partitionManager, "getPartition",
                        new Class<?>[]{String.class}, partitionId);
                if (partition != null) {
                    return (String) invoke(partition, "getName");
                }
            }
        } catch (Exception e) {
            // Return null silently
        }

        return null;
    }

    /**
     * Replaces placeholders with partition-aware values.
     *
     * Example:
     * ```java
     * String text = "{online}/{max_players} players";
     * text = PartitionHelper.replacePlaceholders(text, player);
     * // Result: "5/50 players" (only counts player's partition)
     * ```
     */
    @NotNull
    public static String replacePlaceholders(@NotNull String text, @NotNull Player player) {
        int online = getOnlineCount(player);
        int maxPlayers = getMaxPlayers(player);
        String partitionName = getPartitionName(player);
        String partitionId = getPartitionId(player);

        return text
                .replace("{online}", String.valueOf(online))
                .replace("{max_players}", String.valueOf(maxPlayers))
                .replace("{partition}", partitionName != null ? partitionName : "")
                .replace("{partition_id}", partitionId != null ? partitionId : "");
    }

    // ===== REFLECTION UTILITIES =====

    private static Object invoke(Object obj, String methodName) throws Exception {
        Method method = obj.getClass().getMethod(methodName);
        return method.invoke(obj);
    }

    private static Object invoke(Object obj, String methodName, Class<?>[] paramTypes, Object... params) throws Exception {
        Method method = obj.getClass().getMethod(methodName, paramTypes);
        return method.invoke(obj, params);
    }
}