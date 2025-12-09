package com.yourserver.core.listener;

import com.yourserver.core.player.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles player connection events (join/quit).
 * Loads player data on join and saves on quit.
 */
public class PlayerConnectionListener implements Listener {

    private final PlayerDataManager playerDataManager;
    private final Logger logger = Logger.getLogger(getClass().getName());

    public PlayerConnectionListener(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    /**
     * Handles player join event.
     * Loads player data from database asynchronously.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data asynchronously
        playerDataManager.handlePlayerJoin(player.getUniqueId(), player.getName())
                .exceptionally(throwable -> {
                    logger.log(Level.SEVERE,
                            "Failed to load player data for " + player.getName(),
                            throwable);
                    return null;
                });
    }

    /**
     * Handles player quit event.
     * Saves player data to database asynchronously.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Save player data asynchronously
        playerDataManager.handlePlayerQuit(player.getUniqueId())
                .exceptionally(throwable -> {
                    logger.log(Level.SEVERE,
                            "Failed to save player data for " + player.getName(),
                            throwable);
                    return null;
                });
    }
}