package com.yourserver.social.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable friend request model.
 */
public final class FriendRequest {

    private final int id;
    private final UUID fromUuid;
    private final String fromName;
    private final UUID toUuid;
    private final Instant createdAt;
    private final Instant expiresAt;

    public FriendRequest(int id, @NotNull UUID fromUuid, @NotNull String fromName,
                         @NotNull UUID toUuid, @NotNull Instant createdAt, @NotNull Instant expiresAt) {
        this.id = id;
        this.fromUuid = Objects.requireNonNull(fromUuid);
        this.fromName = Objects.requireNonNull(fromName);
        this.toUuid = Objects.requireNonNull(toUuid);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public static FriendRequest create(@NotNull UUID fromUuid, @NotNull String fromName,
                                       @NotNull UUID toUuid, int expireSeconds) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(expireSeconds);
        return new FriendRequest(0, fromUuid, fromName, toUuid, now, expires);
    }

    public int getId() {
        return id;
    }

    @NotNull
    public UUID getFromUuid() {
        return fromUuid;
    }

    @NotNull
    public String getFromName() {
        return fromName;
    }

    @NotNull
    public UUID getToUuid() {
        return toUuid;
    }

    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @NotNull
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FriendRequest that = (FriendRequest) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FriendRequest{" +
                "from=" + fromUuid +
                ", to=" + toUuid +
                ", createdAt=" + createdAt +
                ", expired=" + isExpired() +
                '}';
    }
}