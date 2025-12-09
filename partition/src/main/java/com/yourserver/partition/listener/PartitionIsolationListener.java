package com.yourserver.partition.listener;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.manager.PartitionManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete isolation listener for partitions.
 * Ensures each partition behaves like a separate server:
 * - Isolated chat
 * - Isolated tab list (player visibility)
 * - Isolated commands (tab completion)
 */
public class PartitionIsolationListener implements Listener {

    private final PartitionPlugin plugin;
    private final PartitionManager partitionManager;

    public PartitionIsolationListener(PartitionPlugin plugin, PartitionManager partitionManager) {
        this.plugin = plugin;
        this.partitionManager = partitionManager;
    }

    // ===== CHAT ISOLATION =====

    /**
     * Isolates chat to only players in the same partition.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getPartitionConfig().getIsolationSettings().isChatIsolation()) {
            return; // Chat isolation disabled
        }

        Player sender = event.getPlayer();
        String senderPartition = partitionManager.getPartitionForPlayer(sender);

        if (senderPartition == null) {
            return; // Not in any partition
        }

        // Filter recipients to only players in same partition
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player recipient)) {
                return false; // Don't remove console
            }

            String recipientPartition = partitionManager.getPartitionForPlayer(recipient);
            return !senderPartition.equals(recipientPartition);
        });

        plugin.getLogger().fine("Chat isolated to partition: " + senderPartition +
                " (" + event.viewers().size() + " recipients)");
    }

    // ===== TAB LIST ISOLATION =====

    /**
     * When a player joins, update tab list for all players.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getPartitionConfig().getIsolationSettings().isTablistIsolation()) {
            return;
        }

        Player joiningPlayer = event.getPlayer();
        String joiningPartition = partitionManager.getPartitionForPlayer(joiningPlayer);

        if (joiningPartition == null) {
            return;
        }

        // Update tab list for joining player (after a small delay to ensure world is loaded)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateTabListForPlayer(joiningPlayer, joiningPartition);

            // Update tab list for all other players in same partition
            List<Player> playersInPartition = partitionManager.getPlayersInPartition(joiningPartition);
            for (Player other : playersInPartition) {
                if (!other.equals(joiningPlayer)) {
                    updateTabListForPlayer(other, joiningPartition);
                }
            }

            // Also update players in OTHER partitions (to hide the joining player from them)
            for (Player other : Bukkit.getOnlinePlayers()) {
                String otherPartition = partitionManager.getPartitionForPlayer(other);
                if (otherPartition != null && !otherPartition.equals(joiningPartition)) {
                    updateTabListForPlayer(other, otherPartition);
                }
            }
        }, 20L); // 1 second delay
    }

    /**
     * When a player quits, update tab list for remaining players.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getPartitionConfig().getIsolationSettings().isTablistIsolation()) {
            return;
        }

        Player quittingPlayer = event.getPlayer();
        String quittingPartition = partitionManager.getPartitionForPlayer(quittingPlayer);

        if (quittingPartition == null) {
            return;
        }

        // Update tab list for all other players in same partition
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Player> playersInPartition = partitionManager.getPlayersInPartition(quittingPartition);
            for (Player other : playersInPartition) {
                if (!other.equals(quittingPlayer)) {
                    updateTabListForPlayer(other, quittingPartition);
                }
            }
        });
    }

    /**
     * When a player changes worlds (and potentially partitions), update tab lists.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!plugin.getPartitionConfig().getIsolationSettings().isTablistIsolation()) {
            return;
        }

        Player player = event.getPlayer();
        String oldPartition = partitionManager.getPartitionForWorld(event.getFrom().getName());
        String newPartition = partitionManager.getPartitionForPlayer(player);

        // If partition changed, update tab lists
        if (!java.util.Objects.equals(oldPartition, newPartition)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Update for moving player
                if (newPartition != null) {
                    updateTabListForPlayer(player, newPartition);
                }

                // Update for old partition players
                if (oldPartition != null) {
                    List<Player> oldPlayers = partitionManager.getPlayersInPartition(oldPartition);
                    for (Player other : oldPlayers) {
                        updateTabListForPlayer(other, oldPartition);
                    }
                }

                // Update for new partition players
                if (newPartition != null) {
                    List<Player> newPlayers = partitionManager.getPlayersInPartition(newPartition);
                    for (Player other : newPlayers) {
                        if (!other.equals(player)) {
                            updateTabListForPlayer(other, newPartition);
                        }
                    }
                }
            });
        }
    }

    /**
     * Updates the tab list for a specific player to only show players in their partition.
     * Uses hidePlayer/showPlayer to control visibility.
     */
    private void updateTabListForPlayer(Player player, String partition) {
        List<Player> playersInPartition = partitionManager.getPlayersInPartition(partition);
        List<Player> allPlayers = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player other : allPlayers) {
            if (playersInPartition.contains(other)) {
                // Show players in same partition
                player.showPlayer(plugin, other);
            } else {
                // Hide players in other partitions
                player.hidePlayer(plugin, other);
            }
        }

        plugin.getLogger().fine("Updated tab list for " + player.getName() +
                " - showing " + playersInPartition.size() + " players in partition: " + partition);
    }

    // ===== COMMAND TAB COMPLETION ISOLATION =====

    /**
     * Isolates command tab completion to only show players in the same partition.
     * This affects commands like /tp <player>, /msg <player>, etc.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTabComplete(TabCompleteEvent event) {
        if (!plugin.getPartitionConfig().getIsolationSettings().isCommandIsolation()) {
            return;
        }

        if (!(event.getSender() instanceof Player player)) {
            return; // Console can see all players
        }

        String playerPartition = partitionManager.getPartitionForPlayer(player);
        if (playerPartition == null) {
            return;
        }

        // Filter completions to only include players in same partition
        List<String> completions = new ArrayList<>(event.getCompletions());
        completions.removeIf(completion -> {
            Player target = Bukkit.getPlayerExact(completion);
            if (target == null) {
                return false; // Keep non-player completions
            }

            String targetPartition = partitionManager.getPartitionForPlayer(target);
            return !playerPartition.equals(targetPartition);
        });

        event.setCompletions(completions);
    }
}