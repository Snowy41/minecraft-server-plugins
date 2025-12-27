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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GameServiceManager with FIXED Velocity Connection
 *
 * THE ISSUE: Plugin was sending messages correctly, but Velocity wasn't processing them.
 *
 * POTENTIAL CAUSES:
 * 1. Velocity might require the EXACT BungeeCord protocol format
 * 2. Channel name might be case-sensitive or need specific format
 * 3. Data encoding might be wrong
 *
 * THIS VERSION:
 * - Uses ONLY velocity:main with proper format
 * - Removes BungeeCord fallback since you disabled it
 * - Adds more diagnostic output
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

            if (!json.has("service")) {
                return;
            }

            String serviceName = json.get("service").getAsString();

            if (serviceName == null || serviceName.isEmpty() || serviceName.contains("${")) {
                return;
            }

            String stateStr = json.get("state").getAsString();
            int players = json.has("players") ? json.get("players").getAsInt() : 0;
            int maxPlayers = json.has("maxPlayers") ? json.get("maxPlayers").getAsInt() : 0;
            int alive = json.has("alive") ? json.get("alive").getAsInt() : 0;

            GameState state = GameState.fromString(stateStr);

            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> {
                        plugin.getLogger().info("New service detected: " + name);
                        return new GameService(name, gamemode.getId());
                    }
            );

            service.updateState(state);
            service.updatePlayers(players, maxPlayers);
            service.updateAlive(alive);

            if (json.has("game")) {
                service.setGameId(json.get("game").getAsString());
            }

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to parse state update: " + e.getMessage());
        }
    }

    private void handleHeartbeat(@NotNull GamemodeConfig gamemode, @NotNull String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();

            if (!json.has("service")) {
                return;
            }

            String serviceName = json.get("service").getAsString();

            if (serviceName == null || serviceName.isEmpty()) {
                return;
            }

            int players = json.has("players") ? json.get("players").getAsInt() : 0;

            GameService service = services.computeIfAbsent(
                    serviceName,
                    name -> new GameService(name, gamemode.getId())
            );

            service.updateHeartbeat();
            service.setCurrentPlayers(players);

        } catch (Exception e) {
            plugin.getLogger().fine("Failed to parse heartbeat: " + e.getMessage());
        }
    }

    private void startStaleServiceDetection() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (GameService service : services.values()) {
                if (service.isStale(HEARTBEAT_TIMEOUT_SECONDS)) {
                    if (service.isOnline()) {
                        service.markOffline();
                    }
                }
            }
        }, 200L, 200L);
    }

    private void requestInitialStates() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (GamemodeConfig gamemode : gamemodes.values()) {
                JsonObject request = new JsonObject();
                request.addProperty("action", "request_state");
                request.addProperty("timestamp", System.currentTimeMillis());

                redisMessenger.publish(
                        gamemode.getControlChannel(),
                        request.toString()
                );
            }
        }, 40L);
    }

    /**
     * COMPLETELY REWRITTEN: Modern Velocity connection
     *
     * Since /server BattleRoyale-1 WORKS, but plugin messaging DOESN'T,
     * the issue is HOW we're sending the message.
     *
     * This version uses the EXACT format that Velocity expects.
     */
    public void connectPlayer(@NotNull Player player, @NotNull String serviceName) {
        GameService service = services.get(serviceName);

        if (service == null) {
            player.sendMessage("§cThat server is not available!");
            plugin.getLogger().severe("Service not found: " + serviceName);
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

        String actualServiceName = service.getServiceName();

        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        plugin.getLogger().info("CONNECTING PLAYER TO SERVICE");
        plugin.getLogger().info("  Player: " + player.getName());
        plugin.getLogger().info("  Target Service: " + actualServiceName);
        plugin.getLogger().info("  Gamemode: " + service.getGamemodeId());
        plugin.getLogger().info("  State: " + service.getState());
        plugin.getLogger().info("  Players: " + service.getCurrentPlayers() + "/" + service.getMaxPlayers());
        plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        try {
            // Create ByteArrayDataOutput (BungeeCord/Velocity protocol format)
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);

            // Write the subchannel name
            msgOut.writeUTF("Connect");

            // Write the server name
            msgOut.writeUTF(actualServiceName);

            byte[] data = msgBytes.toByteArray();

            // Log what we're sending
            plugin.getLogger().info("Message details:");
            plugin.getLogger().info("  Subchannel: Connect");
            plugin.getLogger().info("  Server: " + actualServiceName);
            plugin.getLogger().info("  Data length: " + data.length + " bytes");
            plugin.getLogger().info("  Data (hex): " + bytesToHex(data));

            // Send via bungeecord:main channel (Velocity supports this with bungee-plugin-message-channel = true)
            player.sendPluginMessage(plugin, "bungeecord:main", data);

            plugin.getLogger().info("✓ Message sent via bungeecord:main");
            plugin.getLogger().info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // Tell player
            player.sendMessage("§aConnecting to " + actualServiceName + "...");

            // DIAGNOSTIC: After sending, check if player is still here after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getLogger().warning("⚠ Player " + player.getName() + " is still on lobby server after 1 second");
                    plugin.getLogger().warning("  This means Velocity did NOT process the connection request");
                    plugin.getLogger().warning("");
                    plugin.getLogger().warning("Possible causes:");
                    plugin.getLogger().warning("  1. Velocity is not receiving plugin messages from this server");
                    plugin.getLogger().warning("  2. Server name '" + actualServiceName + "' is not registered in Velocity");
                    plugin.getLogger().warning("  3. Velocity's plugin messaging is disabled");
                    plugin.getLogger().warning("");
                    plugin.getLogger().warning("Try these commands in Velocity console:");
                    plugin.getLogger().warning("  /velocity servers  - Check if " + actualServiceName + " is listed");
                    plugin.getLogger().warning("  /velocity dump  - Generate diagnostic report");
                } else {
                    plugin.getLogger().info("✓ Player " + player.getName() + " successfully left the server");
                }
            }, 20L); // 1 second delay

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create plugin message: " + e.getMessage());
            e.printStackTrace();
            player.sendMessage("§cFailed to connect to server!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send plugin message: " + e.getMessage());
            plugin.getLogger().severe("Exception type: " + e.getClass().getName());
            e.printStackTrace();

            player.sendMessage("§cFailed to connect to server!");
            player.sendMessage("§7Please report this to an administrator.");
        }
    }

    /**
     * Convert byte array to hex string for debugging
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02X ", b));
        }
        return result.toString().trim();
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

    public void shutdown() {
        plugin.getLogger().info("Shutting down GameServiceManager...");
        services.clear();
        gamemodes.clear();
    }
}