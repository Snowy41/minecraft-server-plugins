package com.yourserver.social.database;

import com.yourserver.core.database.DatabaseManager;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation for friend storage.
 * Uses CorePlugin's DatabaseManager.
 */
public class MySQLFriendRepository {

    private final DatabaseManager databaseManager;

    public MySQLFriendRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ===== FRIENDS =====

    /**
     * Gets all friends for a player.
     */
    @NotNull
    public CompletableFuture<List<Friend>> getFriends(@NotNull UUID playerUuid) {
        String sql = """
            SELECT f.friend_uuid, p.username, f.since
            FROM friends f
            JOIN players p ON f.friend_uuid = p.uuid
            WHERE f.player_uuid = ?
            ORDER BY f.since DESC
            """;

        return databaseManager.executeQuery(sql, rs -> {
            List<Friend> friends = new ArrayList<>();
            try {
                while (rs.next()) {
                    friends.add(new Friend(
                            playerUuid,
                            UUID.fromString(rs.getString("friend_uuid")),
                            rs.getString("username"),
                            rs.getTimestamp("since").toInstant()
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load friends", e);
            }
            return friends;
        }, playerUuid.toString());
    }

    /**
     * Checks if two players are friends.
     */
    @NotNull
    public CompletableFuture<Boolean> areFriends(@NotNull UUID player1, @NotNull UUID player2) {
        String sql = "SELECT 1 FROM friends WHERE player_uuid = ? AND friend_uuid = ? LIMIT 1";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                return rs.next();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check friendship", e);
            }
        }, player1.toString(), player2.toString());
    }

    /**
     * Adds a friend relationship (bidirectional).
     */
    @NotNull
    public CompletableFuture<Void> addFriend(@NotNull UUID player1, @NotNull UUID player2) {
        String sql = """
            INSERT INTO friends (player_uuid, friend_uuid, since)
            VALUES (?, ?, ?), (?, ?, ?)
            ON DUPLICATE KEY UPDATE since = VALUES(since)
            """;

        Timestamp now = Timestamp.from(Instant.now());

        return databaseManager.executeUpdate(
                sql,
                player1.toString(), player2.toString(), now,
                player2.toString(), player1.toString(), now
        ).thenApply(result -> null);
    }

    /**
     * Removes a friend relationship (bidirectional).
     */
    @NotNull
    public CompletableFuture<Void> removeFriend(@NotNull UUID player1, @NotNull UUID player2) {
        String sql = """
            DELETE FROM friends
            WHERE (player_uuid = ? AND friend_uuid = ?)
               OR (player_uuid = ? AND friend_uuid = ?)
            """;

        return databaseManager.executeUpdate(
                sql,
                player1.toString(), player2.toString(),
                player2.toString(), player1.toString()
        ).thenApply(result -> null);
    }

    // ===== FRIEND REQUESTS =====

    /**
     * Gets all pending friend requests for a player.
     */
    @NotNull
    public CompletableFuture<List<FriendRequest>> getPendingRequests(@NotNull UUID playerUuid) {
        String sql = """
            SELECT r.id, r.from_uuid, p.username as from_name, r.to_uuid, 
                   r.created_at, r.expires_at
            FROM friend_requests r
            JOIN players p ON r.from_uuid = p.uuid
            WHERE r.to_uuid = ? AND r.expires_at > NOW()
            ORDER BY r.created_at DESC
            """;

        return databaseManager.executeQuery(sql, rs -> {
            List<FriendRequest> requests = new ArrayList<>();
            try {
                while (rs.next()) {
                    requests.add(new FriendRequest(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("from_uuid")),
                            rs.getString("from_name"),
                            UUID.fromString(rs.getString("to_uuid")),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant()
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load friend requests", e);
            }
            return requests;
        }, playerUuid.toString());
    }

    /**
     * Gets a specific friend request.
     */
    @NotNull
    public CompletableFuture<Optional<FriendRequest>> getRequest(@NotNull UUID from, @NotNull UUID to) {
        String sql = """
            SELECT r.id, r.from_uuid, p.username as from_name, r.to_uuid,
                   r.created_at, r.expires_at
            FROM friend_requests r
            JOIN players p ON r.from_uuid = p.uuid
            WHERE r.from_uuid = ? AND r.to_uuid = ? AND r.expires_at > NOW()
            LIMIT 1
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    return Optional.of(new FriendRequest(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("from_uuid")),
                            rs.getString("from_name"),
                            UUID.fromString(rs.getString("to_uuid")),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant()
                    ));
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load friend request", e);
            }
        }, from.toString(), to.toString());
    }

    /**
     * Creates a friend request.
     */
    @NotNull
    public CompletableFuture<Void> createRequest(@NotNull FriendRequest request) {
        String sql = """
            INSERT INTO friend_requests (from_uuid, to_uuid, created_at, expires_at)
            VALUES (?, ?, ?, ?)
            """;

        return databaseManager.executeUpdate(
                sql,
                request.getFromUuid().toString(),
                request.getToUuid().toString(),
                Timestamp.from(request.getCreatedAt()),
                Timestamp.from(request.getExpiresAt())
        ).thenApply(result -> null);
    }

    /**
     * Deletes a friend request.
     */
    @NotNull
    public CompletableFuture<Void> deleteRequest(@NotNull UUID from, @NotNull UUID to) {
        String sql = "DELETE FROM friend_requests WHERE from_uuid = ? AND to_uuid = ?";

        return databaseManager.executeUpdate(
                sql,
                from.toString(),
                to.toString()
        ).thenApply(result -> null);
    }

    /**
     * Deletes expired friend requests (cleanup).
     */
    @NotNull
    public CompletableFuture<Integer> deleteExpiredRequests() {
        String sql = "DELETE FROM friend_requests WHERE expires_at < NOW()";

        return databaseManager.executeUpdate(sql);
    }
}