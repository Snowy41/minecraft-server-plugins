package com.yourserver.social.database;

import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FIXED: MySQL implementation of friend storage.
 * Now properly stores and retrieves player names.
 */
public class MySQLFriendRepository {

    private final DataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public MySQLFriendRepository(@NotNull DataSource dataSource,
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

                        // Update name if stored name is invalid (UUID format or empty)
                        if (friendName == null || friendName.isEmpty() || isUUID(friendName)) {
                            friendName = getPlayerNameFromUUID(friendUuid);
                            // Update database with correct name
                            updateFriendName(playerUuid, friendUuid, friendName);
                        }

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

                    // Get actual player names
                    String player1Name = getPlayerNameFromUUID(player1);
                    String player2Name = getPlayerNameFromUUID(player2);

                    // Add player1 -> player2
                    stmt.setString(1, player1.toString());
                    stmt.setString(2, player2.toString());
                    stmt.setString(3, player2Name);
                    stmt.setTimestamp(4, timestamp);
                    stmt.addBatch();

                    // Add player2 -> player1
                    stmt.setString(1, player2.toString());
                    stmt.setString(2, player1.toString());
                    stmt.setString(3, player1Name);
                    stmt.setTimestamp(4, timestamp);
                    stmt.addBatch();

                    stmt.executeBatch();
                    conn.commit();

                    logger.info("Added friendship: " + player1Name + " <-> " + player2Name);

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

                int deleted = stmt.executeUpdate();

                if (deleted > 0) {
                    logger.info("Removed friendship between " + player1 + " and " + player2);
                }

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

                        // Update name if stored name is invalid
                        if (fromName == null || fromName.isEmpty() || isUUID(fromName)) {
                            fromName = getPlayerNameFromUUID(fromUuid);
                        }

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

                        // Update name if invalid
                        if (fromName == null || fromName.isEmpty() || isUUID(fromName)) {
                            fromName = getPlayerNameFromUUID(from);
                        }

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
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), from_name = VALUES(from_name)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Ensure we have a valid name
                String fromName = request.getFromName();
                if (fromName == null || fromName.isEmpty() || isUUID(fromName)) {
                    fromName = getPlayerNameFromUUID(request.getFromUuid());
                }

                stmt.setString(1, request.getFromUuid().toString());
                stmt.setString(2, fromName);
                stmt.setString(3, request.getToUuid().toString());
                stmt.setTimestamp(4, Timestamp.from(request.getExpiresAt()));

                stmt.executeUpdate();

                logger.info("Created friend request: " + fromName + " -> " + request.getToUuid());

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

    // ===== HELPER METHODS =====

    /**
     * Updates a friend's name in the database.
     */
    private void updateFriendName(@NotNull UUID playerUuid, @NotNull UUID friendUuid, @NotNull String friendName) {
        String sql = "UPDATE social_friends SET friend_name = ? WHERE player_uuid = ? AND friend_uuid = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, friendName);
            stmt.setString(2, playerUuid.toString());
            stmt.setString(3, friendUuid.toString());

            stmt.executeUpdate();
            logger.fine("Updated friend name: " + friendUuid + " -> " + friendName);

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Failed to update friend name", e);
        }
    }

    /**
     * Cleans up invalid friend names in the database.
     * Call this once on startup to fix existing data.
     *
     * Add this to MySQLFriendRepository.java
     */
    @NotNull
    public CompletableFuture<Integer> cleanupInvalidFriendNames() {
        return CompletableFuture.supplyAsync(() -> {
            String selectSql = "SELECT player_uuid, friend_uuid, friend_name FROM social_friends WHERE friend_name REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'";
            String updateSql = "UPDATE social_friends SET friend_name = ? WHERE player_uuid = ? AND friend_uuid = ?";

            int fixedCount = 0;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement(selectSql);
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

                // Find all records with UUID-formatted names
                ResultSet rs = selectStmt.executeQuery();

                while (rs.next()) {
                    String playerUuidStr = rs.getString("player_uuid");
                    String friendUuidStr = rs.getString("friend_uuid");
                    String currentName = rs.getString("friend_name");

                    UUID friendUuid = UUID.fromString(friendUuidStr);

                    // Try to get the correct name
                    String correctName = getPlayerNameFromUUID(friendUuid);

                    // If we got a valid name (not a UUID), update it
                    if (!isUUID(correctName)) {
                        updateStmt.setString(1, correctName);
                        updateStmt.setString(2, playerUuidStr);
                        updateStmt.setString(3, friendUuidStr);
                        updateStmt.addBatch();
                        fixedCount++;

                        logger.info("Fixed friend name: " + currentName + " -> " + correctName);
                    }
                }

                // Execute batch update
                if (fixedCount > 0) {
                    updateStmt.executeBatch();
                    logger.info("Fixed " + fixedCount + " invalid friend names");
                }

                rs.close();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to cleanup friend names", e);
            }

            // Also clean up friend requests
            try (Connection conn = dataSource.getConnection()) {
                String cleanupRequestsSql = "UPDATE social_friend_requests SET from_name = ? WHERE from_uuid = ? AND from_name REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'";

                String selectRequestsSql = "SELECT from_uuid, from_name FROM social_friend_requests WHERE from_name REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'";

                try (PreparedStatement selectStmt = conn.prepareStatement(selectRequestsSql);
                     PreparedStatement updateStmt = conn.prepareStatement(cleanupRequestsSql)) {

                    ResultSet rs = selectStmt.executeQuery();
                    int requestsFixed = 0;

                    while (rs.next()) {
                        UUID fromUuid = UUID.fromString(rs.getString("from_uuid"));
                        String correctName = getPlayerNameFromUUID(fromUuid);

                        if (!isUUID(correctName)) {
                            updateStmt.setString(1, correctName);
                            updateStmt.setString(2, fromUuid.toString());
                            updateStmt.addBatch();
                            requestsFixed++;
                        }
                    }

                    if (requestsFixed > 0) {
                        updateStmt.executeBatch();
                        logger.info("Fixed " + requestsFixed + " invalid friend request names");
                        fixedCount += requestsFixed;
                    }

                    rs.close();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to cleanup friend request names", e);
            }

            return fixedCount;
        }, executor);
    }

    /**
     * Gets a player's name from their UUID.
     * Tries online players first, then offline players.
     */
    @NotNull
    private String getPlayerNameFromUUID(@NotNull UUID uuid) {
        // Try online player first (most reliable)
        org.bukkit.entity.Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        // Fall back to offline player
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();

        // If name is still null (shouldn't happen), use UUID substring as last resort
        if (name == null || name.isEmpty()) {
            logger.warning("Could not find name for UUID: " + uuid);
            return uuid.toString().substring(0, 8);
        }

        return name;
    }

    /**
     * Checks if a string looks like a UUID.
     */
    private boolean isUUID(@NotNull String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}