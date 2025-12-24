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
 * FIXED: GameServiceManager with working CloudNet 4.0 service name extraction.
 *
 * The issue was that service names from Redis contain the actual name,
 * but we need to extract it properly from the JSON messages.
 */
public class GameServiceManager {

    private final GameLobbyPlugin plugin;
    private final CorePlugin corePlugin;
    private final RedisMessenger redisMessenger;

    private final Map<String, GameService> services;
    private final Map<String, GamemodeConfig> gamemodes;

    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    public GameServiceManager(@NotNull GameLobbyPlugin plugin, @NotNull CorePlugin corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.redisMessenger = corePlugin.getRedisManager();
        this.services = new ConcurrentHashMap<>();
        this.gamemodes = new LinkedHashMap<>();
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

            String stateChannel = config.getString(path + ".state-channel", gamemodeId + ":state");
            String heartbeatChannel = config.getString(path + ".heartbeat-channel", gamemodeId + ":heartbeat");
            String controlChannel = config.getString(path + ".control-channel", gamemodeId + ":control");

            org.bukkit.Material iconMaterial;
            try {
                iconMaterial = org.bukkit.Material.valueOf(iconMaterialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material '" + iconMaterialName + "' for " + gamemodeId + ", using DIAMOND_SWORD");
                iconMaterial = org.bukkit.Material.DIAMOND_SWORD;
            }

            GamemodeConfig gamemodeConfig = new GamemodeConfig.Builder()
                    .id(gamemodeId)
                    .displayName(displayName)
                    .servicePrefix(servicePrefix)
                    .iconMaterial(iconMaterial)
                    .description(description)
                    .enabled(true)
                    .stateChannel(stateChannel)
                    .heartbeatChannel(heartbeatChannel)
                    .controlChannel(controlChannel)
                    .build();

            gamemodes.put(gamemodeId, gamemodeConfig);
            plugin.getLogger().info("  ✓ Loaded: " + gamemodeId + " (" + displayName + ")");
            plugin.getLogger().info("    State Channel: " + stateChannel);
            plugin.getLogger().info("    Heartbeat Channel: " + heartbeatChannel);
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

    /**
     * FIXED: Handles state update from Redis.
     * Properly extracts service name from JSON.
     */
    private void handleStateUpdate(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            plugin.getLogger().fine("State update received: " + message);

            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            // ✅ FIXED: Extract service name from JSON
            if (!json.has("service")) {
                plugin.getLogger().warning("State update missing 'service' field: " + message);
                return;
            }

            String serviceName = json.get("service").getAsString();

            // Validate service name
            if (serviceName == null || serviceName.isEmpty() || serviceName.contains("${")) {
                plugin.getLogger().warning("Invalid service name in state update: " + serviceName);
                plugin.getLogger().warning("Full message: " + message);
                return;
            }

            String stateStr = json.get("state").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;
            int maxPlayers = json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : 0;
            int alive = json.has("alive") ? json.get("alive").getAsInt() : 0;

            GameState state = GameState.fromString(stateStr);

            // Get or create service
            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> {
                        plugin.getLogger().info("New service detected: " + name + " (gamemode: " + gamemode.getId() + ")");
                        return new GameService(name, gamemode.getId());
                    }
            );

            // Update service data
            service.updateState(state);
            service.updatePlayers(players, maxPlayers);
            service.updateAlive(alive);

            if (json.has("game")) {
                service.setGameId(json.get("game").getAsString());
            }

            plugin.getLogger().info("✓ State update: " + serviceName + " -> " + state +
                    " (" + players + "/" + maxPlayers + ", alive: " + alive + ")");

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse state update: " + e.getMessage());
            plugin.getLogger().warning("Message: " + message);
            e.printStackTrace();
        }
    }

    /**
     * FIXED: Handles heartbeat from Redis.
     * Properly extracts service name from JSON.
     */
    private void handleHeartbeat(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            // ✅ FIXED: Extract service name from JSON
            if (!json.has("service")) {
                plugin.getLogger().fine("Heartbeat missing 'service' field");
                return;
            }

            String serviceName = json.get("service").getAsString();

            // Validate service name
            if (serviceName == null || serviceName.isEmpty() || serviceName.contains("${")) {
                plugin.getLogger().fine("Invalid service name in heartbeat: " + serviceName);
                return;
            }

            int players = json.has("players") ? json.get("players").getAsInt() : 0;

            // Get or create service
            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> {
                        plugin.getLogger().info("New service detected via heartbeat: " + name);
                        return new GameService(name, gamemode.getId());
                    }
            );

            service.updateHeartbeat();
            service.setCurrentPlayers(players);

            plugin.getLogger().finest("Heartbeat: " + serviceName + " (" + players + " players)");

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to parse heartbeat: " + e.getMessage());
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
                        plugin.getLogger().warning("Service marked as offline (no heartbeat): " +
                                service.getServiceName());
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (GamemodeConfig gamemode : gamemodes.values()) {
                JsonObject request = new JsonObject();
                request.addProperty("action", "request_state");
                request.addProperty("timestamp", System.currentTimeMillis());

                redisMessenger.publish(
                        gamemode.getControlChannel(),
                        request.toString()
                );

                plugin.getLogger().info("Sent state request to: " + gamemode.getControlChannel());
            }
        }, 40L); // Wait 2 seconds after startup
    }

    /**
     * FIXED: Connect player using the actual service name from the GameService object.
     */
    public void connectPlayer(@NotNull Player player, @NotNull String serviceName) {
        // Get service object
        GameService service = services.get(serviceName);

        if (service == null) {
            player.sendMessage("§cThat server is not available!");
            plugin.getLogger().warning("Service not found: " + serviceName);
            plugin.getLogger().warning("Available services: " + services.keySet());
            return;
        }

        if (!service.isOnline()) {
            player.sendMessage("§cThat server is offline!");
            plugin.getLogger().warning("Service offline: " + serviceName);
            return;
        }

        if (!service.isJoinable()) {
            player.sendMessage("§cYou cannot join that server right now!");
            plugin.getLogger().warning("Service not joinable: " + serviceName +
                    " (state: " + service.getState() + ")");
            return;
        }

        // ✅ FIXED: Use the actual service name from the GameService object
        String actualServiceName = service.getServiceName();

        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            out.writeUTF("Connect");
            out.writeUTF(actualServiceName);  // ✅ Use validated service name

            byte[] data = b.toByteArray();

            // Try velocity:main first (modern)
            boolean sent = false;
            try {
                player.sendPluginMessage(plugin, "velocity:main", data);
                plugin.getLogger().info("✓ Sent connection request via velocity:main");
                plugin.getLogger().info("  Player: " + player.getName());
                plugin.getLogger().info("  Target: " + actualServiceName);
                sent = true;
            } catch (Exception e) {
                plugin.getLogger().fine("velocity:main not available: " + e.getMessage());
            }

            // Fallback to bungeecord:main
            if (!sent) {
                try {
                    player.sendPluginMessage(plugin, "bungeecord:main", data);
                    plugin.getLogger().info("✓ Sent connection request via bungeecord:main");
                    plugin.getLogger().info("  Player: " + player.getName());
                    plugin.getLogger().info("  Target: " + actualServiceName);
                    sent = true;
                } catch (Exception e) {
                    plugin.getLogger().fine("bungeecord:main not available: " + e.getMessage());
                }
            }

            if (sent) {
                player.sendMessage("§aConnecting to " + actualServiceName + "...");
            } else {
                player.sendMessage("§cFailed to connect to server!");
                plugin.getLogger().severe("No plugin messaging channel worked!");
                plugin.getLogger().severe("Make sure Velocity plugin messaging is enabled!");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create plugin message: " + e.getMessage());
            player.sendMessage("§cFailed to connect to server!");
        }
    }

    @NotNull
    public List<GameService> getServices(@NotNull String gamemodeId) {
        List<GameService> result = services.values().stream()
                .filter(service -> service.getGamemodeId().equals(gamemodeId))
                .sorted(Comparator.comparing(GameService::getServiceName))
                .collect(Collectors.toList());

        plugin.getLogger().fine("getServices(" + gamemodeId + "): " + result.size() + " services");
        return result;
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

    /**
     * Debug method to show all tracked services.
     */
    public void debugServices() {
        plugin.getLogger().info("━━━━━━━━ TRACKED SERVICES ━━━━━━━━");
        plugin.getLogger().info("Total services: " + services.size());

        if (services.isEmpty()) {
            plugin.getLogger().warning("⚠ NO SERVICES DETECTED!");
            plugin.getLogger().warning("Possible causes:");
            plugin.getLogger().warning("1. Game servers not broadcasting state");
            plugin.getLogger().warning("2. Redis channels don't match");
            plugin.getLogger().warning("3. CloudNet service name contains '${service.name}'");
        }

        for (GameService service : services.values()) {
            plugin.getLogger().info("  • " + service.getServiceName());
            plugin.getLogger().info("    Gamemode: " + service.getGamemodeId());
            plugin.getLogger().info("    State: " + service.getState());
            plugin.getLogger().info("    Players: " + service.getCurrentPlayers() + "/" + service.getMaxPlayers());
            plugin.getLogger().info("    Online: " + service.isOnline());
            plugin.getLogger().info("    Joinable: " + service.isJoinable());
        }

        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down GameServiceManager...");
        services.clear();
        gamemodes.clear();
    }
}