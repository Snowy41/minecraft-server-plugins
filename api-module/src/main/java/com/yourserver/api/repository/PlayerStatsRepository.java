package com.yourserver.api.repository;

import com.yourserver.api.model.PlayerStats;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for player statistics persistence.
 * All operations are asynchronous to avoid blocking the server thread.
 */
public interface PlayerStatsRepository {

    /**
     * Finds statistics for a player.
     *
     * @param uuid The player's unique identifier
     * @return A CompletableFuture containing an Optional with the statistics, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<PlayerStats>> findByUuid(@NotNull UUID uuid);

    /**
     * Saves or updates player statistics.
     *
     * @param stats The statistics to save
     * @return A CompletableFuture that completes when the save operation finishes
     */
    @NotNull
    CompletableFuture<Void> save(@NotNull PlayerStats stats);

    /**
     * Updates only specific statistics fields.
     * More efficient than loading and saving entire stats object.
     *
     * @param uuid The player's unique identifier
     * @param gamesPlayed Number of games played to add
     * @param gamesWon Number of games won to add
     * @param kills Number of kills to add
     * @param deaths Number of deaths to add
     * @param damageDealt Damage dealt to add
     * @param damageTaken Damage taken to add
     * @return A CompletableFuture that completes when the update finishes
     */
    @NotNull
    CompletableFuture<Void> updateStats(
            @NotNull UUID uuid,
            int gamesPlayed,
            int gamesWon,
            int kills,
            int deaths,
            double damageDealt,
            double damageTaken
    );
}