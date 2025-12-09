package com.yourserver.lobby.cosmetics.trail;

import com.yourserver.lobby.cosmetics.CosmeticsManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduled task to update all particle trails.
 */
public class TrailTask extends BukkitRunnable {

    private final CosmeticsManager cosmeticsManager;

    public TrailTask(CosmeticsManager cosmeticsManager) {
        this.cosmeticsManager = cosmeticsManager;
    }

    @Override
    public void run() {
        cosmeticsManager.updateTrails();
    }
}