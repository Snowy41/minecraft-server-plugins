package com.yourserver.lobby.scoreboard;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Scheduled task to update all scoreboards.
 */
public class ScoreboardUpdateTask extends BukkitRunnable {

    private final ScoreboardManager scoreboardManager;

    public ScoreboardUpdateTask(ScoreboardManager scoreboardManager) {
        this.scoreboardManager = scoreboardManager;
    }

    @Override
    public void run() {
        scoreboardManager.updateAll();
    }
}