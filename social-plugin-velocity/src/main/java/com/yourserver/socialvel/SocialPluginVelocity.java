package com.yourserver.socialvel;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.yourserver.socialvel.config.VelocityConfig;
import com.yourserver.socialvel.listener.PlayerConnectionListener;
import com.yourserver.socialvel.messaging.RedisManager;
import com.yourserver.socialvel.messaging.SocialMessageHandler;
import com.yourserver.socialvel.storage.JSONPlayerStatusStorage;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "social-plugin-velocity",
        name = "SocialPluginVelocity",
        version = "1.0.0",
        authors = {"MCBZH"},
        description = "Velocity-side social plugin for cross-server features"
)
public class SocialPluginVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfig config;
    private RedisManager redisManager;
    private JSONPlayerStatusStorage statusStorage;
    private SocialMessageHandler messageHandler;

    @Inject
    public SocialPluginVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Enabling SocialPluginVelocity...");

        try {
            // Load configuration
            config = VelocityConfig.load(dataDirectory);
            logger.info("✓ Configuration loaded");

            // Initialize JSON storage
            statusStorage = new JSONPlayerStatusStorage(dataDirectory.toFile(), logger);
            logger.info("✓ JSON storage initialized");

            // Initialize Redis
            redisManager = new RedisManager(this, config.getRedisConfig());
            logger.info("✓ Redis initialized");

            // Initialize message handler
            messageHandler = new SocialMessageHandler(this, redisManager, statusStorage);
            logger.info("✓ Message handler initialized");

            // Register listeners
            proxy.getEventManager().register(this,
                    new PlayerConnectionListener(this, redisManager, statusStorage));
            logger.info("✓ Listeners registered");

            logger.info("SocialPluginVelocity enabled successfully!");

        } catch (Exception e) {
            logger.error("Failed to enable SocialPluginVelocity!", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Disabling SocialPluginVelocity...");

        if (messageHandler != null) {
            messageHandler.shutdown();
            logger.info("✓ Message handler shut down");
        }

        if (redisManager != null) {
            redisManager.shutdown();
            logger.info("✓ Redis shut down");
        }

        if (statusStorage != null) {
            statusStorage.save().join();
            logger.info("✓ Storage saved");
        }

        logger.info("SocialPluginVelocity disabled successfully!");
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public VelocityConfig getConfig() {
        return config;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public JSONPlayerStatusStorage getStatusStorage() {
        return statusStorage;
    }
}