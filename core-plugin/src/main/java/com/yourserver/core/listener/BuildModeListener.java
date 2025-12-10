package com.yourserver.core.listener;

import com.yourserver.core.buildmode.BuildModeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles build mode state cleanup when players quit.
 */
public class BuildModeListener implements Listener {

    private final BuildModeManager buildModeManager;

    public BuildModeListener(BuildModeManager buildModeManager) {
        this.buildModeManager = buildModeManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        buildModeManager.clearState(player);
    }
}