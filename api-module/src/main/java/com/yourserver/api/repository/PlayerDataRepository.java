package com.yourserver.api.repository;

import com.yourserver.api.model.PlayerData;
import com.yourserver.api.model.PlayerStats;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for player data persistence.
 * All operations are asynchronous to avoid blocking the server thread.
 */
public interface PlayerDataRepository {

    /**
     * Finds a player by their UUID.
     *
     * @param uuid The player's unique identifier
     * @return A CompletableFuture containing an Optional with the player data, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<PlayerData>> findByUuid(@NotNull UUID uuid);

    /**
     * Finds a player by their username (case-insensitive).
     *
     * @param username The player's username
     * @return A CompletableFuture containing an Optional with the player data, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<PlayerData>> findByUsername(@NotNull String username);

    /**
     * Saves or updates player data.
     *
     * @param data The player data to save
     * @return A CompletableFuture that completes when the save operation finishes
     */
    @NotNull
    CompletableFuture<Void> save(@NotNull PlayerData data);

    /**
     * Checks if a player exists in the database.
     *
     * @param uuid The player's unique identifier
     * @return A CompletableFuture containing true if the player exists, false otherwise
     */
    @NotNull
    CompletableFuture<Boolean> exists(@NotNull UUID uuid);
}