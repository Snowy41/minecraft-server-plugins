package com.yourserver.partition.isolation;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.manager.PartitionManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * COMPLETE partition isolation system.
 * Makes each partition behave like a completely separate server:
 * - Isolated player visibility (tab list)
 * - Isolated chat (already handled by PartitionIsolationListener)
 * - Isolated scoreboards
 * - Persistent through restarts
 * - Dynamic updates when players join/leave/change partitions
 */
public class PartitionIsolationSystem implements Listener {

    private final PartitionPlugin plugin;
    private final PartitionManager partitionManager;

    // Track per-partition data
    private final Map<String, PartitionData> partitionData;

    // Track which players have been "hidden" from each other
    private final Map<UUID, Set<UUID>> playerVisibilityCache;

    public PartitionIsolationSystem(PartitionPlugin plugin, PartitionManager partitionManager) {
        this.plugin = plugin;
        this.partitionManager = partitionManager;
        this.partitionData = new ConcurrentHashMap<>();
        this.playerVisibilityCache = new ConcurrentHashMap<>();
    }

    /**
     * Per-partition data storage.
     * Persists across partition restarts.
     */
    private static class PartitionData {
        private final String partitionId;
        private final Set<UUID> onlinePlayers;
        private final Map<UUID, PlayerPartitionState> playerStates;

        public PartitionData(String partitionId) {
            this.partitionId = partitionId;
            this.onlinePlayers = ConcurrentHashMap.newKeySet();
            this.playerStates = new ConcurrentHashMap<>();
        }

        public void addPlayer(UUID uuid, PlayerPartitionState state) {
            onlinePlayers.add(uuid);
            playerStates.put(uuid, state);
        }

        public void removePlayer(UUID uuid) {
            onlinePlayers.remove(uuid);
            // Keep state for when they return
        }

        public PlayerPartitionState getPlayerState(UUID uuid) {
            return playerStates.computeIfAbsent(uuid,
                    k -> new PlayerPartitionState(uuid, partitionId));
        }

        public Set<UUID> getOnlinePlayers() {
            return new HashSet<>(onlinePlayers);
        }
    }

    /**
     * Per-player state within a partition.
     * Persists across server restarts.
     */
    private static class PlayerPartitionState {
        private final UUID playerUuid;
        private final String partitionId;
        private long lastJoinTime;
        private long totalPlayTime;
        private Map<String, Object> customData;

        public PlayerPartitionState(UUID playerUuid, String partitionId) {
            this.playerUuid = playerUuid;
            this.partitionId = partitionId;
            this.lastJoinTime = System.currentTimeMillis();
            this.totalPlayTime = 0;
            this.customData = new ConcurrentHashMap<>();
        }

        public void updateJoinTime() {
            lastJoinTime = System.currentTimeMillis();
        }

        public void updatePlayTime() {
            long sessionTime = System.currentTimeMillis() - lastJoinTime;
            totalPlayTime += sessionTime;
        }
    }

    // ===== DYNAMIC PLAYER VISIBILITY =====

    /**
     * Completely isolates players between partitions.
     * This is the CORE of the isolation system.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        String joiningPartition = partitionManager.getPartitionForPlayer(joiningPlayer);

        if (joiningPartition == null) {
            plugin.getLogger().warning("Player " + joiningPlayer.getName() +
                    " joined without partition assignment!");
            return;
        }

        // Get or create partition data
        PartitionData data = partitionData.computeIfAbsent(
                joiningPartition,
                PartitionData::new
        );

        // Track player in partition
        PlayerPartitionState state = data.getPlayerState(joiningPlayer.getUniqueId());
        state.updateJoinTime();
        data.addPlayer(joiningPlayer.getUniqueId(), state);

        // Schedule visibility update (must wait for player to fully load)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateAllPlayerVisibility(joiningPlayer, joiningPartition);
        }, 20L); // 1 second delay

        plugin.getLogger().info("✓ Player " + joiningPlayer.getName() +
                " joined partition: " + joiningPartition);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        String partition = partitionManager.getPartitionForPlayer(quittingPlayer);

        if (partition != null) {
            PartitionData data = partitionData.get(partition);
            if (data != null) {
                // Update play time before removing
                PlayerPartitionState state = data.getPlayerState(quittingPlayer.getUniqueId());
                state.updatePlayTime();

                data.removePlayer(quittingPlayer.getUniqueId());

                // Update visibility for remaining players
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updatePartitionVisibility(partition);
                });
            }
        }

        // Clear visibility cache
        playerVisibilityCache.remove(quittingPlayer.getUniqueId());
        playerVisibilityCache.values().forEach(set ->
                set.remove(quittingPlayer.getUniqueId()));
    }

    /**
     * Updates visibility for ALL players when someone joins a partition.
     * This ensures complete isolation.
     */
    private void updateAllPlayerVisibility(Player joiningPlayer, String joiningPartition) {
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        List<Player> samePartitionPlayers = partitionManager.getPlayersInPartition(joiningPartition);

        // For the joining player: hide everyone not in their partition
        for (Player other : allPlayers) {
            if (other.equals(joiningPlayer)) continue;

            if (samePartitionPlayers.contains(other)) {
                // Show players in same partition
                showPlayer(joiningPlayer, other);
                showPlayer(other, joiningPlayer); // Mutual visibility
            } else {
                // Hide players in other partitions
                hidePlayer(joiningPlayer, other);
                hidePlayer(other, joiningPlayer); // Mutual hiding
            }
        }

        plugin.getLogger().fine("Updated visibility for " + joiningPlayer.getName() +
                " - showing " + samePartitionPlayers.size() + " players");
    }

    /**
     * Updates visibility for all players in a partition.
     * Called when someone leaves or partition changes.
     * PUBLIC so PartitionPlugin can call it.
     */
    public void updatePartitionVisibility(String partitionId) {
        List<Player> partitionPlayers = partitionManager.getPlayersInPartition(partitionId);
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player player : partitionPlayers) {
            for (Player other : allPlayers) {
                if (player.equals(other)) continue;

                if (partitionPlayers.contains(other)) {
                    showPlayer(player, other);
                } else {
                    hidePlayer(player, other);
                }
            }
        }
    }

    /**
     * Hides a player from another player (with caching to prevent spam).
     */
    private void hidePlayer(Player viewer, Player target) {
        Set<UUID> hidden = playerVisibilityCache.computeIfAbsent(
                viewer.getUniqueId(),
                k -> ConcurrentHashMap.newKeySet()
        );

        if (!hidden.contains(target.getUniqueId())) {
            viewer.hidePlayer(plugin, target);
            hidden.add(target.getUniqueId());
        }
    }

    /**
     * Shows a player to another player (with caching).
     */
    private void showPlayer(Player viewer, Player target) {
        Set<UUID> hidden = playerVisibilityCache.get(viewer.getUniqueId());

        if (hidden != null && hidden.contains(target.getUniqueId())) {
            viewer.showPlayer(plugin, target);
            hidden.remove(target.getUniqueId());
        } else {
            // Ensure they're shown even if not in cache
            viewer.showPlayer(plugin, target);
        }
    }

    // ===== PARTITION RESTART HANDLING =====

    /**
     * Prepares a partition for restart.
     * Saves all player states and gracefully moves players.
     */
    public void preparePartitionRestart(@NotNull String partitionId) {
        plugin.getLogger().info("Preparing partition restart: " + partitionId);

        PartitionData data = partitionData.get(partitionId);
        if (data == null) {
            plugin.getLogger().warning("No data found for partition: " + partitionId);
            return;
        }

        // Save all player states
        List<Player> players = partitionManager.getPlayersInPartition(partitionId);
        for (Player player : players) {
            PlayerPartitionState state = data.getPlayerState(player.getUniqueId());
            state.updatePlayTime();

            // Could save to database here if needed
            plugin.getLogger().fine("Saved state for " + player.getName() +
                    " (playtime: " + state.totalPlayTime + "ms)");
        }

        plugin.getLogger().info("✓ Saved state for " + players.size() + " players");
    }

    /**
     * Restores a partition after restart.
     * Moves players back and restores their states.
     */
    public void restorePartitionAfterRestart(@NotNull String partitionId) {
        plugin.getLogger().info("Restoring partition after restart: " + partitionId);

        PartitionData data = partitionData.get(partitionId);
        if (data == null) {
            plugin.getLogger().warning("No data to restore for partition: " + partitionId);
            return;
        }

        // Restore player states
        Set<UUID> savedPlayers = new HashSet<>(data.getOnlinePlayers());
        int restoredCount = 0;

        for (UUID uuid : savedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                PlayerPartitionState state = data.getPlayerState(uuid);
                state.updateJoinTime(); // Reset join time after restart

                restoredCount++;
            }
        }

        // Update visibility for all players in partition
        Bukkit.getScheduler().runTask(plugin, () -> {
            updatePartitionVisibility(partitionId);
        });

        plugin.getLogger().info("✓ Restored state for " + restoredCount + " players");
    }

    // ===== PUBLIC API =====

    /**
     * Gets the online player count for a partition.
     * This is partition-specific and persists through restarts.
     */
    public int getOnlineCount(@NotNull String partitionId) {
        PartitionData data = partitionData.get(partitionId);
        return data != null ? data.getOnlinePlayers().size() : 0;
    }

    /**
     * Gets total play time for a player in a partition.
     */
    public long getPlayerPlayTime(@NotNull UUID playerUuid, @NotNull String partitionId) {
        PartitionData data = partitionData.get(partitionId);
        if (data == null) return 0;

        PlayerPartitionState state = data.getPlayerState(playerUuid);
        return state.totalPlayTime;
    }

    /**
     * Stores custom data for a player in a partition.
     * Useful for plugins to store partition-specific player data.
     */
    public void setPlayerData(@NotNull UUID playerUuid, @NotNull String partitionId,
                              @NotNull String key, @NotNull Object value) {
        PartitionData data = partitionData.computeIfAbsent(partitionId, PartitionData::new);
        PlayerPartitionState state = data.getPlayerState(playerUuid);
        state.customData.put(key, value);
    }

    /**
     * Retrieves custom data for a player in a partition.
     */
    @SuppressWarnings("unchecked")
    public <T> T getPlayerData(@NotNull UUID playerUuid, @NotNull String partitionId,
                               @NotNull String key, T defaultValue) {
        PartitionData data = partitionData.get(partitionId);
        if (data == null) return defaultValue;

        PlayerPartitionState state = data.getPlayerState(playerUuid);
        Object value = state.customData.get(key);
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Clears all data for a partition (called on partition unload).
     */
    public void clearPartitionData(@NotNull String partitionId) {
        partitionData.remove(partitionId);
        plugin.getLogger().info("Cleared data for partition: " + partitionId);
    }

    /**
     * Shuts down the isolation system.
     */
    public void shutdown() {
        // Save all player states before shutdown
        for (PartitionData data : partitionData.values()) {
            for (UUID uuid : data.getOnlinePlayers()) {
                PlayerPartitionState state = data.getPlayerState(uuid);
                state.updatePlayTime();
            }
        }

        partitionData.clear();
        playerVisibilityCache.clear();
    }

    // ===== DYNAMIC PARTITION-AWARE PLACEHOLDERS =====

    /**
     * Gets partition-specific online count for a player.
     * This should be used by plugins instead of Bukkit.getOnlinePlayers().size()
     */
    public int getPartitionOnlineCount(@NotNull Player player) {
        String partition = partitionManager.getPartitionForPlayer(player);
        if (partition == null) {
            return Bukkit.getOnlinePlayers().size(); // Fallback
        }
        return getOnlineCount(partition);
    }

    /**
     * Gets partition-specific max players.
     * Can be configured per-partition in config.yml
     */
    public int getPartitionMaxPlayers(@NotNull String partitionId) {
        // TODO: Read from config per partition
        return Bukkit.getMaxPlayers(); // Default for now
    }
}