package com.yourserver.battleroyale.game;

import org.jetbrains.annotations.NotNull;

/**
 * Configuration for a single game instance.
 * Defines rules, limits, and settings for the game.
 */
public class GameConfig {

    private final int minPlayers;
    private final int maxPlayers;
    private final long gameDurationMilliSeconds;
    private final int worldSize; // blocks (radius)
    private final int pregameLobbyHeight; // Y level
    private final boolean teamsEnabled;
    private final int teamSize;
    private final int zonePhases;
    private final int zoneGracePeriodSeconds;
    private final boolean deathmatchEnabled;
    private final int deathmatchTimeLimitSeconds;

    private GameConfig(Builder builder) {
        this.minPlayers = builder.minPlayers;
        this.maxPlayers = builder.maxPlayers;
        this.gameDurationMilliSeconds = builder.gameDuration;
        this.worldSize = builder.worldSize;
        this.pregameLobbyHeight = builder.pregameLobbyHeight;
        this.teamsEnabled = builder.teamsEnabled;
        this.teamSize = builder.teamSize;
        this.zonePhases = builder.zonePhases;
        this.zoneGracePeriodSeconds = builder.zoneGracePeriod;
        this.deathmatchEnabled = builder.deathmatchEnabled;
        this.deathmatchTimeLimitSeconds = builder.deathmatchTimeLimit;
    }

    /**
     * Creates a default configuration.
     */
    @NotNull
    public static GameConfig createDefault() {
        return new Builder()
                .minPlayers(25)
                .maxPlayers(100)
                .gameDuration(3600000L) // 1 hour
                .worldSize(2000)
                .pregameLobbyHeight(320)
                .teamsEnabled(false)
                .teamSize(1)
                .zonePhases(8)
                .zoneGracePeriod(120) // 2 minutes
                .deathmatchEnabled(true)
                .deathmatchTimeLimit(3600) // 1 hour
                .build();
    }

    // ===== GETTERS =====

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public long getGameDuration() {
        return gameDurationMilliSeconds;
    }

    public int getWorldSize() {
        return worldSize;
    }

    public int getPregameLobbyHeight() {
        return pregameLobbyHeight;
    }

    public boolean isTeamsEnabled() {
        return teamsEnabled;
    }

    public int getTeamSize() {
        return teamSize;
    }

    public int getZonePhases() {
        return zonePhases;
    }

    public int getZoneGracePeriod() {
        return zoneGracePeriodSeconds;
    }

    public boolean isDeathmatchEnabled() {
        return deathmatchEnabled;
    }

    public int getDeathmatchTimeLimit() {
        return deathmatchTimeLimitSeconds;
    }

    // ===== BUILDER =====

    public static class Builder {
        private int minPlayers = 25;
        private int maxPlayers = 100;
        private long gameDuration = 3600000L;
        private int worldSize = 2000;
        private int pregameLobbyHeight = 320;
        private boolean teamsEnabled = false;
        private int teamSize = 1;
        private int zonePhases = 8;
        private int zoneGracePeriod = 120;
        private boolean deathmatchEnabled = true;
        private int deathmatchTimeLimit = 3600;

        public Builder minPlayers(int minPlayers) {
            this.minPlayers = minPlayers;
            return this;
        }

        public Builder maxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
            return this;
        }

        public Builder gameDuration(long gameDuration) {
            this.gameDuration = gameDuration;
            return this;
        }

        public Builder worldSize(int worldSize) {
            this.worldSize = worldSize;
            return this;
        }

        public Builder pregameLobbyHeight(int pregameLobbyHeight) {
            this.pregameLobbyHeight = pregameLobbyHeight;
            return this;
        }

        public Builder teamsEnabled(boolean teamsEnabled) {
            this.teamsEnabled = teamsEnabled;
            return this;
        }

        public Builder teamSize(int teamSize) {
            this.teamSize = teamSize;
            return this;
        }

        public Builder zonePhases(int zonePhases) {
            this.zonePhases = zonePhases;
            return this;
        }

        public Builder zoneGracePeriod(int zoneGracePeriod) {
            this.zoneGracePeriod = zoneGracePeriod;
            return this;
        }

        public Builder deathmatchEnabled(boolean deathmatchEnabled) {
            this.deathmatchEnabled = deathmatchEnabled;
            return this;
        }

        public Builder deathmatchTimeLimit(int deathmatchTimeLimit) {
            this.deathmatchTimeLimit = deathmatchTimeLimit;
            return this;
        }

        public GameConfig build() {
            return new GameConfig(this);
        }
    }
}