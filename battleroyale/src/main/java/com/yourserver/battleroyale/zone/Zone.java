package com.yourserver.battleroyale.zone;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a shrinking zone in the battle royale game.
 *
 * The zone defines the safe area where players can survive.
 * Players outside the zone take damage over time.
 */
public class Zone {

    private final World world;
    private Location center;
    private double currentRadius;
    private double targetRadius;
    private final ZonePhase phase;

    private long shrinkStartTime;
    private long shrinkDuration;
    private boolean shrinking;

    public Zone(@NotNull World world, @NotNull Location center,
                double initialRadius, @NotNull ZonePhase phase) {
        this.world = Objects.requireNonNull(world, "world cannot be null");
        this.center = Objects.requireNonNull(center, "center cannot be null");
        this.currentRadius = initialRadius;
        this.targetRadius = initialRadius;
        this.phase = Objects.requireNonNull(phase, "phase cannot be null");
        this.shrinking = false;
    }

    /**
     * Starts shrinking the zone to a new radius.
     *
     * @param newRadius The target radius to shrink to
     * @param durationSeconds How long the shrink should take
     */
    public void startShrink(double newRadius, long durationSeconds) {
        this.targetRadius = newRadius;
        this.shrinkStartTime = System.currentTimeMillis();
        this.shrinkDuration = durationSeconds * 1000L;
        this.shrinking = true;
    }

    /**
     * Updates the zone's current radius based on shrink progress.
     * Should be called each tick.
     */
    public void tick() {
        if (!shrinking) {
            return;
        }

        long elapsed = System.currentTimeMillis() - shrinkStartTime;

        if (elapsed >= shrinkDuration) {
            // Shrink complete
            currentRadius = targetRadius;
            shrinking = false;
            return;
        }

        // Linear interpolation for smooth shrinking
        double progress = (double) elapsed / shrinkDuration;
        double startRadius = currentRadius;
        currentRadius = startRadius - (startRadius - targetRadius) * progress;
    }

    /**
     * Checks if a location is inside the safe zone.
     *
     * @param location The location to check
     * @return true if inside the zone, false if outside
     */
    public boolean isInZone(@NotNull Location location) {
        if (!location.getWorld().equals(world)) {
            return false;
        }

        double distance = center.distance(location);
        return distance <= currentRadius;
    }

    /**
     * Gets the distance from a location to the zone edge.
     * Positive = inside zone, Negative = outside zone
     *
     * @param location The location to check
     * @return Distance in blocks
     */
    public double getDistanceToEdge(@NotNull Location location) {
        if (!location.getWorld().equals(world)) {
            return -1000; // Very far outside
        }

        double distance = center.distance(location);
        return currentRadius - distance;
    }

    /**
     * Checks if the zone has finished shrinking.
     */
    public boolean isShrinkComplete() {
        return !shrinking && Math.abs(currentRadius - targetRadius) < 0.1;
    }

    /**
     * Gets the shrink progress as a percentage (0.0 to 1.0).
     */
    public double getShrinkProgress() {
        if (!shrinking) {
            return 1.0;
        }

        long elapsed = System.currentTimeMillis() - shrinkStartTime;
        return Math.min(1.0, (double) elapsed / shrinkDuration);
    }

    /**
     * Gets the remaining shrink time in seconds.
     */
    public long getRemainingSeconds() {
        if (!shrinking) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - shrinkStartTime;
        long remaining = shrinkDuration - elapsed;
        return Math.max(0, remaining / 1000L);
    }

    // ===== GETTERS AND SETTERS =====

    @NotNull
    public World getWorld() {
        return world;
    }

    @NotNull
    public Location getCenter() {
        return center.clone();
    }

    public void setCenter(@NotNull Location newCenter) {
        this.center = Objects.requireNonNull(newCenter);
    }

    public double getCurrentRadius() {
        return currentRadius;
    }

    public double getTargetRadius() {
        return targetRadius;
    }

    @NotNull
    public ZonePhase getPhase() {
        return phase;
    }

    public boolean isShrinking() {
        return shrinking;
    }

    @Override
    public String toString() {
        return "Zone{" +
                "center=" + center +
                ", currentRadius=" + String.format("%.1f", currentRadius) +
                ", targetRadius=" + String.format("%.1f", targetRadius) +
                ", shrinking=" + shrinking +
                ", phase=" + phase.getId() +
                '}';
    }
}