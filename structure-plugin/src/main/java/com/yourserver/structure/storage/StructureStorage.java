package com.yourserver.structure.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yourserver.structure.StructurePlugin;
import com.yourserver.structure.model.Structure;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages structure file storage and metadata.
 */
public class StructureStorage {

    private final StructurePlugin plugin;
    private final File structuresDir;
    private final File metadataDir;
    private final Gson gson;

    public StructureStorage(@NotNull StructurePlugin plugin) {
        this.plugin = plugin;
        this.structuresDir = new File(plugin.getDataFolder(), "structures");
        this.metadataDir = new File(plugin.getDataFolder(), "metadata");

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    public void initialize() {
        // Create directories
        if (!structuresDir.exists()) {
            structuresDir.mkdirs();
        }
        if (!metadataDir.exists()) {
            metadataDir.mkdirs();
        }

        // Create subdirectories for different formats
        new File(structuresDir, "schematics").mkdirs();
        new File(structuresDir, "nbt").mkdirs();
        new File(structuresDir, "legacy").mkdirs();

        plugin.getLogger().info("Structure storage initialized at: " + structuresDir.getAbsolutePath());
    }

    /**
     * Loads all structures from disk.
     */
    @NotNull
    public List<Structure> loadAllStructures() {
        List<Structure> structures = new ArrayList<>();

        File[] metadataFiles = metadataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (metadataFiles == null) {
            return structures;
        }

        for (File metadataFile : metadataFiles) {
            try {
                Structure structure = loadStructureMetadata(metadataFile);
                if (structure != null) {
                    structures.add(structure);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load structure metadata: " + metadataFile.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + structures.size() + " structure metadata files");
        return structures;
    }

    /**
     * Loads structure metadata from JSON file.
     */
    @NotNull
    private Structure loadStructureMetadata(@NotNull File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            StructureMetadataJson meta = gson.fromJson(reader, StructureMetadataJson.class);

            return Structure.builder()
                    .namespace(meta.namespace)
                    .id(meta.id)
                    .name(meta.name)
                    .description(meta.description)
                    .author(meta.author)
                    .format(Structure.StructureFormat.valueOf(meta.format.toUpperCase()))
                    .filePath(meta.file)
                    .dimensions(meta.dimensions.width, meta.dimensions.height, meta.dimensions.length)
                    .tags(new HashSet<>(meta.tags))
                    .build();
        }
    }

    /**
     * Saves structure metadata to JSON file.
     */
    public void saveStructureMetadata(@NotNull Structure structure) throws IOException {
        String filename = structure.getNamespace() + "_" + structure.getId() + ".json";
        File metadataFile = new File(metadataDir, filename);

        StructureMetadataJson meta = new StructureMetadataJson();
        meta.namespace = structure.getNamespace();
        meta.id = structure.getId();
        meta.name = structure.getName();
        meta.description = structure.getDescription();
        meta.author = structure.getAuthor();
        meta.format = structure.getFormat().name().toLowerCase();
        meta.file = structure.getFilePath();
        meta.created = structure.getCreatedAt();
        meta.modified = structure.getModifiedAt();
        meta.dimensions = new DimensionsJson();
        meta.dimensions.width = structure.getDimensions().getWidth();
        meta.dimensions.height = structure.getDimensions().getHeight();
        meta.dimensions.length = structure.getDimensions().getLength();
        meta.tags = new ArrayList<>(structure.getTags());

        try (Writer writer = new FileWriter(metadataFile)) {
            gson.toJson(meta, writer);
        }

        plugin.getLogger().info("Saved metadata for structure: " + structure.getFullId());
    }

    /**
     * Gets the file path for a structure based on format.
     */
    @NotNull
    public File getStructureFile(@NotNull String namespace, @NotNull String id,
                                 @NotNull Structure.StructureFormat format) {
        String subdir = switch (format) {
            case WORLDEDIT -> "schematics";
            case NBT -> "nbt";
            case LEGACY -> "legacy";
        };

        String filename = namespace + "_" + id + format.getExtension();
        return new File(new File(structuresDir, subdir), filename);
    }

    public void shutdown() {
        plugin.getLogger().info("Structure storage shut down");
    }

    // JSON models for Gson
    private static class StructureMetadataJson {
        String namespace;
        String id;
        String name;
        String description;
        String author;
        String format;
        String file;
        Instant created;
        Instant modified;
        DimensionsJson dimensions;
        List<String> tags;
    }

    private static class DimensionsJson {
        int width;
        int height;
        int length;
    }

    // Instant adapter for Gson
    private static class InstantTypeAdapter extends com.google.gson.TypeAdapter<Instant> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}