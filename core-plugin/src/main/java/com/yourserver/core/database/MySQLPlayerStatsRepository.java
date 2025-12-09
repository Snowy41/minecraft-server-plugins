package com.yourserver.core.database;

import com.yourserver.api.model.PlayerStats;
import com.yourserver.api.repository.PlayerStatsRepository;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation of PlayerStatsRepository.
 */
public class MySQLPlayerStatsRepository implements PlayerStatsRepository {

    private final DatabaseManager databaseManager;

    public MySQLPlayerStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerStats>> findByUuid(@NotNull UUID uuid) {
        String sql = "SELECT * FROM player_stats WHERE uuid = ?";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to map player stats", e);
            }
        }, uuid.toString());
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerStats stats) {
        String sql = """
            INSERT INTO player_stats 
                (uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                games_played = VALUES(games_played),
                games_won = VALUES(games_won),
                kills = VALUES(kills),
                deaths = VALUES(deaths),
                damage_dealt = VALUES(damage_dealt),
                damage_taken = VALUES(damage_taken)
            """;

        return databaseManager.executeUpdate(
                sql,
                stats.getUuid().toString(),
                stats.getGamesPlayed(),
                stats.getGamesWon(),
                stats.getKills(),
                stats.getDeaths(),
                stats.getDamageDealt(),
                stats.getDamageTaken()
        ).thenApply(result -> null);
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
        String sql = """
            INSERT INTO player_stats 
                (uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                games_played = games_played + VALUES(games_played),
                games_won = games_won + VALUES(games_won),
                kills = kills + VALUES(kills),
                deaths = deaths + VALUES(deaths),
                damage_dealt = damage_dealt + VALUES(damage_dealt),
                damage_taken = damage_taken + VALUES(damage_taken)
            """;

        return databaseManager.executeUpdate(
                sql,
                uuid.toString(),
                gamesPlayed,
                gamesWon,
                kills,
                deaths,
                damageDealt,
                damageTaken
        ).thenApply(result -> null);
    }

    /**
     * Maps a ResultSet row to a PlayerStats object.
     *
     * @param rs ResultSet positioned at a row
     * @return PlayerStats object
     * @throws SQLException if mapping fails
     */
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