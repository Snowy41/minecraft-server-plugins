package com.yourserver.battleroyale.zone;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a phase of zone shrinking with configuration.
 * Each phase defines timing, radius, and damage parameters.
 */
public class ZonePhase {

    private final int id;
    private final int waitDuration;      // seconds to wait before shrinking
    private final int shrinkDuration;    // seconds for the shrink to complete
    private final double targetRadius;   // final radius after shrink
    private final double damagePerTick;  // damage dealt outside zone per tick
    private final int tickInterval;      // ticks between damage applications

    public ZonePhase(int id, int waitDuration, int shrinkDuration,
                     double targetRadius, double damagePerTick, int tickInterval) {
        this.id = id;
        this.waitDuration = Math.max(0, waitDuration);
        this.shrinkDuration = Math.max(1, shrinkDuration);
        this.targetRadius = Math.max(0, targetRadius);
        this.damagePerTick = Math.max(0, damagePerTick);
        this.tickInterval = Math.max(1, tickInterval);
    }

    /**
     * Creates a default phase configuration.
     */
    @NotNull
    public static ZonePhase createDefault(int id) {
        return new ZonePhase(
                id,
                180,  // 3 minutes wait
                60,   // 1 minute shrink
                500,  // 500 block radius
                1.0,  // 1 HP per tick
                20    // Every second (20 ticks)
        );
    }

    /**
     * Gets the total duration of this phase in seconds (wait + shrink).
     */
    public int getTotalDuration() {
        return waitDuration + shrinkDuration;
    }

    /**
     * Checks if this is the final phase (radius very small).
     */
    public boolean isFinalPhase() {
        return targetRadius <= 20.0;
    }

    // ===== GETTERS =====

    public int getId() {
        return id;
    }

    public int getWaitDuration() {
        return waitDuration;
    }

    public int getShrinkDuration() {
        return shrinkDuration;
    }

    public double getTargetRadius() {
        return targetRadius;
    }

    public double getDamagePerTick() {
        return damagePerTick;
    }

    public int getTickInterval() {
        return tickInterval;
    }

    @Override
    public String toString() {
        return "ZonePhase{" +
                "id=" + id +
                ", wait=" + waitDuration + "s" +
                ", shrink=" + shrinkDuration + "s" +
                ", radius=" + targetRadius +
                ", damage=" + damagePerTick +
                '}';
    }

    // ===== BUILDER =====

    public static class Builder {
        private int id;
        private int waitDuration = 180;
        private int shrinkDuration = 60;
        private double targetRadius = 500;
        private double damagePerTick = 1.0;
        private int tickInterval = 20;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder waitDuration(int waitDuration) {
            this.waitDuration = waitDuration;
            return this;
        }

        public Builder shrinkDuration(int shrinkDuration) {
            this.shrinkDuration = shrinkDuration;
            return this;
        }

        public Builder targetRadius(double targetRadius) {
            this.targetRadius = targetRadius;
            return this;
        }

        public Builder damagePerTick(double damagePerTick) {
            this.damagePerTick = damagePerTick;
            return this;
        }

        public Builder tickInterval(int tickInterval) {
            this.tickInterval = tickInterval;
            return this;
        }

        public ZonePhase build() {
            return new ZonePhase(id, waitDuration, shrinkDuration,
                    targetRadius, damagePerTick, tickInterval);
        }
    }
}