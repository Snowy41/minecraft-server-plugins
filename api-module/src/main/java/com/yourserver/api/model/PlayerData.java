package com.yourserver.api.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable player data model representing persistent player information.
 * Thread-safe due to immutability - can be safely cached and shared.
 */
public final class PlayerData {
    private final UUID uuid;
    private final String username;
    private final Instant firstJoin;
    private final Instant lastJoin;
    private final long playtimeSeconds;

    /**
     * Creates a new PlayerData instance.
     *
     * @param uuid The player's unique identifier
     * @param username The player's current username
     * @param firstJoin The timestamp of first join
     * @param lastJoin The timestamp of last join
     * @param playtimeSeconds Total playtime in seconds
     */
    public PlayerData(
            @NotNull UUID uuid,
            @NotNull String username,
            @NotNull Instant firstJoin,
            @NotNull Instant lastJoin,
            long playtimeSeconds
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.username = Objects.requireNonNull(username, "username cannot be null");
        this.firstJoin = Objects.requireNonNull(firstJoin, "firstJoin cannot be null");
        this.lastJoin = Objects.requireNonNull(lastJoin, "lastJoin cannot be null");
        this.playtimeSeconds = Math.max(0, playtimeSeconds);
    }

    /**
     * Creates default player data for a new player.
     *
     * @param uuid The player's unique identifier
     * @param username The player's username
     * @return A new PlayerData instance with default values
     */
    public static PlayerData createDefault(@NotNull UUID uuid, @NotNull String username) {
        Instant now = Instant.now();
        return new PlayerData(uuid, username, now, now, 0L);
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    @NotNull
    public Instant getFirstJoin() {
        return firstJoin;
    }

    @NotNull
    public Instant getLastJoin() {
        return lastJoin;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    /**
     * Creates a new PlayerData with updated username.
     * Original instance remains unchanged.
     *
     * @param newUsername The new username
     * @return A new PlayerData instance
     */
    public PlayerData withUsername(@NotNull String newUsername) {
        return new PlayerData(uuid, newUsername, firstJoin, lastJoin, playtimeSeconds);
    }

    /**
     * Creates a new PlayerData with updated last join timestamp.
     *
     * @param newLastJoin The new last join timestamp
     * @return A new PlayerData instance
     */
    public PlayerData withLastJoin(@NotNull Instant newLastJoin) {
        return new PlayerData(uuid, username, firstJoin, newLastJoin, playtimeSeconds);
    }

    /**
     * Creates a new PlayerData with updated playtime.
     *
     * @param newPlaytimeSeconds The new playtime in seconds
     * @return A new PlayerData instance
     */
    public PlayerData withPlaytime(long newPlaytimeSeconds) {
        return new PlayerData(uuid, username, firstJoin, lastJoin, newPlaytimeSeconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerData that = (PlayerData) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", firstJoin=" + firstJoin +
                ", lastJoin=" + lastJoin +
                ", playtimeSeconds=" + playtimeSeconds +
                '}';
    }
}