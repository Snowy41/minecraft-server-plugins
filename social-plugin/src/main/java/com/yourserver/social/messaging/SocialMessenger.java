package com.yourserver.social.messaging;

import com.google.gson.Gson;
import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.social.SocialPlugin;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-server messaging system using CorePlugin's existing Redis.
 *
 * Handles:
 * - Friend requests/accepts across servers
 * - Party invites/joins/leaves across servers
 * - Clan chat across all servers
 * - Online status synchronization
 *
 * NO ADDITIONAL INFRASTRUCTURE NEEDED - uses existing Redis from CorePlugin!
 */
public class SocialMessenger {

    private final SocialPlugin plugin;
    private final RedisMessenger redisMessenger;
    private final Gson gson;

    // Channels
    private static final String CHANNEL_FRIENDS = "social:friends";
    private static final String CHANNEL_PARTY = "social:party";
    private static final String CHANNEL_CLAN = "social:clan";
    private static final String CHANNEL_STATUS = "social:status";

    // Message handlers
    private final Map<MessageType, MessageHandler> handlers;

    public SocialMessenger(@NotNull SocialPlugin plugin, @NotNull RedisMessenger redisMessenger) {
        this.plugin = plugin;
        this.redisMessenger = redisMessenger;
        this.gson = new Gson();
        this.handlers = new ConcurrentHashMap<>();

        // Subscribe to all social channels
        subscribeToChannels();
    }

    /**
     * Subscribes to all social channels.
     */
    private void subscribeToChannels() {
        // Friends channel
        redisMessenger.subscribe(CHANNEL_FRIENDS, message -> {
            handleMessage(message, MessageType.FRIEND);
        });

        // Party channel
        redisMessenger.subscribe(CHANNEL_PARTY, message -> {
            handleMessage(message, MessageType.PARTY);
        });

        // Clan channel
        redisMessenger.subscribe(CHANNEL_CLAN, message -> {
            handleMessage(message, MessageType.CLAN);
        });

        // Status channel
        redisMessenger.subscribe(CHANNEL_STATUS, message -> {
            handleMessage(message, MessageType.STATUS);
        });

        plugin.getLogger().info("✓ Subscribed to all social channels");
    }

    // ===== FRIEND MESSAGES =====

    /**
     * Sends friend request across servers.
     */
    public void sendFriendRequest(@NotNull UUID from, @NotNull UUID to) {
        SocialMessage msg = new SocialMessage(
                MessageType.FRIEND,
                "REQUEST",
                from,
                to,
                Map.of("action", "request")
        );
        publish(CHANNEL_FRIENDS, msg);
    }

    /**
     * Accepts friend request.
     */
    public void acceptFriendRequest(@NotNull UUID from, @NotNull UUID to) {
        SocialMessage msg = new SocialMessage(
                MessageType.FRIEND,
                "ACCEPT",
                from,
                to,
                Map.of("action", "accept")
        );
        publish(CHANNEL_FRIENDS, msg);
    }

    /**
     * Removes friend.
     */
    public void removeFriend(@NotNull UUID from, @NotNull UUID to) {
        SocialMessage msg = new SocialMessage(
                MessageType.FRIEND,
                "REMOVE",
                from,
                to,
                Map.of("action", "remove")
        );
        publish(CHANNEL_FRIENDS, msg);
    }

    // ===== PARTY MESSAGES =====

    /**
     * Sends party invite across servers.
     */
    public void sendPartyInvite(@NotNull String partyId, @NotNull UUID from, @NotNull UUID to) {
        SocialMessage msg = new SocialMessage(
                MessageType.PARTY,
                "INVITE",
                from,
                to,
                Map.of(
                        "partyId", partyId,
                        "action", "invite"
                )
        );
        publish(CHANNEL_PARTY, msg);
    }

    /**
     * Player joins party.
     */
    public void joinParty(@NotNull String partyId, @NotNull UUID player, @NotNull String playerName) {
        SocialMessage msg = new SocialMessage(
                MessageType.PARTY,
                "JOIN",
                player,
                null,
                Map.of(
                        "partyId", partyId,
                        "playerName", playerName,
                        "action", "join"
                )
        );
        publish(CHANNEL_PARTY, msg);
    }

    /**
     * Player leaves party.
     */
    public void leaveParty(@NotNull String partyId, @NotNull UUID player, @NotNull String playerName) {
        SocialMessage msg = new SocialMessage(
                MessageType.PARTY,
                "LEAVE",
                player,
                null,
                Map.of(
                        "partyId", partyId,
                        "playerName", playerName,
                        "action", "leave"
                )
        );
        publish(CHANNEL_PARTY, msg);
    }

    /**
     * Party warp (all members teleport to same server).
     */
    public void warpParty(@NotNull String partyId, @NotNull String targetServer) {
        SocialMessage msg = new SocialMessage(
                MessageType.PARTY,
                "WARP",
                null,
                null,
                Map.of(
                        "partyId", partyId,
                        "targetServer", targetServer,
                        "action", "warp"
                )
        );
        publish(CHANNEL_PARTY, msg);
    }

    /**
     * Party chat message.
     */
    public void sendPartyChat(@NotNull String partyId, @NotNull UUID sender,
                              @NotNull String senderName, @NotNull String message) {
        SocialMessage msg = new SocialMessage(
                MessageType.PARTY,
                "CHAT",
                sender,
                null,
                Map.of(
                        "partyId", partyId,
                        "senderName", senderName,
                        "message", message,
                        "action", "chat"
                )
        );
        publish(CHANNEL_PARTY, msg);
    }

    // ===== CLAN MESSAGES =====

    /**
     * Sends clan invite.
     */
    public void sendClanInvite(@NotNull String clanId, @NotNull UUID from, @NotNull UUID to) {
        SocialMessage msg = new SocialMessage(
                MessageType.CLAN,
                "INVITE",
                from,
                to,
                Map.of(
                        "clanId", clanId,
                        "action", "invite"
                )
        );
        publish(CHANNEL_CLAN, msg);
    }

    /**
     * Player joins clan.
     */
    public void joinClan(@NotNull String clanId, @NotNull UUID player, @NotNull String playerName) {
        SocialMessage msg = new SocialMessage(
                MessageType.CLAN,
                "JOIN",
                player,
                null,
                Map.of(
                        "clanId", clanId,
                        "playerName", playerName,
                        "action", "join"
                )
        );
        publish(CHANNEL_CLAN, msg);
    }

    /**
     * Clan chat message (cross-server).
     */
    public void sendClanChat(@NotNull String clanId, @NotNull UUID sender,
                             @NotNull String senderName, @NotNull String message) {
        SocialMessage msg = new SocialMessage(
                MessageType.CLAN,
                "CHAT",
                sender,
                null,
                Map.of(
                        "clanId", clanId,
                        "senderName", senderName,
                        "message", message,
                        "action", "chat"
                )
        );
        publish(CHANNEL_CLAN, msg);
    }

    // ===== STATUS MESSAGES =====

    /**
     * Updates player's online status.
     */
    public void updateStatus(@NotNull UUID player, @NotNull String server, @NotNull PlayerStatus status) {
        SocialMessage msg = new SocialMessage(
                MessageType.STATUS,
                "UPDATE",
                player,
                null,
                Map.of(
                        "server", server,
                        "status", status.name(),
                        "action", "update"
                )
        );
        publish(CHANNEL_STATUS, msg);

        // Also update Redis cache
        String serverKey = "player:" + player + ":server";
        String statusKey = "player:" + player + ":status";

        redisMessenger.set(serverKey, server, 300); // 5 minutes TTL
        redisMessenger.set(statusKey, status.name(), 300);
    }

    // ===== MESSAGE HANDLING =====

    /**
     * Registers a handler for a specific message type.
     */
    public void onMessage(@NotNull MessageType type, @NotNull MessageHandler handler) {
        handlers.put(type, handler);
    }

    /**
     * Publishes message to channel.
     */
    private void publish(@NotNull String channel, @NotNull SocialMessage message) {
        String json = gson.toJson(message);
        redisMessenger.publish(channel, json);
    }

    /**
     * Handles incoming message from Redis.
     */
    private void handleMessage(@NotNull String json, @NotNull MessageType expectedType) {
        try {
            SocialMessage message = gson.fromJson(json, SocialMessage.class);

            // Run on main thread (Bukkit requirement)
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageHandler handler = handlers.get(message.type);
                if (handler != null) {
                    handler.handle(message);
                }
            });

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to handle message: " + e.getMessage());
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Checks if player is online (on any server).
     */
    public boolean isOnline(@NotNull UUID player) {
        String key = "player:" + player + ":server";
        return redisMessenger.exists(key);
    }

    /**
     * Gets player's current server.
     */
    public String getPlayerServer(@NotNull UUID player) {
        String key = "player:" + player + ":server";
        return redisMessenger.get(key);
    }

    /**
     * Gets player's status.
     */
    public PlayerStatus getPlayerStatus(@NotNull UUID player) {
        String key = "player:" + player + ":status";
        String status = redisMessenger.get(key);
        return status != null ? PlayerStatus.valueOf(status) : PlayerStatus.OFFLINE;
    }

    /**
     * Shuts down messenger.
     */
    public void shutdown() {
        // Unsubscribe from all channels
        redisMessenger.unsubscribe(CHANNEL_FRIENDS);
        redisMessenger.unsubscribe(CHANNEL_PARTY);
        redisMessenger.unsubscribe(CHANNEL_CLAN);
        redisMessenger.unsubscribe(CHANNEL_STATUS);

        handlers.clear();
        plugin.getLogger().info("✓ Messenger shut down");
    }

    // ===== DATA MODELS =====

    public static class SocialMessage {
        public final MessageType type;
        public final String action;
        public final UUID from;
        public final UUID to;
        public final Map<String, String> data;
        public final long timestamp;

        public SocialMessage(@NotNull MessageType type, @NotNull String action,
                             UUID from, UUID to, @NotNull Map<String, String> data) {
            this.type = type;
            this.action = action;
            this.from = from;
            this.to = to;
            this.data = new HashMap<>(data);
            this.timestamp = System.currentTimeMillis();
        }
    }

    public enum MessageType {
        FRIEND,
        PARTY,
        CLAN,
        STATUS
    }

    public enum PlayerStatus {
        ONLINE,
        AWAY,
        DND,      // Do Not Disturb
        OFFLINE
    }

    @FunctionalInterface
    public interface MessageHandler {
        void handle(SocialMessage message);
    }
}