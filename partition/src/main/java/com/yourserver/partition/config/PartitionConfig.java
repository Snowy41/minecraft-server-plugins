package com.yourserver.partition.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Configuration loader for partition settings.
 */
public class PartitionConfig {

    private final Map<String, PartitionSettings> partitions;
    private final IsolationSettings isolationSettings;

    private PartitionConfig(Map<String, PartitionSettings> partitions, IsolationSettings isolationSettings) {
        this.partitions = partitions;
        this.isolationSettings = isolationSettings;
    }

    public static PartitionConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            Map<String, PartitionSettings> partitions = loadPartitions(root.node("partitions"));
            IsolationSettings isolationSettings = loadIsolationSettings(root.node("isolation"));

            return new PartitionConfig(partitions, isolationSettings);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private static Map<String, PartitionSettings> loadPartitions(CommentedConfigurationNode node) {
        Map<String, PartitionSettings> partitions = new LinkedHashMap<>();

        node.childrenMap().forEach((key, partitionNode) -> {
            String id = key.toString();

            String name = partitionNode.node("name").getString(id);
            String description = partitionNode.node("description").getString("");
            boolean enabled = partitionNode.node("enabled").getBoolean(true);

            List<String> worlds = new ArrayList<>();
            partitionNode.node("worlds").childrenList().forEach(w -> worlds.add(w.getString("")));

            List<String> plugins = new ArrayList<>();
            partitionNode.node("plugins").childrenList().forEach(p -> plugins.add(p.getString("")));

            String spawnWorld = partitionNode.node("spawn-world").getString(worlds.isEmpty() ? "world" : worlds.get(0));
            double spawnX = partitionNode.node("spawn-location", "x").getDouble(0.0);
            double spawnY = partitionNode.node("spawn-location", "y").getDouble(64.0);
            double spawnZ = partitionNode.node("spawn-location", "z").getDouble(0.0);
            float spawnYaw = (float) partitionNode.node("spawn-location", "yaw").getDouble(0.0);
            float spawnPitch = (float) partitionNode.node("spawn-location", "pitch").getDouble(0.0);

            boolean autoLoad = partitionNode.node("auto-load").getBoolean(true);
            boolean persistent = partitionNode.node("persistent").getBoolean(true);

            PartitionSettings settings = new PartitionSettings(
                    id, name, description, enabled, worlds, plugins,
                    spawnWorld, spawnX, spawnY, spawnZ, spawnYaw, spawnPitch,
                    autoLoad, persistent
            );

            partitions.put(id, settings);
        });

        return partitions;
    }

    private static IsolationSettings loadIsolationSettings(CommentedConfigurationNode node) {
        boolean chatIsolation = node.node("chat").getBoolean(true);
        boolean tablistIsolation = node.node("tablist").getBoolean(true);
        boolean commandIsolation = node.node("commands").getBoolean(false);
        boolean worldBorderIsolation = node.node("world-border").getBoolean(true);

        return new IsolationSettings(chatIsolation, tablistIsolation, commandIsolation, worldBorderIsolation);
    }

    public Map<String, PartitionSettings> getPartitions() {
        return new LinkedHashMap<>(partitions);
    }

    public PartitionSettings getPartition(String id) {
        return partitions.get(id);
    }

    public IsolationSettings getIsolationSettings() {
        return isolationSettings;
    }

    /**
     * Settings for a single partition.
     */
    public static class PartitionSettings {
        private final String id;
        private final String name;
        private final String description;
        private final boolean enabled;
        private final List<String> worlds;
        private final List<String> plugins;
        private final String spawnWorld;
        private final double spawnX;
        private final double spawnY;
        private final double spawnZ;
        private final float spawnYaw;
        private final float spawnPitch;
        private final boolean autoLoad;
        private final boolean persistent;

        public PartitionSettings(String id, String name, String description, boolean enabled,
                                 List<String> worlds, List<String> plugins,
                                 String spawnWorld, double spawnX, double spawnY, double spawnZ,
                                 float spawnYaw, float spawnPitch,
                                 boolean autoLoad, boolean persistent) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.enabled = enabled;
            this.worlds = new ArrayList<>(worlds);
            this.plugins = new ArrayList<>(plugins);
            this.spawnWorld = spawnWorld;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.spawnYaw = spawnYaw;
            this.spawnPitch = spawnPitch;
            this.autoLoad = autoLoad;
            this.persistent = persistent;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return enabled; }
        public List<String> getWorlds() { return new ArrayList<>(worlds); }
        public List<String> getPlugins() { return new ArrayList<>(plugins); }
        public String getSpawnWorld() { return spawnWorld; }
        public double getSpawnX() { return spawnX; }
        public double getSpawnY() { return spawnY; }
        public double getSpawnZ() { return spawnZ; }
        public float getSpawnYaw() { return spawnYaw; }
        public float getSpawnPitch() { return spawnPitch; }
        public boolean isAutoLoad() { return autoLoad; }
        public boolean isPersistent() { return persistent; }
    }

    /**
     * Isolation settings for player interactions.
     */
    public static class IsolationSettings {
        private final boolean chatIsolation;
        private final boolean tablistIsolation;
        private final boolean commandIsolation;
        private final boolean worldBorderIsolation;

        public IsolationSettings(boolean chatIsolation, boolean tablistIsolation,
                                 boolean commandIsolation, boolean worldBorderIsolation) {
            this.chatIsolation = chatIsolation;
            this.tablistIsolation = tablistIsolation;
            this.commandIsolation = commandIsolation;
            this.worldBorderIsolation = worldBorderIsolation;
        }

        public boolean isChatIsolation() { return chatIsolation; }
        public boolean isTablistIsolation() { return tablistIsolation; }
        public boolean isCommandIsolation() { return commandIsolation; }
        public boolean isWorldBorderIsolation() { return worldBorderIsolation; }
    }
}