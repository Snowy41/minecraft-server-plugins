package com.yourserver.structure.model;

import com.yourserver.structure.api.PlacementOptions;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Represents a saved structure with metadata.
 * Can be in multiple formats: WorldEdit (.schem), NBT, or Legacy.
 */
public class Structure {

    private final String namespace;
    private final String id;
    private final String name;
    private final String description;
    private final String author;
    private final StructureFormat format;
    private final String filePath;

    private final Instant createdAt;
    private Instant modifiedAt;

    private final Dimensions dimensions;
    private final Set<String> tags;
    private final PlacementOptions defaultOptions;

    private int timesPlaced;
    private long averageLoadTime;

    private Structure(Builder builder) {
        this.namespace = builder.namespace;
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.author = builder.author;
        this.format = builder.format;
        this.filePath = builder.filePath;
        this.createdAt = builder.createdAt;
        this.modifiedAt = builder.modifiedAt;
        this.dimensions = builder.dimensions;
        this.tags = new HashSet<>(builder.tags);
        this.defaultOptions = builder.defaultOptions;
        this.timesPlaced = builder.timesPlaced;
        this.averageLoadTime = builder.averageLoadTime;
    }

    @NotNull
    public String getNamespace() { return namespace; }

    @NotNull
    public String getId() { return id; }

    @NotNull
    public String getFullId() { return namespace + ":" + id; }

    @NotNull
    public String getName() { return name; }

    @Nullable
    public String getDescription() { return description; }

    @Nullable
    public String getAuthor() { return author; }

    @NotNull
    public StructureFormat getFormat() { return format; }

    @NotNull
    public String getFilePath() { return filePath; }

    @NotNull
    public Instant getCreatedAt() { return createdAt; }

    @NotNull
    public Instant getModifiedAt() { return modifiedAt; }

    @NotNull
    public Dimensions getDimensions() { return dimensions; }

    @NotNull
    public Set<String> getTags() { return Collections.unmodifiableSet(tags); }

    @Nullable
    public PlacementOptions getDefaultOptions() { return defaultOptions; }

    public int getTimesPlaced() { return timesPlaced; }

    public long getAverageLoadTime() { return averageLoadTime; }

    public void incrementPlacementCount() {
        this.timesPlaced++;
    }

    public void updateLoadTime(long loadTimeMs) {
        if (timesPlaced == 1) {
            this.averageLoadTime = loadTimeMs;
        } else {
            this.averageLoadTime = (averageLoadTime * (timesPlaced - 1) + loadTimeMs) / timesPlaced;
        }
    }

    public void touch() {
        this.modifiedAt = Instant.now();
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String namespace = "default";
        private String id;
        private String name;
        private String description;
        private String author;
        private StructureFormat format = StructureFormat.WORLDEDIT;
        private String filePath;
        private Instant createdAt = Instant.now();
        private Instant modifiedAt = Instant.now();
        private Dimensions dimensions;
        private Set<String> tags = new HashSet<>();
        private PlacementOptions defaultOptions;
        private int timesPlaced = 0;
        private long averageLoadTime = 0;

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder format(StructureFormat format) {
            this.format = format;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder dimensions(int width, int height, int length) {
            this.dimensions = new Dimensions(width, height, length);
            return this;
        }

        public Builder dimensions(Dimensions dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = new HashSet<>(tags);
            return this;
        }

        public Builder addTag(String tag) {
            this.tags.add(tag.toLowerCase());
            return this;
        }

        public Builder defaultOptions(PlacementOptions options) {
            this.defaultOptions = options;
            return this;
        }

        public Structure build() {
            Objects.requireNonNull(id, "Structure ID cannot be null");
            Objects.requireNonNull(name, "Structure name cannot be null");
            Objects.requireNonNull(filePath, "File path cannot be null");

            return new Structure(this);
        }
    }

    public static class Dimensions {
        private final int width;
        private final int height;
        private final int length;

        public Dimensions(int width, int height, int length) {
            this.width = width;
            this.height = height;
            this.length = length;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getLength() { return length; }

        public int getVolume() {
            return width * height * length;
        }

        @Override
        public String toString() {
            return width + "x" + height + "x" + length;
        }
    }

    public enum StructureFormat {
        WORLDEDIT(".schem"),
        NBT(".nbt"),
        LEGACY(".structure");

        private final String extension;

        StructureFormat(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }
}