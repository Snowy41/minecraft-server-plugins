package com.yourserver.social.database;

import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL implementation of friend storage.
 * Uses CorePlugin's HikariCP connection pool.
 */
public class MySQLFriendRepository {

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public MySQLFriendRepository(@NotNull HikariDataSource dataSource,
                                 @NotNull Executor executor,
                                 @NotNull Logger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
    }

    // ===== FRIENDS =====

    @NotNull
    public CompletableFuture<List<Friend>> getFriends(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Friend> friends = new ArrayList<>();

            String sql = "SELECT friend_uuid, friend_name, since FROM social_friends WHERE player_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID friendUuid = UUID.fromString(rs.getString("friend_uuid"));
                        String friendName = rs.getString("friend_name");
                        Instant since = rs.getTimestamp("since").toInstant();

                        friends.add(new Friend(playerUuid, friendUuid, friendName, since));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get friends for: " + playerUuid, e);
            }

            return friends;
        }, executor);
    }

    @NotNull
    public CompletableFuture<Boolean> areFriends(@NotNull UUID player1, @NotNull UUID player2) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM social_friends WHERE player_uuid = ? AND friend_uuid = ? LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, player1.toString());
                stmt.setString(2, player2.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to check friendship", e);
                return false;
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> addFriend(@NotNull UUID player1, @NotNull UUID player2) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO social_friends (player_uuid, friend_uuid, friend_name, since) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE friend_name = VALUES(friend_name)";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    Instant now = Instant.now();
                    Timestamp timestamp = Timestamp.from(now);

                    // Add player1 -> player2
                    stmt.setString(1, player1.toString());
                    stmt.setString(2, player2.toString());
                    stmt.setString(3, getPlayerName(player2));
                    stmt.setTimestamp(4, timestamp);
                    stmt.addBatch();

                    // Add player2 -> player1
                    stmt.setString(1, player2.toString());
                    stmt.setString(2, player1.toString());
                    stmt.setString(3, getPlayerName(player1));
                    stmt.setTimestamp(4, timestamp);
                    stmt.addBatch();

                    stmt.executeBatch();
                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to add friendship", e);
                throw new RuntimeException("Failed to add friendship", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> removeFriend(@NotNull UUID player1, @NotNull UUID player2) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM social_friends WHERE " +
                    "(player_uuid = ? AND friend_uuid = ?) OR (player_uuid = ? AND friend_uuid = ?)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, player1.toString());
                stmt.setString(2, player2.toString());
                stmt.setString(3, player2.toString());
                stmt.setString(4, player1.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to remove friendship", e);
                throw new RuntimeException("Failed to remove friendship", e);
            }
        }, executor);
    }

    // ===== FRIEND REQUESTS =====

    @NotNull
    public CompletableFuture<List<FriendRequest>> getPendingRequests(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<FriendRequest> requests = new ArrayList<>();

            String sql = "SELECT id, from_uuid, from_name, created_at, expires_at " +
                    "FROM social_friend_requests WHERE to_uuid = ? AND expires_at > NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        UUID fromUuid = UUID.fromString(rs.getString("from_uuid"));
                        String fromName = rs.getString("from_name");
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();

                        requests.add(new FriendRequest(id, fromUuid, fromName, playerUuid, createdAt, expiresAt));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get pending requests", e);
            }

            return requests;
        }, executor);
    }

    @NotNull
    public CompletableFuture<Optional<FriendRequest>> getRequest(@NotNull UUID from, @NotNull UUID to) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id, from_name, created_at, expires_at " +
                    "FROM social_friend_requests WHERE from_uuid = ? AND to_uuid = ? AND expires_at > NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, from.toString());
                stmt.setString(2, to.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        String fromName = rs.getString("from_name");
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();

                        return Optional.of(new FriendRequest(id, from, fromName, to, createdAt, expiresAt));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get request", e);
            }

            return Optional.empty();
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> createRequest(@NotNull FriendRequest request) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO social_friend_requests (from_uuid, from_name, to_uuid, expires_at) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, request.getFromUuid().toString());
                stmt.setString(2, request.getFromName());
                stmt.setString(3, request.getToUuid().toString());
                stmt.setTimestamp(4, Timestamp.from(request.getExpiresAt()));

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create request", e);
                throw new RuntimeException("Failed to create request", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> deleteRequest(@NotNull UUID from, @NotNull UUID to) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM social_friend_requests WHERE from_uuid = ? AND to_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, from.toString());
                stmt.setString(2, to.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete request", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Integer> deleteExpiredRequests() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM social_friend_requests WHERE expires_at < NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                int count = stmt.executeUpdate();
                if (count > 0) {
                    logger.info("Deleted " + count + " expired friend requests");
                }
                return count;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete expired requests", e);
                return 0;
            }
        }, executor);
    }

    private String getPlayerName(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }
}