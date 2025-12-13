package com.yourserver.social.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable friend relationship model.
 * Thread-safe due to immutability.
 */
public final class Friend {

    private final UUID playerUuid;
    private final UUID friendUuid;
    private final String friendName;
    private final Instant since;

    public Friend(@NotNull UUID playerUuid, @NotNull UUID friendUuid,
                  @NotNull String friendName, @NotNull Instant since) {
        this.playerUuid = Objects.requireNonNull(playerUuid);
        this.friendUuid = Objects.requireNonNull(friendUuid);
        this.friendName = Objects.requireNonNull(friendName);
        this.since = Objects.requireNonNull(since);
    }

    public static Friend create(@NotNull UUID playerUuid, @NotNull UUID friendUuid,
                                @NotNull String friendName) {
        return new Friend(playerUuid, friendUuid, friendName, Instant.now());
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public UUID getFriendUuid() {
        return friendUuid;
    }

    @NotNull
    public String getFriendName() {
        return friendName;
    }

    @NotNull
    public Instant getSince() {
        return since;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Friend friend = (Friend) o;
        return playerUuid.equals(friend.playerUuid) && friendUuid.equals(friend.friendUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerUuid, friendUuid);
    }

    @Override
    public String toString() {
        return "Friend{" +
                "player=" + playerUuid +
                ", friend=" + friendUuid +
                ", name='" + friendName + '\'' +
                ", since=" + since +
                '}';
    }
}