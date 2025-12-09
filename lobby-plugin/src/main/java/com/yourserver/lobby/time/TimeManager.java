package com.yourserver.lobby.time;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages world time to keep it always day (if enabled).
 * Runs a task that checks and sets time every second.
 */
public class TimeManager {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private BukkitTask timeTask;

    public TimeManager(LobbyPlugin plugin, LobbyConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Starts the time management task.
     */
    public void startTimeTask() {
        if (!config.getProtectionConfig().isAlwaysDay()) {
            return; // Don't start if always-day is disabled
        }

        if (timeTask != null) {
            timeTask.cancel();
        }

        // Run every second (20 ticks)
        timeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long targetTime = config.getProtectionConfig().getDayTime();

            // Set time for all worlds (or just spawn world)
            for (World world : Bukkit.getWorlds()) {
                if (world.getTime() != targetTime) {
                    world.setTime(targetTime);
                }
            }
        }, 0L, 20L);

        plugin.getLogger().info("Time manager started - keeping time at " +
                config.getProtectionConfig().getDayTime());
    }

    /**
     * Shuts down the time manager.
     */
    public void shutdown() {
        if (timeTask != null) {
            timeTask.cancel();
            timeTask = null;
        }
    }
}