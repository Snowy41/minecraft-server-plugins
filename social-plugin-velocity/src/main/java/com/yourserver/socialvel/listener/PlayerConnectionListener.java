package com.yourserver.socialvel.listener;

import com.google.gson.Gson;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.yourserver.socialvel.SocialPluginVelocity;
import com.yourserver.socialvel.messaging.RedisManager;
import com.yourserver.socialvel.model.PlayerStatus;
import com.yourserver.socialvel.storage.JSONPlayerStatusStorage;

import java.util.HashMap;
import java.util.Map;

public class PlayerConnectionListener {

    private final SocialPluginVelocity plugin;
    private final RedisManager redis;
    private final JSONPlayerStatusStorage storage;
    private final Gson gson;

    private static final String CHANNEL_STATUS = "social:status";

    public PlayerConnectionListener(SocialPluginVelocity plugin, RedisManager redis,
                                    JSONPlayerStatusStorage storage) {
        this.plugin = plugin;
        this.redis = redis;
        this.storage = storage;
        this.gson = new Gson();
    }

    @Subscribe(order = PostOrder.LATE)
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getServer().getServerInfo().getName();

        // Update player status
        PlayerStatus status = new PlayerStatus(
                player.getUniqueId(),
                serverName,
                PlayerStatus.Status.ONLINE,
                System.currentTimeMillis()
        );

        storage.updateStatus(status);

        // Update Redis cache
        String serverKey = "player:" + player.getUniqueId() + ":server";
        String statusKey = "player:" + player.getUniqueId() + ":status";

        redis.set(serverKey, serverName, 300); // 5 minutes TTL
        redis.set(statusKey, "ONLINE", 300);

        // Publish status update
        Map<String, String> data = new HashMap<>();
        data.put("action", "update");
        data.put("server", serverName);
        data.put("status", "ONLINE");

        Map<String, Object> message = new HashMap<>();
        message.put("type", "STATUS");
        message.put("action", "UPDATE");
        message.put("from", player.getUniqueId().toString());
        message.put("to", null);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        redis.publish(CHANNEL_STATUS, gson.toJson(message));

        plugin.getLogger().info("Player " + player.getUsername() +
                " connected to " + serverName);
    }

    @Subscribe(order = PostOrder.LATE)
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();

        // Update player status
        PlayerStatus status = new PlayerStatus(
                player.getUniqueId(),
                null,
                PlayerStatus.Status.OFFLINE,
                System.currentTimeMillis()
        );

        storage.updateStatus(status);

        // Remove from Redis cache
        String serverKey = "player:" + player.getUniqueId() + ":server";
        String statusKey = "player:" + player.getUniqueId() + ":status";

        redis.delete(serverKey);
        redis.set(statusKey, "OFFLINE", 300);

        // Publish status update
        Map<String, String> data = new HashMap<>();
        data.put("action", "update");
        data.put("server", "");
        data.put("status", "OFFLINE");

        Map<String, Object> message = new HashMap<>();
        message.put("type", "STATUS");
        message.put("action", "UPDATE");
        message.put("from", player.getUniqueId().toString());
        message.put("to", null);
        message.put("data", data);
        message.put("timestamp", System.currentTimeMillis());

        redis.publish(CHANNEL_STATUS, gson.toJson(message));

        plugin.getLogger().info("Player " + player.getUsername() + " disconnected");
    }
}