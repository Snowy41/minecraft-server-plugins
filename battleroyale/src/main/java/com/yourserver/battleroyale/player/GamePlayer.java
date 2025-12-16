package com.yourserver.battleroyale.player;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player in a battle royale game.
 * Tracks player state, statistics, and game-specific data.
 */
public class GamePlayer {

    private final UUID uuid;
    private final String name;
    private final Instant joinedAt;

    // Player state
    private PlayerState state;
    private boolean alive;

    // In-game statistics
    private int kills;
    private int assists;
    private double damageDealt;
    private double damageTaken;
    private long survivalTime; // milliseconds
    private int placement; // 1st, 2nd, 3rd, etc.

    // TODO: Team support
    // private Team team;

    public GamePlayer(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = Objects.requireNonNull(uuid);
        this.name = Objects.requireNonNull(name);
        this.joinedAt = Instant.now();
        this.state = PlayerState.WAITING;
        this.alive = true;
        this.kills = 0;
        this.assists = 0;
        this.damageDealt = 0.0;
        this.damageTaken = 0.0;
    }

    // ===== STATE MANAGEMENT =====

    public void setState(@NotNull PlayerState state) {
        this.state = Objects.requireNonNull(state);
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
        if (!alive) {
            this.state = PlayerState.SPECTATING;
            this.survivalTime = System.currentTimeMillis() - joinedAt.toEpochMilli();
        }
    }

    // ===== STATISTICS =====

    public void addKill() {
        this.kills++;
    }

    public void addAssist() {
        this.assists++;
    }

    public void addDamageDealt(double damage) {
        this.damageDealt += damage;
    }

    public void addDamageTaken(double damage) {
        this.damageTaken += damage;
    }

    public void setPlacement(int placement) {
        this.placement = placement;
    }

    // ===== GETTERS =====

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public Instant getJoinedAt() {
        return joinedAt;
    }

    @NotNull
    public PlayerState getState() {
        return state;
    }

    public boolean isAlive() {
        return alive;
    }

    public int getKills() {
        return kills;
    }

    public int getAssists() {
        return assists;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public long getSurvivalTime() {
        return survivalTime;
    }

    public int getPlacement() {
        return placement;
    }

    /**
     * Gets kill/death/assist summary for display.
     */
    @NotNull
    public String getKDAString() {
        return kills + "/" + (alive ? 0 : 1) + "/" + assists;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GamePlayer that = (GamePlayer) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "GamePlayer{" +
                "name='" + name + '\'' +
                ", state=" + state +
                ", alive=" + alive +
                ", kills=" + kills +
                ", placement=" + placement +
                '}';
    }
}