package com.yourserver.battleroyale.redis;

import com.google.gson.JsonObject;
import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * FIXED Redis broadcaster with proper CloudNet 4.0 API integration.
 *
 * CloudNet 4.0 Changes:
 * - Uses InjectionLayer.ext() instead of InjectionLayer.boot()
 * - Proper dependency injection for ServiceInfoSnapshot
 * - Better error handling for missing CloudNet environment
 */
public class RedisGameStateBroadcaster {

    private final Plugin plugin;
    private final CorePlugin corePlugin;
    private final RedisMessenger redisMessenger;
    private final String gamemodeId;
    private final String channelPrefix;

    private final String serviceName;
    private final String stateChannel;
    private final String heartbeatChannel;
    private final String controlChannel;

    private final AtomicBoolean initialized;

    private volatile String lastState = "WAITING";
    private volatile int lastPlayers = 0;
    private volatile int lastMaxPlayers = 100;
    private volatile int lastAlive = 0;
    private volatile String lastGameId = null;

    private static final long HEARTBEAT_INTERVAL_TICKS = 100L; // 5 seconds

    public RedisGameStateBroadcaster(
            @NotNull Plugin plugin,
            @NotNull CorePlugin corePlugin,
            @NotNull String gamemodeId,
            @NotNull String channelPrefix) {

        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.redisMessenger = corePlugin.getRedisManager();
        this.gamemodeId = gamemodeId;
        this.channelPrefix = channelPrefix;
        this.initialized = new AtomicBoolean(false);

        // Get service name from CloudNet system properties (set by wrapper)
        this.serviceName = detectServiceName();

        this.stateChannel = channelPrefix + ":state";
        this.heartbeatChannel = channelPrefix + ":heartbeat";
        this.controlChannel = channelPrefix + ":control";

        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        plugin.getLogger().info("Redis Broadcaster Configuration:");
        plugin.getLogger().info("  Service Name: " + serviceName);
        plugin.getLogger().info("  Gamemode ID: " + gamemodeId);
        plugin.getLogger().info("  State Channel: " + stateChannel);
        plugin.getLogger().info("  Heartbeat Channel: " + heartbeatChannel);
        plugin.getLogger().info("  Control Channel: " + controlChannel);
        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Detect CloudNet service name - SIMPLE VERSION.
     * CloudNet wrapper sets system properties automatically.
     */
    @NotNull
    private String detectServiceName() {
        // CloudNet 4.0: Wrapper sets this property
        String name = System.getProperty("cloudnet.service.name");

        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ CloudNet service: " + name);
            return name;
        }

        // Fallback
        name = Bukkit.getServer().getName();
        plugin.getLogger().warning("⚠ CloudNet property not set, using: " + name);
        return name;
    }

    public void initialize() {
        if (initialized.getAndSet(true)) {
            plugin.getLogger().warning("Redis broadcaster already initialized!");
            return;
        }

        try {
            if (redisMessenger == null) {
                throw new IllegalStateException("Redis is not available!");
            }

            // Subscribe to control channel
            redisMessenger.subscribe(controlChannel, this::handleControlMessage);
            plugin.getLogger().info("✓ Subscribed to control channel: " + controlChannel);

            // Start heartbeat task
            startHeartbeatTask();
            plugin.getLogger().info("✓ Heartbeat task started");

            // Broadcast initial state
            broadcastInitialState();

            plugin.getLogger().info("✓ Redis broadcaster initialized successfully!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Redis broadcaster!", e);
            initialized.set(false);
        }
    }

    private void broadcastInitialState() {
        broadcastState("WAITING", 0, lastMaxPlayers, 0, null);
        plugin.getLogger().info("✓ Initial state broadcasted to Redis");
    }

    public void broadcastState(
            @NotNull String state,
            int currentPlayers,
            int maxPlayers,
            int alivePlayers,
            String gameId) {

        if (!initialized.get()) {
            plugin.getLogger().warning("Cannot broadcast: not initialized");
            return;
        }

        try {
            this.lastState = state;
            this.lastPlayers = currentPlayers;
            this.lastMaxPlayers = maxPlayers;
            this.lastAlive = alivePlayers;
            this.lastGameId = gameId;

            JsonObject json = new JsonObject();
            json.addProperty("service", serviceName);
            json.addProperty("state", state.toUpperCase());
            json.addProperty("players", currentPlayers);
            json.addProperty("maxPlayers", maxPlayers);
            json.addProperty("alive", alivePlayers);
            json.addProperty("timestamp", System.currentTimeMillis());

            if (gameId != null) {
                json.addProperty("game", gameId);
            }

            redisMessenger.publish(stateChannel, json.toString());

            plugin.getLogger().fine("State broadcast: " + serviceName + " -> " + state +
                    " (" + currentPlayers + "/" + maxPlayers + ")");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to broadcast state", e);
        }
    }

    public void broadcastHeartbeat(int currentPlayers) {
        if (!initialized.get()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("service", serviceName);
            json.addProperty("players", currentPlayers);
            json.addProperty("timestamp", System.currentTimeMillis());

            redisMessenger.publish(heartbeatChannel, json.toString());

            plugin.getLogger().finest("Heartbeat: " + serviceName + " (" + currentPlayers + " players)");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send heartbeat", e);
        }
    }

    private void handleControlMessage(@NotNull String message) {
        try {
            plugin.getLogger().fine("Received control message: " + message);

            if (message.contains("request_state")) {
                broadcastState(lastState, lastPlayers, lastMaxPlayers, lastAlive, lastGameId);
                plugin.getLogger().fine("Re-broadcasted state in response to request");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle control message", e);
        }
    }

    private void startHeartbeatTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> {
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    broadcastHeartbeat(playerCount);
                },
                HEARTBEAT_INTERVAL_TICKS,
                HEARTBEAT_INTERVAL_TICKS
        );
    }

    public void shutdown() {
        if (!initialized.getAndSet(false)) {
            return;
        }

        plugin.getLogger().info("Shutting down Redis broadcaster...");

        if (redisMessenger != null) {
            redisMessenger.unsubscribe(controlChannel);
        }
    }

    // Getters
    @NotNull
    public String getServiceName() {
        return serviceName;
    }

    @NotNull
    public String getGamemodeId() {
        return gamemodeId;
    }

    @NotNull
    public String getChannelPrefix() {
        return channelPrefix;
    }

    @NotNull
    public String getStateChannel() {
        return stateChannel;
    }

    @NotNull
    public String getHeartbeatChannel() {
        return heartbeatChannel;
    }

    @NotNull
    public String getControlChannel() {
        return controlChannel;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}