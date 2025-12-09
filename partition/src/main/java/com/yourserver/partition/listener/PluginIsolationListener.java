package com.yourserver.partition.listener;

import com.yourserver.partition.manager.PluginIsolationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Listens for plugin enable/disable events.
 */
public class PluginIsolationListener implements Listener {

    private final PluginIsolationManager isolationManager;

    public PluginIsolationListener(PluginIsolationManager isolationManager) {
        this.isolationManager = isolationManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        // Log plugin enable
        // Could be used to automatically add plugins to partitions
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        // Log plugin disable
    }
}