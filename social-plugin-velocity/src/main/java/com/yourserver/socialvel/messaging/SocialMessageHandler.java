package com.yourserver.socialvel.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.yourserver.socialvel.SocialPluginVelocity;
import com.yourserver.socialvel.storage.JSONPlayerStatusStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;
import java.util.UUID;

public class SocialMessageHandler {

    private final SocialPluginVelocity plugin;
    private final RedisManager redis;
    private final JSONPlayerStatusStorage storage;
    private final Gson gson;
    private final MiniMessage miniMessage;

    // Channels
    private static final String CHANNEL_PARTY = "social:party";
    private static final String CHANNEL_FRIENDS = "social:friends";
    private static final String CHANNEL_CLAN = "social:clan";

    public SocialMessageHandler(SocialPluginVelocity plugin, RedisManager redis,
                                JSONPlayerStatusStorage storage) {
        this.plugin = plugin;
        this.redis = redis;
        this.storage = storage;
        this.gson = new Gson();
        this.miniMessage = MiniMessage.miniMessage();

        // Subscribe to channels
        redis.subscribe(CHANNEL_PARTY, this::handlePartyMessage);
        redis.subscribe(CHANNEL_FRIENDS, this::handleFriendMessage);
        redis.subscribe(CHANNEL_CLAN, this::handleClanMessage);
    }

    private void handlePartyMessage(String json) {
        try {
            JsonObject msg = gson.fromJson(json, JsonObject.class);
            String action = msg.get("action").getAsString();

            switch (action) {
                case "warp" -> handlePartyWarp(msg);
                case "chat" -> {} // Handled by Paper servers
                default -> {}
            }
        } catch (Exception e) {
            plugin.getLogger().error("Failed to handle party message", e);
        }
    }

    private void handleFriendMessage(String json) {
        // Friends messages are handled by Paper servers
        // Velocity just routes them through Redis
    }

    private void handleClanMessage(String json) {
        // Clan messages are handled by Paper servers
        // Velocity just routes them through Redis
    }

    private void handlePartyWarp(JsonObject msg) {
        String partyId = msg.get("partyId").getAsString();
        String targetServerName = msg.get("targetServer").getAsString();

        // Get party data from Redis
        String partyKey = "party:" + partyId;
        String partyJson = redis.get(partyKey);

        if (partyJson == null) {
            plugin.getLogger().warn("Party not found for warp: " + partyId);
            return;
        }

        try {
            JsonObject party = gson.fromJson(partyJson, JsonObject.class);
            JsonObject members = party.getAsJsonObject("members");

            // Find target server
            Optional<RegisteredServer> targetServer = plugin.getProxy()
                    .getServer(targetServerName);

            if (targetServer.isEmpty()) {
                plugin.getLogger().warn("Target server not found: " + targetServerName);
                return;
            }

            // Warp all party members
            for (String memberUuidStr : members.keySet()) {
                UUID memberUuid = UUID.fromString(memberUuidStr);
                Optional<Player> player = plugin.getProxy().getPlayer(memberUuid);

                if (player.isPresent()) {
                    player.get().createConnectionRequest(targetServer.get())
                            .fireAndForget();

                    player.get().sendMessage(miniMessage.deserialize(
                            "<gradient:#FFD700:#FFA500>[Social]</gradient> " +
                                    "<yellow>Warping to <white>" + targetServerName + "<yellow>..."
                    ));
                }
            }

            plugin.getLogger().info("Warped party " + partyId + " to " + targetServerName);

        } catch (Exception e) {
            plugin.getLogger().error("Failed to warp party", e);
        }
    }

    public void shutdown() {
        redis.unsubscribe(CHANNEL_PARTY);
        redis.unsubscribe(CHANNEL_FRIENDS);
        redis.unsubscribe(CHANNEL_CLAN);
    }
}