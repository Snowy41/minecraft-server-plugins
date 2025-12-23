package com.yourserver.gamelobby.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Represents a generic game service running on CloudNet.
 *
 * Works for ANY gamemode:
 * - BattleRoyale-1, BattleRoyale-2
 * - SkyWars-1, SkyWars-2
 * - BedWars-1, BedWars-2
 * - etc.
 *
 * Tracks:
 * - Service name and gamemode type
 * - Game state (WAITING, STARTING, ACTIVE, etc.)
 * - Player counts (current, max, alive)
 * - Online status and heartbeat
 */
public class GameService {

    private final String serviceName;       // "BattleRoyale-1", "SkyWars-1", etc.
    private final String gamemodeId;        // "battleroyale", "skywars", etc.

    private GameState state;                // Current game state
    private int currentPlayers;             // Current player count
    private int maxPlayers;                 // Maximum player capacity
    private int alivePlayers;               // Players still alive (for elimination games)

    private boolean online;                 // Is service responding?
    private Instant lastUpdate;             // Last heartbeat/state update

    private String gameId;                  // Optional: specific game instance ID

    public GameService(@NotNull String serviceName, @NotNull String gamemodeId) {
        this.serviceName = serviceName;
        this.gamemodeId = gamemodeId;
        this.state = GameState.WAITING;
        this.currentPlayers = 0;
        this.maxPlayers = 0;
        this.alivePlayers = 0;
        this.online = true;
        this.lastUpdate = Instant.now();
        this.gameId = null;
    }

    // ===== UPDATE METHODS =====

    public void updateState(@NotNull GameState state) {
        this.state = state;
        this.lastUpdate = Instant.now();
    }

    public void updatePlayers(int current, int max) {
        this.currentPlayers = current;
        this.maxPlayers = max;
        this.lastUpdate = Instant.now();
    }

    public void updateAlive(int alive) {
        this.alivePlayers = alive;
        this.lastUpdate = Instant.now();
    }

    public void updateHeartbeat() {
        this.online = true;
        this.lastUpdate = Instant.now();
    }

    public void markOffline() {
        this.online = false;
    }

    /**
     * Checks if this service is stale (no updates recently).
     *
     * @param timeoutSeconds Seconds before considering stale
     * @return true if stale
     */
    public boolean isStale(long timeoutSeconds) {
        long secondsSinceUpdate = Instant.now().getEpochSecond() - lastUpdate.getEpochSecond();
        return secondsSinceUpdate > timeoutSeconds;
    }

    // ===== JOINABILITY CHECKS =====

    /**
     * Can players join this service?
     */
    public boolean isJoinable() {
        if (!online) return false;
        if (state != GameState.WAITING && state != GameState.STARTING) return false;
        if (maxPlayers > 0 && currentPlayers >= maxPlayers) return false;
        return true;
    }

    /**
     * Is this service full?
     */
    public boolean isFull() {
        return maxPlayers > 0 && currentPlayers >= maxPlayers;
    }

    /**
     * Is this service empty?
     */
    public boolean isEmpty() {
        return currentPlayers == 0;
    }

    // ===== GETTERS =====

    @NotNull
    public String getServiceName() {
        return serviceName;
    }

    @NotNull
    public String getGamemodeId() {
        return gamemodeId;
    }

    @NotNull
    public GameState getState() {
        return state;
    }

    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getAlivePlayers() {
        return alivePlayers;
    }

    public boolean isOnline() {
        return online;
    }

    @NotNull
    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public String getGameId() {
        return gameId;
    }

    // ===== SETTERS =====

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public void setState(@NotNull GameState state) {
        this.state = state;
    }

    public void setCurrentPlayers(int currentPlayers) {
        this.currentPlayers = currentPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public void setAlivePlayers(int alivePlayers) {
        this.alivePlayers = alivePlayers;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    @Override
    public String toString() {
        return "GameService{" +
                "serviceName='" + serviceName + '\'' +
                ", gamemodeId='" + gamemodeId + '\'' +
                ", state=" + state +
                ", players=" + currentPlayers + "/" + maxPlayers +
                ", alive=" + alivePlayers +
                ", online=" + online +
                '}';
    }

    // ===== ENUM: GAME STATE =====

    public enum GameState {
        WAITING("Waiting", "§aJoinable"),
        STARTING("Starting", "§eStarting Soon"),
        ACTIVE("Active", "§cIn Progress"),
        DEATHMATCH("Deathmatch", "§4Final Battle"),
        ENDING("Ending", "§7Finishing"),
        RESTARTING("Restarting", "§7Restarting"),
        UNKNOWN("Unknown", "§8Unknown");

        private final String displayName;
        private final String coloredName;

        GameState(String displayName, String coloredName) {
            this.displayName = displayName;
            this.coloredName = coloredName;
        }

        @NotNull
        public String getDisplayName() {
            return displayName;
        }

        @NotNull
        public String getColoredName() {
            return coloredName;
        }

        /**
         * Parse state from string (case-insensitive).
         */
        @NotNull
        public static GameState fromString(@NotNull String state) {
            try {
                return valueOf(state.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}