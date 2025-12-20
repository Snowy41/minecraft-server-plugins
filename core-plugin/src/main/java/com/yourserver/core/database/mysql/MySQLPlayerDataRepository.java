package com.yourserver.core.database.mysql;

import com.yourserver.api.model.PlayerData;
import com.yourserver.api.repository.PlayerDataRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL implementation of PlayerDataRepository.
 * Uses HikariCP for connection pooling and async operations.
 *
 * IMPROVEMENTS:
 * - Prepared statements prevent SQL injection
 * - Connection pooling for performance
 * - Async operations don't block main thread
 * - Proper error handling and logging
 * - Transaction support where needed
 */
public class MySQLPlayerDataRepository implements PlayerDataRepository {

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    private static final String TABLE_NAME = "player_data";

    // SQL queries as constants (easier to maintain)
    private static final String SELECT_BY_UUID =
            "SELECT uuid, username, first_join, last_join, playtime_seconds " +
                    "FROM " + TABLE_NAME + " WHERE uuid = ?";

    private static final String SELECT_BY_USERNAME =
            "SELECT uuid, username, first_join, last_join, playtime_seconds " +
                    "FROM " + TABLE_NAME + " WHERE username = ? COLLATE utf8mb4_unicode_ci";

    private static final String INSERT_OR_UPDATE =
            "INSERT INTO " + TABLE_NAME + " " +
                    "(uuid, username, first_join, last_join, playtime_seconds) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), " +
                    "last_join = VALUES(last_join), " +
                    "playtime_seconds = VALUES(playtime_seconds), " +
                    "updated_at = CURRENT_TIMESTAMP";

    private static final String CHECK_EXISTS =
            "SELECT 1 FROM " + TABLE_NAME + " WHERE uuid = ? LIMIT 1";

    public MySQLPlayerDataRepository(
            @NotNull HikariDataSource dataSource,
            @NotNull Executor executor,
            @NotNull Logger logger
    ) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUuid(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_UUID)) {

                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to find player by UUID: " + uuid, e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUsername(@NotNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_BY_USERNAME)) {

                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapResultSet(rs));
                    }
                    return Optional.empty();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to find player by username: " + username, e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_OR_UPDATE)) {

                stmt.setString(1, data.getUuid().toString());
                stmt.setString(2, data.getUsername());
                stmt.setTimestamp(3, Timestamp.from(data.getFirstJoin()));
                stmt.setTimestamp(4, Timestamp.from(data.getLastJoin()));
                stmt.setLong(5, data.getPlaytimeSeconds());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save player data: " + data.getUsername(), e);
                throw new RuntimeException("Failed to save player data", e);
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(CHECK_EXISTS)) {

                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to check player existence: " + uuid, e);
                return false;
            }
        }, executor);
    }

    /**
     * Batch save multiple players (more efficient).
     *
     * @param players Multiple player data to save
     * @return CompletableFuture that completes when all are saved
     */
    @NotNull
    public CompletableFuture<Void> saveBatch(@NotNull Iterable<PlayerData> players) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false); // Start transaction

                try (PreparedStatement stmt = conn.prepareStatement(INSERT_OR_UPDATE)) {

                    int batchSize = 0;
                    for (PlayerData data : players) {
                        stmt.setString(1, data.getUuid().toString());
                        stmt.setString(2, data.getUsername());
                        stmt.setTimestamp(3, Timestamp.from(data.getFirstJoin()));
                        stmt.setTimestamp(4, Timestamp.from(data.getLastJoin()));
                        stmt.setLong(5, data.getPlaytimeSeconds());

                        stmt.addBatch();
                        batchSize++;

                        // Execute batch every 100 records
                        if (batchSize % 100 == 0) {
                            stmt.executeBatch();
                        }
                    }

                    // Execute remaining
                    if (batchSize % 100 != 0) {
                        stmt.executeBatch();
                    }

                    conn.commit(); // Commit transaction
                    logger.info("Batch saved " + batchSize + " player records");

                } catch (SQLException e) {
                    conn.rollback(); // Rollback on error
                    throw e;
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to batch save player data", e);
                throw new RuntimeException("Failed to batch save player data", e);
            }
        }, executor);
    }

    /**
     * Update only playtime (more efficient than full save).
     *
     * @param uuid Player UUID
     * @param additionalSeconds Seconds to add to playtime
     * @return CompletableFuture that completes when updated
     */
    @NotNull
    public CompletableFuture<Void> updatePlaytime(@NotNull UUID uuid, long additionalSeconds) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE " + TABLE_NAME + " " +
                    "SET playtime_seconds = playtime_seconds + ?, " +
                    "updated_at = CURRENT_TIMESTAMP " +
                    "WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, additionalSeconds);
                stmt.setString(2, uuid.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to update playtime for: " + uuid, e);
                throw new RuntimeException("Failed to update playtime", e);
            }
        }, executor);
    }

    /**
     * Maps a ResultSet row to a PlayerData object.
     *
     * @param rs ResultSet positioned at a row
     * @return PlayerData object
     * @throws SQLException if mapping fails
     */
    private PlayerData mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String username = rs.getString("username");
        Instant firstJoin = rs.getTimestamp("first_join").toInstant();
        Instant lastJoin = rs.getTimestamp("last_join").toInstant();
        long playtimeSeconds = rs.getLong("playtime_seconds");

        return new PlayerData(uuid, username, firstJoin, lastJoin, playtimeSeconds);
    }

    /**
     * Test database connection.
     *
     * @return true if connection works
     */
    public boolean testConnection() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            return rs.next();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database connection test failed", e);
            return false;
        }
    }

    /**
     * Get connection pool statistics.
     */
    public String getPoolStats() {
        return String.format(
                "HikariCP Stats: Active=%d, Idle=%d, Total=%d, Waiting=%d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}