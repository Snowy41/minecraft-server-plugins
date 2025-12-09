package com.yourserver.lobby.tablist;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduled task to update all tab lists.
 */
public class TabListUpdateTask extends BukkitRunnable {

    private final TabListManager tabListManager;

    public TabListUpdateTask(TabListManager tabListManager) {
        this.tabListManager = tabListManager;
    }

    @Override
    public void run() {
        tabListManager.updateAll();
    }
}