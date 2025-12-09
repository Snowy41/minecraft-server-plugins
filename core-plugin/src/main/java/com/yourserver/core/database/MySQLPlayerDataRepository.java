package com.yourserver.core.database;

import com.yourserver.api.model.PlayerData;
import com.yourserver.api.repository.PlayerDataRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation of PlayerDataRepository.
 * All operations are async and use prepared statements for security.
 */
public class MySQLPlayerDataRepository implements PlayerDataRepository {

    private final DatabaseManager databaseManager;

    public MySQLPlayerDataRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUuid(@NotNull UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to map player data", e);
            }
        }, uuid.toString());
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUsername(@NotNull String username) {
        String sql = "SELECT * FROM players WHERE username = ? LIMIT 1";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to map player data", e);
            }
        }, username);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerData data) {
        String sql = """
            INSERT INTO players (uuid, username, first_join, last_join, playtime_seconds)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                last_join = VALUES(last_join),
                playtime_seconds = VALUES(playtime_seconds)
            """;

        return databaseManager.executeUpdate(
                sql,
                data.getUuid().toString(),
                data.getUsername(),
                Timestamp.from(data.getFirstJoin()),
                Timestamp.from(data.getLastJoin()),
                data.getPlaytimeSeconds()
        ).thenApply(result -> null);
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ? LIMIT 1";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                return rs.next();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check player existence", e);
            }
        }, uuid.toString());
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
}