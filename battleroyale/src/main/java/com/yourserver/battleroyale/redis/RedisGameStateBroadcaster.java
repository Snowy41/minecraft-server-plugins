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
 * FIXED Redis broadcaster with proper CloudNet service detection.
 *
 * FIXES:
 * 1. Proper CloudNet service name detection
 * 2. Immediate initial state broadcast
 * 3. Better error handling
 * 4. Control message handling for state requests
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

    // Last known state (for responding to requests)
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

        // FIXED: Proper CloudNet service detection
        this.serviceName = detectServiceName();

        // Build channel names
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
     * FIXED: Proper CloudNet service name detection.
     */
    @NotNull
    private String detectServiceName() {
        // Try CloudNet 4.0 system property first
        String name = System.getProperty("cloudnet.service.name");

        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ Detected CloudNet service: " + name);
            return name;
        }

        // Try alternative CloudNet properties
        name = System.getProperty("cloudnet.wrapper.service.name");
        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ Detected CloudNet service (alt): " + name);
            return name;
        }

        // Try environment variable
        name = System.getenv("CLOUDNET_SERVICE_NAME");
        if (name != null && !name.isEmpty()) {
            plugin.getLogger().info("✓ Detected CloudNet service (env): " + name);
            return name;
        }

        // Fallback to server name
        String serverName = Bukkit.getServer().getName();
        if (serverName != null && !serverName.isEmpty() && !serverName.equals("Unknown Server")) {
            plugin.getLogger().warning("Using server name as fallback: " + serverName);
            return serverName;
        }

        // Last resort - use gamemode-1
        plugin.getLogger().warning("⚠ CloudNet service name not detected! Using fallback.");
        plugin.getLogger().warning("⚠ Make sure CloudNet is properly configured.");
        return gamemodeId + "-1";
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

            // Subscribe to control channel for state requests
            redisMessenger.subscribe(controlChannel, this::handleControlMessage);
            plugin.getLogger().info("✓ Subscribed to control channel: " + controlChannel);

            // Start heartbeat task
            startHeartbeatTask();
            plugin.getLogger().info("✓ Heartbeat task started");

            // FIXED: Broadcast initial state immediately
            broadcastInitialState();

            plugin.getLogger().info("✓ Redis broadcaster initialized successfully!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Redis broadcaster!", e);
            initialized.set(false);
        }
    }

    /**
     * FIXED: Broadcast initial state immediately after initialization.
     */
    private void broadcastInitialState() {
        // Broadcast initial WAITING state
        broadcastState("WAITING", 0, lastMaxPlayers, 0, null);
        plugin.getLogger().info("✓ Initial state broadcasted to Redis");
    }

    /**
     * Broadcasts game state to Redis.
     */
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
            // Store last known state
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

    /**
     * Broadcasts a heartbeat to Redis.
     */
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

    /**
     * FIXED: Handle control messages (state requests from lobby).
     */
    private void handleControlMessage(@NotNull String message) {
        try {
            plugin.getLogger().fine("Received control message: " + message);

            // Check if it's a state request
            if (message.contains("request_state")) {
                // Re-broadcast current state
                broadcastState(lastState, lastPlayers, lastMaxPlayers, lastAlive, lastGameId);
                plugin.getLogger().fine("Re-broadcasted state in response to request");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle control message", e);
        }
    }

    /**
     * Starts the heartbeat task.
     */
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

    // ===== GETTERS =====

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