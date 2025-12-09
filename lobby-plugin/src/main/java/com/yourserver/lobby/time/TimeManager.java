package com.yourserver.lobby.time;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages world time to keep it always day (if enabled).
 * Runs a task that checks and sets time every 5 seconds.
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
            plugin.getLogger().info("Time manager not started - always-day is disabled");
            return;
        }

        if (timeTask != null) {
            timeTask.cancel();
        }

        long targetTime = config.getProtectionConfig().getDayTime();

        // Run every 5 seconds (100 ticks) - more frequent than before
        timeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Set time for all worlds (or just spawn world)
            for (World world : Bukkit.getWorlds()) {
                // Only set time if it's significantly different
                long currentTime = world.getTime();

                // Normalize times to 0-24000 range for comparison
                long normalizedCurrent = currentTime % 24000;
                long normalizedTarget = targetTime % 24000;

                // Only update if time is different (with small tolerance)
                if (Math.abs(normalizedCurrent - normalizedTarget) > 100) {
                    world.setTime(targetTime);
                    plugin.getLogger().fine("Reset time in world " + world.getName() +
                            " from " + normalizedCurrent + " to " + normalizedTarget);
                }
            }
        }, 0L, 100L); // 0 delay, run every 100 ticks (5 seconds)

        plugin.getLogger().info("Time manager started - keeping time at " + targetTime +
                " (updating every 5 seconds)");
    }

    /**
     * Shuts down the time manager.
     */
    public void shutdown() {
        if (timeTask != null) {
            timeTask.cancel();
            timeTask = null;
            plugin.getLogger().info("Time manager stopped");
        }
    }
}