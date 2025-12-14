package com.yourserver.api;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * API Module Plugin - provides shared interfaces and models.
 * This plugin must load FIRST so other plugins can depend on it.
 */
public class APIModulePlugin extends JavaPlugin {

    @Override
    public void onLoad() {
        getLogger().info("Loading APIModule...");
    }

    @Override
    public void onEnable() {
        getLogger().info("APIModule enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("APIModule disabled.");
    }
}