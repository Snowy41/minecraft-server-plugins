package com.yourserver.socialvel.model;

import java.util.UUID;

public class PlayerStatus {

    private final UUID uuid;
    private final String server;
    private final Status status;
    private final long timestamp;

    public PlayerStatus(UUID uuid, String server, Status status, long timestamp) {
        this.uuid = uuid;
        this.server = server;
        this.status = status;
        this.timestamp = timestamp;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getServer() {
        return server;
    }

    public Status getStatus() {
        return status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public enum Status {
        ONLINE,
        AWAY,
        DND,
        OFFLINE
    }
}