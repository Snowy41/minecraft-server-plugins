package com.yourserver.social.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yourserver.social.SocialPlugin;
import com.yourserver.social.database.JSONClanRepository;
import com.yourserver.social.database.JSONFriendRepository;
import com.yourserver.social.database.MySQLFriendRepository;
import com.yourserver.social.messaging.SocialMessenger;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages friend system with caching and cross-server messaging.
 */
public class FriendManager {

    private final SocialPlugin plugin;
    private final JSONFriendRepository repository;
    private final SocialMessenger messenger;

    // Cache friends list (10 minutes)
    private final Cache<UUID, List<Friend>> friendsCache;

    public FriendManager(@NotNull SocialPlugin plugin,
                         @NotNull JSONFriendRepository repository,
                         @NotNull SocialMessenger messenger) {
        this.plugin = plugin;
        this.repository = repository;
        this.messenger = messenger;

        // Initialize cache
        this.friendsCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        // Register message handlers
        setupMessageHandlers();

        // Schedule expired request cleanup (every 5 minutes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            repository.deleteExpiredRequests();
        }, 6000L, 6000L);
    }

    /**
     * Sets up cross-server message handlers.
     */
    private void setupMessageHandlers() {
        messenger.onMessage(SocialMessenger.MessageType.FRIEND, msg -> {
            String action = msg.data.get("action");

            switch (action) {
                case "request" -> handleFriendRequestMessage(msg);
                case "accept" -> handleFriendAcceptMessage(msg);
                case "remove" -> handleFriendRemoveMessage(msg);
            }
        });
    }

    // ===== FRIEND REQUESTS =====

    /**
     * Sends a friend request.
     */
    @NotNull
    public CompletableFuture<RequestResult> sendFriendRequest(@NotNull Player sender,
                                                              @NotNull UUID targetUuid,
                                                              @NotNull String targetName) {
        UUID senderUuid = sender.getUniqueId();

        // Validation checks
        if (senderUuid.equals(targetUuid)) {
            return CompletableFuture.completedFuture(RequestResult.CANNOT_ADD_SELF);
        }

        // Check if already friends
        return repository.areFriends(senderUuid, targetUuid).thenCompose(areFriends -> {
            if (areFriends) {
                return CompletableFuture.completedFuture(RequestResult.ALREADY_FRIENDS);
            }

            // Check if request already exists
            return repository.getRequest(senderUuid, targetUuid).thenCompose(existing -> {
                if (existing.isPresent()) {
                    return CompletableFuture.completedFuture(RequestResult.REQUEST_EXISTS);
                }

                // Check friend limit
                return repository.getFriends(senderUuid).thenCompose(friends -> {
                    int maxFriends = plugin.getSocialConfig().getFriendsConfig().getMaxFriends();
                    if (friends.size() >= maxFriends) {
                        return CompletableFuture.completedFuture(RequestResult.MAX_FRIENDS);
                    }

                    // Create request
                    int expireSeconds = plugin.getSocialConfig().getFriendsConfig()
                            .getRequestExpireSeconds();
                    FriendRequest request = FriendRequest.create(
                            senderUuid,
                            sender.getName(),
                            targetUuid,
                            expireSeconds
                    );

                    return repository.createRequest(request).thenApply(v -> {
                        // Send cross-server message
                        messenger.sendFriendRequest(senderUuid, targetUuid);
                        return RequestResult.SUCCESS;
                    });
                });
            });
        });
    }

    /**
     * Accepts a friend request.
     */
    @NotNull
    public CompletableFuture<RequestResult> acceptFriendRequest(@NotNull Player accepter,
                                                                @NotNull UUID fromUuid) {
        UUID accepterUuid = accepter.getUniqueId();

        return repository.getRequest(fromUuid, accepterUuid).thenCompose(requestOpt -> {
            if (requestOpt.isEmpty()) {
                return CompletableFuture.completedFuture(RequestResult.REQUEST_NOT_FOUND);
            }

            FriendRequest request = requestOpt.get();

            if (request.isExpired()) {
                return repository.deleteRequest(fromUuid, accepterUuid)
                        .thenApply(v -> RequestResult.REQUEST_EXPIRED);
            }

            // Add friend relationship
            return repository.addFriend(fromUuid, accepterUuid).thenCompose(v -> {
                // Delete request
                return repository.deleteRequest(fromUuid, accepterUuid).thenApply(v2 -> {
                    // Invalidate caches
                    friendsCache.invalidate(fromUuid);
                    friendsCache.invalidate(accepterUuid);

                    // Send cross-server message
                    messenger.acceptFriendRequest(accepterUuid, fromUuid);
                    return RequestResult.SUCCESS;
                });
            });
        });
    }

    /**
     * Denies a friend request.
     */
    @NotNull
    public CompletableFuture<Void> denyFriendRequest(@NotNull UUID denier, @NotNull UUID from) {
        return repository.deleteRequest(from, denier);
    }

    /**
     * Removes a friend.
     */
    @NotNull
    public CompletableFuture<Boolean> removeFriend(@NotNull Player player, @NotNull UUID friendUuid) {
        UUID playerUuid = player.getUniqueId();

        return repository.areFriends(playerUuid, friendUuid).thenCompose(areFriends -> {
            if (!areFriends) {
                return CompletableFuture.completedFuture(false);
            }

            return repository.removeFriend(playerUuid, friendUuid).thenApply(v -> {
                // Invalidate caches
                friendsCache.invalidate(playerUuid);
                friendsCache.invalidate(friendUuid);

                // Send cross-server message
                messenger.removeFriend(playerUuid, friendUuid);
                return true;
            });
        });
    }

    // ===== QUERIES =====

    /**
     * Gets all friends for a player (cached).
     */
    @NotNull
    public CompletableFuture<List<Friend>> getFriends(@NotNull UUID playerUuid) {
        List<Friend> cached = friendsCache.getIfPresent(playerUuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return repository.getFriends(playerUuid).thenApply(friends -> {
            friendsCache.put(playerUuid, friends);
            return friends;
        });
    }

    /**
     * Gets all pending friend requests.
     */
    @NotNull
    public CompletableFuture<List<FriendRequest>> getPendingRequests(@NotNull UUID playerUuid) {
        return repository.getPendingRequests(playerUuid);
    }

    /**
     * Checks if two players are friends.
     */
    @NotNull
    public CompletableFuture<Boolean> areFriends(@NotNull UUID player1, @NotNull UUID player2) {
        return repository.areFriends(player1, player2);
    }

    // ===== MESSAGE HANDLERS =====

    private void handleFriendRequestMessage(@NotNull SocialMessenger.SocialMessage msg) {
        Player target = Bukkit.getPlayer(msg.to);
        if (target == null) return;

        Player sender = Bukkit.getPlayer(msg.from);
        String senderName = sender != null ? sender.getName() : "Unknown";

        // Show notification
        String message = plugin.getSocialConfig().getMessagesConfig()
                .getFriendRequestReceived()
                .replace("{player}", senderName);

        target.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() + message
        ));
    }

    private void handleFriendAcceptMessage(@NotNull SocialMessenger.SocialMessage msg) {
        Player sender = Bukkit.getPlayer(msg.from);
        if (sender == null) return;

        Player accepter = Bukkit.getPlayer(msg.to);
        String accepterName = accepter != null ? accepter.getName() : "Unknown";

        // Show notification
        String message = plugin.getSocialConfig().getMessagesConfig()
                .getFriendAdded()
                .replace("{player}", accepterName);

        sender.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() + message
        ));
    }

    private void handleFriendRemoveMessage(@NotNull SocialMessenger.SocialMessage msg) {
        // Invalidate caches for both players if they're online
        Player p1 = Bukkit.getPlayer(msg.from);
        Player p2 = Bukkit.getPlayer(msg.to);

        if (p1 != null) friendsCache.invalidate(p1.getUniqueId());
        if (p2 != null) friendsCache.invalidate(p2.getUniqueId());
    }

    // ===== LIFECYCLE =====

    public void shutdown() {
        friendsCache.invalidateAll();
    }

    // ===== RESULT ENUM =====

    public enum RequestResult {
        SUCCESS,
        ALREADY_FRIENDS,
        REQUEST_EXISTS,
        REQUEST_NOT_FOUND,
        REQUEST_EXPIRED,
        MAX_FRIENDS,
        CANNOT_ADD_SELF,
        PLAYER_NOT_FOUND
    }
}