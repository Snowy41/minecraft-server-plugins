package com.yourserver.gamelobby.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.core.CorePlugin;
import com.yourserver.gamelobby.GameLobbyPlugin;
import com.yourserver.gamelobby.model.GameService;
import com.yourserver.gamelobby.model.GameService.GameState;
import com.yourserver.gamelobby.model.GamemodeConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all game services across all gamemodes.
 *
 * Features:
 * - Tracks services for ALL gamemodes (BattleRoyale, SkyWars, etc.)
 * - Subscribes to Redis channels for real-time updates
 * - Detects and removes stale services
 * - Handles player connections via Velocity
 * - Provides gamemode configuration
 */
public class GameServiceManager {

    private final GameLobbyPlugin plugin;
    private final CorePlugin corePlugin;
    private final RedisMessenger redisMessenger;

    // All game services (key: serviceName, value: GameService)
    private final Map<String, GameService> services;

    // Gamemode configurations (key: gamemodeId, value: GamemodeConfig)
    private final Map<String, GamemodeConfig> gamemodes;

    // Stale service detection
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    public GameServiceManager(@NotNull GameLobbyPlugin plugin, @NotNull CorePlugin corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.redisMessenger = corePlugin.getRedisManager();
        this.services = new ConcurrentHashMap<>();
        this.gamemodes = new LinkedHashMap<>();
    }

    /**
     * Initializes the service manager.
     * Loads gamemode configs and subscribes to Redis channels.
     */
    public void initialize() {
        plugin.getLogger().info("Initializing GameServiceManager...");

        // 1. Load gamemode configurations from config.yml
        loadGamemodeConfigs();

        // 2. Subscribe to Redis channels for each gamemode
        subscribeToRedisChannels();

        // 3. Start stale service detection
        startStaleServiceDetection();

        // 4. Request initial state from all services
        requestInitialStates();

        plugin.getLogger().info("GameServiceManager initialized successfully!");
    }

    /**
     * Loads gamemode configurations from config.yml.
     */
    private void loadGamemodeConfigs() {
        plugin.getLogger().info("Loading gamemode configurations...");

        var config = plugin.getConfig();
        var gamemodesSection = config.getConfigurationSection("gamemodes");

        if (gamemodesSection == null) {
            plugin.getLogger().warning("No 'gamemodes' section found in config.yml!");
            return;
        }

        for (String gamemodeId : gamemodesSection.getKeys(false)) {
            String path = "gamemodes." + gamemodeId;

            boolean enabled = config.getBoolean(path + ".enabled", false);
            if (!enabled) {
                plugin.getLogger().info("  Skipping disabled gamemode: " + gamemodeId);
                continue;
            }

            String displayName = config.getString(path + ".display-name", gamemodeId);
            String servicePrefix = config.getString(path + ".service-prefix", gamemodeId + "-");
            String iconMaterialName = config.getString(path + ".icon-material", "DIAMOND_SWORD");
            List<String> description = config.getStringList(path + ".description");

            Material iconMaterial;
            try {
                iconMaterial = Material.valueOf(iconMaterialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + iconMaterialName + "' for " + gamemodeId + ", using DIAMOND_SWORD");
                iconMaterial = Material.DIAMOND_SWORD;
            }

            GamemodeConfig gamemodeConfig = new GamemodeConfig.Builder()
                    .id(gamemodeId)
                    .displayName(displayName)
                    .servicePrefix(servicePrefix)
                    .iconMaterial(iconMaterial)
                    .description(description)
                    .enabled(true)
                    .build();

            gamemodes.put(gamemodeId, gamemodeConfig);
            plugin.getLogger().info("  ✓ Loaded: " + gamemodeId + " (" + displayName + ")");
        }

        plugin.getLogger().info("Loaded " + gamemodes.size() + " gamemode(s)");
    }

    /**
     * Subscribes to Redis channels for all enabled gamemodes.
     * Uses CorePlugin's RedisManager (RedisMessenger interface) for pub/sub.
     */
    private void subscribeToRedisChannels() {
        plugin.getLogger().info("Subscribing to Redis channels...");

        for (GamemodeConfig gamemode : gamemodes.values()) {
            // Subscribe to state channel (e.g., "br:state", "sw:state")
            String stateChannel = gamemode.getStateChannel();
            redisMessenger.subscribe(
                    stateChannel,
                    message -> handleStateUpdate(gamemode, message)
            );
            plugin.getLogger().info("  ✓ Subscribed: " + stateChannel);

            // Subscribe to heartbeat channel (e.g., "br:heartbeat", "sw:heartbeat")
            String heartbeatChannel = gamemode.getHeartbeatChannel();
            redisMessenger.subscribe(
                    heartbeatChannel,
                    message -> handleHeartbeat(gamemode, message)
            );
            plugin.getLogger().info("  ✓ Subscribed: " + heartbeatChannel);
        }

        plugin.getLogger().info("Subscribed to " + (gamemodes.size() * 2) + " Redis channels");
    }

    /**
     * Handles state updates from Redis.
     */
    private void handleStateUpdate(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            String serviceName = json.get("service").getAsString();
            String stateStr = json.get("state").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;
            int maxPlayers = json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : 0;
            int alive = json.has("alive") ? json.get("alive").getAsInt() : 0;

            GameState state = GameState.fromString(stateStr);

            // Get or create service
            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> new GameService(name, gamemode.getId())
            );

            // Update service data
            service.updateState(state);
            service.updatePlayers(players, maxPlayers);
            service.updateAlive(alive);

            if (json.has("game")) {
                service.setGameId(json.get("game").getAsString());
            }

            plugin.getLogger().fine("State update: " + serviceName + " -> " + state + " (" + players + "/" + maxPlayers + ")");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse state update: " + e.getMessage());
        }
    }

    /**
     * Handles heartbeat updates from Redis.
     */
    private void handleHeartbeat(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            String serviceName = json.get("service").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;

            // Get or create service
            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> new GameService(name, gamemode.getId())
            );

            // Update heartbeat
            service.updateHeartbeat();
            service.setCurrentPlayers(players);

            plugin.getLogger().finest("Heartbeat: " + serviceName + " (" + players + " players)");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse heartbeat: " + e.getMessage());
        }
    }

    /**
     * Starts periodic stale service detection.
     */
    private void startStaleServiceDetection() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int staleCount = 0;

            for (GameService service : services.values()) {
                if (service.isStale(HEARTBEAT_TIMEOUT_SECONDS)) {
                    if (service.isOnline()) {
                        service.markOffline();
                        staleCount++;
                        plugin.getLogger().warning("Service marked as offline (no heartbeat): " + service.getServiceName());
                    }
                }
            }

            if (staleCount > 0) {
                plugin.getLogger().info("Detected " + staleCount + " stale service(s)");
            }

        }, 200L, 200L); // Every 10 seconds
    }

    /**
     * Requests initial state from all game services.
     */
    private void requestInitialStates() {
        plugin.getLogger().info("Requesting initial states from game services...");

        for (GamemodeConfig gamemode : gamemodes.values()) {
            JsonObject request = new JsonObject();
            request.addProperty("action", "request_state");
            request.addProperty("timestamp", System.currentTimeMillis());

            redisMessenger.publish(
                    gamemode.getControlChannel(),
                    request.toString()
            );
        }
    }

    /**
     * Connects a player to a game service via Velocity.
     */
    public void connectPlayer(@NotNull Player player, @NotNull String serviceName) {
        GameService service = services.get(serviceName);

        if (service == null) {
            player.sendMessage("§cThat server is not available!");
            return;
        }

        if (!service.isOnline()) {
            player.sendMessage("§cThat server is offline!");
            return;
        }

        if (!service.isJoinable()) {
            player.sendMessage("§cYou cannot join that server right now!");
            return;
        }

        // Send via Velocity plugin messaging
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(serviceName);

            player.sendPluginMessage(plugin, "bungeecord:main", b.toByteArray());
            plugin.getLogger().info("Connecting " + player.getName() + " to " + serviceName);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to connect player: " + e.getMessage());
            player.sendMessage("§cFailed to connect to server!");
        }
    }

    // ===== PUBLIC API =====

    /**
     * Gets all services for a specific gamemode.
     */
    @NotNull
    public List<GameService> getServices(@NotNull String gamemodeId) {
        return services.values().stream()
                .filter(service -> service.getGamemodeId().equals(gamemodeId))
                .sorted(Comparator.comparing(GameService::getServiceName))
                .collect(Collectors.toList());
    }

    /**
     * Gets a specific service by name.
     */
    @Nullable
    public GameService getService(@NotNull String serviceName) {
        return services.get(serviceName);
    }

    /**
     * Gets gamemode configuration.
     */
    @Nullable
    public GamemodeConfig getGamemodeConfig(@NotNull String gamemodeId) {
        return gamemodes.get(gamemodeId);
    }

    /**
     * Gets all enabled gamemode IDs.
     */
    @NotNull
    public Set<String> getEnabledGamemodes() {
        return new LinkedHashSet<>(gamemodes.keySet());
    }

    /**
     * Gets all gamemode configurations.
     */
    @NotNull
    public Collection<GamemodeConfig> getAllGamemodes() {
        return new ArrayList<>(gamemodes.values());
    }

    /**
     * Checks if a gamemode is enabled.
     */
    public boolean isGamemodeEnabled(@NotNull String gamemodeId) {
        return gamemodes.containsKey(gamemodeId);
    }

    /**
     * Shuts down the service manager.
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down GameServiceManager...");
        services.clear();
        gamemodes.clear();
    }
}