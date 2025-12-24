package com.yourserver.structure.api;

import org.jetbrains.annotations.NotNull;

/**
 * Options for structure placement.
 */
public class PlacementOptions {

    private final Rotation rotation;
    private final boolean mirror;
    private final boolean adaptToTerrain;
    private final boolean surfaceDetection;
    private final boolean replaceTerrain;
    private final boolean includeEntities;
    private final boolean updatePhysics;
    private final boolean async;
    private final int yOffset;

    private PlacementOptions(Builder builder) {
        this.rotation = builder.rotation;
        this.mirror = builder.mirror;
        this.adaptToTerrain = builder.adaptToTerrain;
        this.surfaceDetection = builder.surfaceDetection;
        this.replaceTerrain = builder.replaceTerrain;
        this.includeEntities = builder.includeEntities;
        this.updatePhysics = builder.updatePhysics;
        this.async = builder.async;
        this.yOffset = builder.yOffset;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Rotation getRotation() { return rotation; }
    public boolean isMirror() { return mirror; }
    public boolean isAdaptToTerrain() { return adaptToTerrain; }
    public boolean isSurfaceDetection() { return surfaceDetection; }
    public boolean isReplaceTerrain() { return replaceTerrain; }
    public boolean isIncludeEntities() { return includeEntities; }
    public boolean isUpdatePhysics() { return updatePhysics; }
    public boolean isAsync() { return async; }
    public int getYOffset() { return yOffset; }

    public static class Builder {
        private Rotation rotation = Rotation.NONE;
        private boolean mirror = false;
        private boolean adaptToTerrain = false;
        private boolean surfaceDetection = false;
        private boolean replaceTerrain = true;
        private boolean includeEntities = true;
        private boolean updatePhysics = false;
        private boolean async = true;
        private int yOffset = 0;

        public Builder rotation(Rotation rotation) {
            this.rotation = rotation;
            return this;
        }

        public Builder mirror(boolean mirror) {
            this.mirror = mirror;
            return this;
        }

        public Builder adaptToTerrain(boolean adaptToTerrain) {
            this.adaptToTerrain = adaptToTerrain;
            return this;
        }

        public Builder surfaceDetection(boolean surfaceDetection) {
            this.surfaceDetection = surfaceDetection;
            return this;
        }

        public Builder replaceTerrain(boolean replaceTerrain) {
            this.replaceTerrain = replaceTerrain;
            return this;
        }

        public Builder includeEntities(boolean includeEntities) {
            this.includeEntities = includeEntities;
            return this;
        }

        public Builder updatePhysics(boolean updatePhysics) {
            this.updatePhysics = updatePhysics;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder yOffset(int yOffset) {
            this.yOffset = yOffset;
            return this;
        }

        public PlacementOptions build() {
            return new PlacementOptions(this);
        }
    }

    /**
     * Rotation enum.
     */
    public enum Rotation {
        NONE(0),
        CLOCKWISE_90(90),
        CLOCKWISE_180(180),
        CLOCKWISE_270(270);

        private final int degrees;

        Rotation(int degrees) {
            this.degrees = degrees;
        }

        public int getDegrees() {
            return degrees;
        }

        public static Rotation random() {
            Rotation[] values = values();
            return values[(int) (Math.random() * values.length)];
        }
    }
}
