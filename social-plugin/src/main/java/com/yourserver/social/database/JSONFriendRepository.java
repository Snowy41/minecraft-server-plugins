package com.yourserver.social.database;

import com.google.gson.reflect.TypeToken;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import com.yourserver.social.storage.JSONStorage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * JSON-based friend storage.
 * Stores friends and friend requests in JSON files.
 */
public class JSONFriendRepository {

    private final JSONStorage<FriendData> friendStorage;
    private final JSONStorage<RequestData> requestStorage;
    private final Logger logger;

    public JSONFriendRepository(@NotNull File dataFolder, @NotNull Logger logger) {
        this.logger = logger;

        this.friendStorage = new JSONStorage<>(
                dataFolder,
                "friends.json",
                new TypeToken<FriendData>(){},
                new FriendData(),
                logger
        );

        this.requestStorage = new JSONStorage<>(
                dataFolder,
                "friend-requests.json",
                new TypeToken<RequestData>(){},
                new RequestData(),
                logger
        );
    }

    // ===== FRIENDS =====

    @NotNull
    public CompletableFuture<List<Friend>> getFriends(@NotNull UUID playerUuid) {
        return friendStorage.load().thenApply(data -> {
            List<Friend> friends = data.friendships.get(playerUuid);
            return friends != null ? new ArrayList<>(friends) : new ArrayList<>();
        });
    }

    @NotNull
    public CompletableFuture<Boolean> areFriends(@NotNull UUID player1, @NotNull UUID player2) {
        return friendStorage.load().thenApply(data -> {
            List<Friend> friends = data.friendships.get(player1);
            if (friends == null) return false;
            return friends.stream().anyMatch(f -> f.getFriendUuid().equals(player2));
        });
    }

    @NotNull
    public CompletableFuture<Void> addFriend(@NotNull UUID player1, @NotNull UUID player2) {
        return friendStorage.load().thenCompose(data -> {
            Instant now = Instant.now();

            // Add player1 -> player2
            data.friendships.computeIfAbsent(player1, k -> new ArrayList<>())
                    .add(new Friend(player1, player2, getPlayerName(player2), now));

            // Add player2 -> player1
            data.friendships.computeIfAbsent(player2, k -> new ArrayList<>())
                    .add(new Friend(player2, player1, getPlayerName(player1), now));

            return friendStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Void> removeFriend(@NotNull UUID player1, @NotNull UUID player2) {
        return friendStorage.load().thenCompose(data -> {
            // Remove player1 -> player2
            List<Friend> friends1 = data.friendships.get(player1);
            if (friends1 != null) {
                friends1.removeIf(f -> f.getFriendUuid().equals(player2));
            }

            // Remove player2 -> player1
            List<Friend> friends2 = data.friendships.get(player2);
            if (friends2 != null) {
                friends2.removeIf(f -> f.getFriendUuid().equals(player1));
            }

            return friendStorage.save(data);
        });
    }

    // ===== FRIEND REQUESTS =====

    @NotNull
    public CompletableFuture<List<FriendRequest>> getPendingRequests(@NotNull UUID playerUuid) {
        return requestStorage.load().thenApply(data -> {
            List<FriendRequest> all = data.requests.getOrDefault(playerUuid, new ArrayList<>());

            // Filter out expired requests
            List<FriendRequest> active = all.stream()
                    .filter(r -> !r.isExpired())
                    .collect(Collectors.toList());

            // Clean up expired
            if (active.size() != all.size()) {
                data.requests.put(playerUuid, active);
                requestStorage.save(data);
            }

            return active;
        });
    }

    @NotNull
    public CompletableFuture<Optional<FriendRequest>> getRequest(@NotNull UUID from, @NotNull UUID to) {
        return requestStorage.load().thenApply(data -> {
            List<FriendRequest> requests = data.requests.get(to);
            if (requests == null) return Optional.empty();

            return requests.stream()
                    .filter(r -> r.getFromUuid().equals(from) && !r.isExpired())
                    .findFirst();
        });
    }

    @NotNull
    public CompletableFuture<Void> createRequest(@NotNull FriendRequest request) {
        return requestStorage.load().thenCompose(data -> {
            data.requests.computeIfAbsent(request.getToUuid(), k -> new ArrayList<>())
                    .add(request);

            return requestStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Void> deleteRequest(@NotNull UUID from, @NotNull UUID to) {
        return requestStorage.load().thenCompose(data -> {
            List<FriendRequest> requests = data.requests.get(to);
            if (requests != null) {
                requests.removeIf(r -> r.getFromUuid().equals(from));
            }

            return requestStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Integer> deleteExpiredRequests() {
        return requestStorage.load().thenCompose(data -> {
            int count = 0;

            for (List<FriendRequest> requests : data.requests.values()) {
                int before = requests.size();
                requests.removeIf(FriendRequest::isExpired);
                count += (before - requests.size());
            }

            int finalCount = count;
            return requestStorage.save(data).thenApply(v -> finalCount);
        });
    }

    private String getPlayerName(UUID uuid) {
        // In a real implementation, you'd look this up from player cache
        return uuid.toString().substring(0, 8);
    }

    // ===== DATA MODELS =====

    public static class FriendData {
        public Map<UUID, List<Friend>> friendships = new HashMap<>();
    }

    public static class RequestData {
        public Map<UUID, List<FriendRequest>> requests = new HashMap<>();
    }
}