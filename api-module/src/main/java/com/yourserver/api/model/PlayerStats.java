package com.yourserver.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable player statistics model for battle royale game mode.
 * Thread-safe due to immutability.
 */
public final class PlayerStats {
    private final UUID uuid;
    private final int gamesPlayed;
    private final int gamesWon;
    private final int kills;
    private final int deaths;
    private final double damageDealt;
    private final double damageTaken;

    public PlayerStats(
            @NotNull UUID uuid,
            int gamesPlayed,
            int gamesWon,
            int kills,
            int deaths,
            double damageDealt,
            double damageTaken
    ) {
        this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
        this.gamesPlayed = Math.max(0, gamesPlayed);
        this.gamesWon = Math.max(0, gamesWon);
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.damageDealt = Math.max(0.0, damageDealt);
        this.damageTaken = Math.max(0.0, damageTaken);
    }

    /**
     * Creates default statistics for a new player.
     *
     * @param uuid The player's unique identifier
     * @return A new PlayerStats instance with zero values
     */
    public static PlayerStats createDefault(@NotNull UUID uuid) {
        return new PlayerStats(uuid, 0, 0, 0, 0, 0.0, 0.0);
    }

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    public int getGamesPlayed() {
        return gamesPlayed;
    }

    public int getGamesWon() {
        return gamesWon;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    /**
     * Calculates kill/death ratio.
     * Returns kills if no deaths, or the actual ratio.
     *
     * @return The K/D ratio
     */
    public double getKDRatio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    /**
     * Calculates win rate as a percentage.
     *
     * @return Win rate from 0.0 to 100.0
     */
    public double getWinRate() {
        return gamesPlayed == 0 ? 0.0 : (double) gamesWon / gamesPlayed * 100.0;
    }

    /**
     * Creates a new PlayerStats with incremented games played.
     *
     * @return A new PlayerStats instance
     */
    public PlayerStats withGamePlayed() {
        return new PlayerStats(uuid, gamesPlayed + 1, gamesWon, kills, deaths, damageDealt, damageTaken);
    }

    /**
     * Creates a new PlayerStats with incremented games won.
     *
     * @return A new PlayerStats instance
     */
    public PlayerStats withGameWon() {
        return new PlayerStats(uuid, gamesPlayed, gamesWon + 1, kills, deaths, damageDealt, damageTaken);
    }

    /**
     * Creates a new PlayerStats with added kills.
     *
     * @param additionalKills Number of kills to add
     * @return A new PlayerStats instance
     */
    public PlayerStats withKills(int additionalKills) {
        return new PlayerStats(uuid, gamesPlayed, gamesWon, kills + additionalKills, deaths, damageDealt, damageTaken);
    }

    /**
     * Creates a new PlayerStats with incremented deaths.
     *
     * @return A new PlayerStats instance
     */
    public PlayerStats withDeath() {
        return new PlayerStats(uuid, gamesPlayed, gamesWon, kills, deaths + 1, damageDealt, damageTaken);
    }

    /**
     * Creates a new PlayerStats with added damage dealt.
     *
     * @param damage Amount of damage to add
     * @return A new PlayerStats instance
     */
    public PlayerStats withDamageDealt(double damage) {
        return new PlayerStats(uuid, gamesPlayed, gamesWon, kills, deaths, damageDealt + damage, damageTaken);
    }

    /**
     * Creates a new PlayerStats with added damage taken.
     *
     * @param damage Amount of damage to add
     * @return A new PlayerStats instance
     */
    public PlayerStats withDamageTaken(double damage) {
        return new PlayerStats(uuid, gamesPlayed, gamesWon, kills, deaths, damageDealt, damageTaken + damage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerStats that = (PlayerStats) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerStats{" +
                "uuid=" + uuid +
                ", gamesPlayed=" + gamesPlayed +
                ", gamesWon=" + gamesWon +
                ", kills=" + kills +
                ", deaths=" + deaths +
                ", kd=" + String.format("%.2f", getKDRatio()) +
                ", winRate=" + String.format("%.1f%%", getWinRate()) +
                '}';
    }
}