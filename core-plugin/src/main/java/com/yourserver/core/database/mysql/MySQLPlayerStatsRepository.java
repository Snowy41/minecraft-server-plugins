package com.yourserver.core.database.mysql;

import com.yourserver.api.model.PlayerStats;
import com.yourserver.api.repository.PlayerStatsRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL implementation of PlayerStatsRepository.
 */
public class MySQLPlayerStatsRepository implements PlayerStatsRepository {

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    private static final String TABLE_NAME = "player_stats";

    private static final String SELECT_BY_UUID =
            "SELECT uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken " +
                    "FROM " + TABLE_NAME + " WHERE uuid = ?";

    private static final String INSERT_OR_UPDATE =
            "INSERT INTO " + TABLE_NAME + " " +
                    "(uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "games_played = VALUES(games_played), " +
                    "games_won = VALUES(games_won), " +
                    "kills = VALUES(kills), " +
                    "deaths = VALUES(deaths), " +
                    "damage_dealt = VALUES(damage_dealt), " +
                    "damage_taken = VALUES(damage_taken), " +
                    "updated_at = CURRENT_TIMESTAMP";

    private static final String UPDATE_STATS =
            "INSERT INTO " + TABLE_NAME + " " +
                    "(uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "games_played = games_played + VALUES(games_played), " +
                    "games_won = games_won + VALUES(games_won), " +
                    "kills = kills + VALUES(kills), " +
                    "deaths = deaths + VALUES(deaths), " +
                    "damage_dealt = damage_dealt + VALUES(damage_dealt), " +
                    "damage_taken = damage_taken + VALUES(damage_taken), " +
                    "updated_at = CURRENT_TIMESTAMP";

    public MySQLPlayerStatsRepository(
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
    public CompletableFuture<Optional<PlayerStats>> findByUuid(@NotNull UUID uuid) {
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
                logger.log(Level.SEVERE, "Failed to find stats by UUID: " + uuid, e);
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_OR_UPDATE)) {

                stmt.setString(1, stats.getUuid().toString());
                stmt.setInt(2, stats.getGamesPlayed());
                stmt.setInt(3, stats.getGamesWon());
                stmt.setInt(4, stats.getKills());
                stmt.setInt(5, stats.getDeaths());
                stmt.setDouble(6, stats.getDamageDealt());
                stmt.setDouble(7, stats.getDamageTaken());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to save player stats: " + stats.getUuid(), e);
                throw new RuntimeException("Failed to save player stats", e);
            }
        }, executor);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> updateStats(
            @NotNull UUID uuid,
            int gamesPlayed,
            int gamesWon,
            int kills,
            int deaths,
            double damageDealt,
            double damageTaken
    ) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPDATE_STATS)) {

                stmt.setString(1, uuid.toString());
                stmt.setInt(2, gamesPlayed);
                stmt.setInt(3, gamesWon);
                stmt.setInt(4, kills);
                stmt.setInt(5, deaths);
                stmt.setDouble(6, damageDealt);
                stmt.setDouble(7, damageTaken);

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to update stats: " + uuid, e);
                throw new RuntimeException("Failed to update stats", e);
            }
        }, executor);
    }

    private PlayerStats mapResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        int gamesPlayed = rs.getInt("games_played");
        int gamesWon = rs.getInt("games_won");
        int kills = rs.getInt("kills");
        int deaths = rs.getInt("deaths");
        double damageDealt = rs.getDouble("damage_dealt");
        double damageTaken = rs.getDouble("damage_taken");

        return new PlayerStats(uuid, gamesPlayed, gamesWon, kills, deaths, damageDealt, damageTaken);
    }
}