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
 * Generic Redis broadcaster for game state updates.
 *
 * Works for ANY gamemode:
 * - BattleRoyale
 * - SkyWars
 * - BedWars
 * - Duels
 * - etc.
 *
 * Features:
 * - Broadcasts game state to Redis
 * - Sends periodic heartbeats
 * - Listens for control messages
 * - Auto-detects CloudNet service name
 *
 * Usage:
 * ```java
 * RedisGameStateBroadcaster broadcaster = new RedisGameStateBroadcaster(
 *     plugin,
 *     corePlugin,
 *     "battleroyale",  // gamemode ID
 *     "br"              // channel prefix
 * );
 *
 * broadcaster.initialize();
 * broadcaster.broadcastState("WAITING", 10, 100, 10);
 * ```
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

    // Heartbeat interval (5 seconds)
    private static final long HEARTBEAT_INTERVAL_TICKS = 100L;

    /**
     * Creates a new Redis broadcaster.
     *
     * @param plugin Your game plugin
     * @param corePlugin CorePlugin instance
     * @param gamemodeId Gamemode ID (e.g., "battleroyale", "skywars")
     * @param channelPrefix Channel prefix (e.g., "br", "sw", "bw")
     */
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

        // Detect CloudNet service name
        this.serviceName = detectServiceName();

        // Build channel names
        this.stateChannel = channelPrefix + ":state";
        this.heartbeatChannel = channelPrefix + ":heartbeat";
        this.controlChannel = channelPrefix + ":control";

        plugin.getLogger().info("Redis Broadcaster initialized:");
        plugin.getLogger().info("  Service: " + serviceName);
        plugin.getLogger().info("  Gamemode: " + gamemodeId);
        plugin.getLogger().info("  State Channel: " + stateChannel);
        plugin.getLogger().info("  Heartbeat Channel: " + heartbeatChannel);
        plugin.getLogger().info("  Control Channel: " + controlChannel);
    }

    /**
     * Initializes the broadcaster.
     * Starts heartbeat task and subscribes to control channel.
     */
    public void initialize() {
        if (initialized.getAndSet(true)) {
            plugin.getLogger().warning("Redis broadcaster already initialized!");
            return;
        }

        try {
            // Verify Redis connection
            if (redisMessenger == null) {
                throw new IllegalStateException("Redis is not available!");
            }

            // Subscribe to control channel
            redisMessenger.subscribe(controlChannel, this::handleControlMessage);
            plugin.getLogger().info("✓ Subscribed to control channel: " + controlChannel);

            // Start heartbeat task
            startHeartbeatTask();
            plugin.getLogger().info("✓ Heartbeat task started (every 5 seconds)");

            plugin.getLogger().info("Redis broadcaster initialized successfully!");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Redis broadcaster!", e);
            initialized.set(false);
        }
    }

    /**
     * Broadcasts game state to Redis.
     *
     * @param state Game state (e.g., "WAITING", "STARTING", "ACTIVE")
     * @param currentPlayers Current player count
     * @param maxPlayers Maximum player capacity
     * @param alivePlayers Players still alive (0 if not applicable)
     */
    public void broadcastState(
            @NotNull String state,
            int currentPlayers,
            int maxPlayers,
            int alivePlayers) {

        broadcastState(state, currentPlayers, maxPlayers, alivePlayers, null);
    }

    /**
     * Broadcasts game state to Redis with game ID.
     *
     * @param state Game state
     * @param currentPlayers Current player count
     * @param maxPlayers Maximum player capacity
     * @param alivePlayers Players still alive
     * @param gameId Optional game instance ID
     */
    public void broadcastState(
            @NotNull String state,
            int currentPlayers,
            int maxPlayers,
            int alivePlayers,
            String gameId) {

        if (!initialized.get()) {
            plugin.getLogger().warning("Cannot broadcast state: broadcaster not initialized");
            return;
        }

        try {
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

            plugin.getLogger().fine("Broadcasted state: " + state + " (" + currentPlayers + "/" + maxPlayers + ")");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to broadcast state", e);
        }
    }

    /**
     * Broadcasts a heartbeat to Redis.
     *
     * @param currentPlayers Current player count
     */
    public void broadcastHeartbeat(int currentPlayers) {
        if (!initialized.get()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("service", serviceName);
            json.addProperty("players", currentPlayers);
            json.addProperty("timestamp", System.currentTimeMillis());

            redisMessenger.publish(heartbeatChannel, json.toString());

            plugin.getLogger().finest("Heartbeat sent (" + currentPlayers + " players)");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send heartbeat", e);
        }
    }

    /**
     * Handles control messages from Redis.
     */
    private void handleControlMessage(@NotNull String message) {
        try {
            // Parse control message (currently only supports "request_state")
            plugin.getLogger().fine("Received control message: " + message);

            // Implement your game-specific logic here to respond to control messages
            // For example: send current state when requested

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
                    // Broadcast heartbeat with current player count
                    int playerCount = Bukkit.getOnlinePlayers().size();
                    broadcastHeartbeat(playerCount);
                },
                HEARTBEAT_INTERVAL_TICKS,
                HEARTBEAT_INTERVAL_TICKS
        );
    }

    /**
     * Detects the CloudNet service name.
     *
     * @return Service name (e.g., "BattleRoyale-1")
     */
    @NotNull
    private String detectServiceName() {
        // Try to get from CloudNet system property
        String name = System.getProperty("cloudnet.service.name");

        if (name != null && !name.isEmpty()) {
            return name;
        }

        // Fallback to localhost (for testing)
        plugin.getLogger().warning("CloudNet service name not detected, using fallback: localhost");
        return "localhost-" + gamemodeId;
    }

    /**
     * Shuts down the broadcaster.
     */
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