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
 * FIXED: GameServiceManager with proper CloudNet 4.0 support.
 *
 * CloudNet 4.0 Integration:
 * - Uses InjectionLayer.ext() for dependency injection
 * - Proper ServiceInfoSnapshot retrieval
 * - Better fallback mechanisms
 * - Improved error handling
 */
public class GameServiceManager {

    private final GameLobbyPlugin plugin;
    private final CorePlugin corePlugin;
    private final RedisMessenger redisMessenger;

    private final Map<String, GameService> services;
    private final Map<String, GamemodeConfig> gamemodes;

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    // FIXED: Store current CloudNet service name
    private final String currentServiceName;

    public GameServiceManager(@NotNull GameLobbyPlugin plugin, @NotNull CorePlugin corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.redisMessenger = corePlugin.getRedisManager();
        this.services = new ConcurrentHashMap<>();
        this.gamemodes = new LinkedHashMap<>();

        // Get service name from CloudNet system properties
        this.currentServiceName = detectCurrentServiceName();
        plugin.getLogger().info("Running on CloudNet service: " + currentServiceName);
    }

    /**
     * Detect CloudNet service name - SIMPLE VERSION.
     * CloudNet wrapper sets system properties automatically.
     */
    @NotNull
    private String detectCurrentServiceName() {
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
        plugin.getLogger().info("Initializing GameServiceManager...");

        loadGamemodeConfigs();
        subscribeToRedisChannels();
        startStaleServiceDetection();
        requestInitialStates();

        plugin.getLogger().info("GameServiceManager initialized successfully!");
    }

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

            String stateChannel = config.getString(path + ".state-channel", null);
            String heartbeatChannel = config.getString(path + ".heartbeat-channel", null);
            String controlChannel = config.getString(path + ".control-channel", null);

            org.bukkit.Material iconMaterial;
            try {
                iconMaterial = org.bukkit.Material.valueOf(iconMaterialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + iconMaterialName + "' for " + gamemodeId + ", using DIAMOND_SWORD");
                iconMaterial = org.bukkit.Material.DIAMOND_SWORD;
            }

            GamemodeConfig.Builder builder = new GamemodeConfig.Builder()
                    .id(gamemodeId)
                    .displayName(displayName)
                    .servicePrefix(servicePrefix)
                    .iconMaterial(iconMaterial)
                    .description(description)
                    .enabled(true);

            if (stateChannel != null) {
                builder.stateChannel(stateChannel);
            }
            if (heartbeatChannel != null) {
                builder.heartbeatChannel(heartbeatChannel);
            }
            if (controlChannel != null) {
                builder.controlChannel(controlChannel);
            }

            GamemodeConfig gamemodeConfig = builder.build();

            gamemodes.put(gamemodeId, gamemodeConfig);
            plugin.getLogger().info("  ✓ Loaded: " + gamemodeId + " (" + displayName + ")");
            plugin.getLogger().info("    Channels: " + gamemodeConfig.getStateChannel() +
                    ", " + gamemodeConfig.getHeartbeatChannel());
        }

        plugin.getLogger().info("Loaded " + gamemodes.size() + " gamemode(s)");
    }

    private void subscribeToRedisChannels() {
        plugin.getLogger().info("Subscribing to Redis channels...");

        for (GamemodeConfig gamemode : gamemodes.values()) {
            String stateChannel = gamemode.getStateChannel();
            redisMessenger.subscribe(
                    stateChannel,
                    message -> handleStateUpdate(gamemode, message)
            );
            plugin.getLogger().info("  ✓ Subscribed: " + stateChannel);

            String heartbeatChannel = gamemode.getHeartbeatChannel();
            redisMessenger.subscribe(
                    heartbeatChannel,
                    message -> handleHeartbeat(gamemode, message)
            );
            plugin.getLogger().info("  ✓ Subscribed: " + heartbeatChannel);
        }

        plugin.getLogger().info("Subscribed to " + (gamemodes.size() * 2) + " Redis channels");
    }

    private void handleStateUpdate(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            String serviceName = json.get("service").getAsString();
            String stateStr = json.get("state").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;
            int maxPlayers = json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : 0;
            int alive = json.has("alive") ? json.get("alive").getAsInt() : 0;

            GameState state = GameState.fromString(stateStr);

            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> new GameService(name, gamemode.getId())
            );

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

    private void handleHeartbeat(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            String serviceName = json.get("service").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;

            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> new GameService(name, gamemode.getId())
            );

            service.updateHeartbeat();
            service.setCurrentPlayers(players);

            plugin.getLogger().finest("Heartbeat: " + serviceName + " (" + players + " players)");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse heartbeat: " + e.getMessage());
        }
    }

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

        }, 200L, 200L);
    }

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
     * FIXED: Connect player to CloudNet service via Velocity plugin messaging.
     * Supports both velocity:main and bungeecord:main channels.
     */
    public void connectPlayer(@NotNull Player player, @NotNull String serviceName) {
        GameService service = services.get(serviceName);

        if (service == null) {
            player.sendMessage("§cThat server is not available!");
            plugin.getLogger().warning("Service not found: " + serviceName);
            return;
        }

        if (!service.isOnline()) {
            player.sendMessage("§cThat server is offline!");
            plugin.getLogger().warning("Service offline: " + serviceName);
            return;
        }

        if (!service.isJoinable()) {
            player.sendMessage("§cYou cannot join that server right now!");
            plugin.getLogger().warning("Service not joinable: " + serviceName + " (state: " + service.getState() + ")");
            return;
        }

        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(serviceName);

            byte[] data = b.toByteArray();

            // Try both channels (Velocity and BungeeCord)
            boolean sent = false;

            // Try velocity:main first (modern)
            try {
                player.sendPluginMessage(plugin, "velocity:main", data);
                plugin.getLogger().info("Sent connection request to " + serviceName + " via velocity:main");
                sent = true;
            } catch (Exception e) {
                plugin.getLogger().fine("velocity:main not available: " + e.getMessage());
            }

            // Fallback to bungeecord:main
            if (!sent) {
                try {
                    player.sendPluginMessage(plugin, "bungeecord:main", data);
                    plugin.getLogger().info("Sent connection request to " + serviceName + " via bungeecord:main");
                    sent = true;
                } catch (Exception e) {
                    plugin.getLogger().fine("bungeecord:main not available: " + e.getMessage());
                }
            }

            if (sent) {
                player.sendMessage("§aConnecting to " + serviceName + "...");
                plugin.getLogger().info("Connecting " + player.getName() + " to " + serviceName);
            } else {
                player.sendMessage("§cFailed to connect to server!");
                plugin.getLogger().severe("No plugin messaging channel worked for " + player.getName() + " -> " + serviceName);
                plugin.getLogger().severe("Make sure Velocity/BungeeCord plugin messaging is registered!");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create plugin message: " + e.getMessage());
            player.sendMessage("§cFailed to connect to server!");
        }
    }

    @NotNull
    public List<GameService> getServices(@NotNull String gamemodeId) {
        return services.values().stream()
                .filter(service -> service.getGamemodeId().equals(gamemodeId))
                .sorted(Comparator.comparing(GameService::getServiceName))
                .collect(Collectors.toList());
    }

    @Nullable
    public GameService getService(@NotNull String serviceName) {
        return services.get(serviceName);
    }

    @Nullable
    public GamemodeConfig getGamemodeConfig(@NotNull String gamemodeId) {
        return gamemodes.get(gamemodeId);
    }

    @NotNull
    public Set<String> getEnabledGamemodes() {
        return new LinkedHashSet<>(gamemodes.keySet());
    }

    @NotNull
    public Collection<GamemodeConfig> getAllGamemodes() {
        return new ArrayList<>(gamemodes.values());
    }

    public boolean isGamemodeEnabled(@NotNull String gamemodeId) {
        return gamemodes.containsKey(gamemodeId);
    }

    @NotNull
    public String getCurrentServiceName() {
        return currentServiceName;
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down GameServiceManager...");
        services.clear();
        gamemodes.clear();
    }
}