package com.yourserver.partition.listener;

import com.yourserver.partition.manager.PartitionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player world changes and updates partition tracking.
 */
public class PlayerWorldChangeListener implements Listener {

    private final PartitionManager partitionManager;

    public PlayerWorldChangeListener(PartitionManager partitionManager) {
        this.partitionManager = partitionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        partitionManager.updatePlayerPartition(player, player.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        partitionManager.clearPlayerPartition(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        partitionManager.updatePlayerPartition(player, player.getWorld());
    }
}