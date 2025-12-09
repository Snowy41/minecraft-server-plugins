package com.yourserver.lobby.config;

import org.bukkit.Particle;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration loader for lobby settings.
 * Parses config.yml and provides typed configuration objects.
 */
public class LobbyConfig {

    private final SpawnLocation spawnLocation;
    private final ProtectionConfig protectionConfig;
    private final ScoreboardConfig scoreboardConfig;
    private final TabListConfig tabListConfig;
    private final GUIConfig guiConfig;
    private final JoinItemsConfig joinItemsConfig;
    private final CosmeticsConfig cosmeticsConfig;
    private final MessagesConfig messagesConfig;

    private LobbyConfig(
            SpawnLocation spawnLocation,
            ProtectionConfig protectionConfig,
            ScoreboardConfig scoreboardConfig,
            TabListConfig tabListConfig,
            GUIConfig guiConfig,
            JoinItemsConfig joinItemsConfig,
            CosmeticsConfig cosmeticsConfig,
            MessagesConfig messagesConfig
    ) {
        this.spawnLocation = spawnLocation;
        this.protectionConfig = protectionConfig;
        this.scoreboardConfig = scoreboardConfig;
        this.tabListConfig = tabListConfig;
        this.guiConfig = guiConfig;
        this.joinItemsConfig = joinItemsConfig;
        this.cosmeticsConfig = cosmeticsConfig;
        this.messagesConfig = messagesConfig;
    }

    public static LobbyConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            SpawnLocation spawn = loadSpawn(root.node("spawn"));
            ProtectionConfig protection = loadProtection(root.node("protection"));
            ScoreboardConfig scoreboard = loadScoreboard(root.node("scoreboard"));
            TabListConfig tabList = loadTabList(root.node("tablist"));
            GUIConfig gui = loadGUI(root.node("gui"));
            JoinItemsConfig joinItems = loadJoinItems(root.node("join-items"));
            CosmeticsConfig cosmetics = loadCosmetics(root.node("cosmetics"));
            MessagesConfig messages = loadMessages(root.node("messages"));

            return new LobbyConfig(spawn, protection, scoreboard, tabList, gui,
                    joinItems, cosmetics, messages);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private static SpawnLocation loadSpawn(CommentedConfigurationNode node) {
        return new SpawnLocation(
                node.node("world").getString("world"),
                node.node("x").getDouble(-174.5),
                node.node("y").getDouble(-45.0),
                node.node("z").getDouble(-13.5),
                node.node("yaw").getFloat(0.0f),
                node.node("pitch").getFloat(0.0f)
        );
    }

    private static ProtectionConfig loadProtection(CommentedConfigurationNode node) {
        Region spawnRegion = null;
        CommentedConfigurationNode regionNode = node.node("regions", "spawn");
        if (regionNode.node("enabled").getBoolean(true)) {
            spawnRegion = new Region(
                    regionNode.node("min", "x").getInt(-326),
                    regionNode.node("min", "y").getInt(-60),
                    regionNode.node("min", "z").getInt(-157),
                    regionNode.node("max", "x").getInt(-23),
                    regionNode.node("max", "y").getInt(-30),
                    regionNode.node("max", "z").getInt(130)
            );
        }

        return new ProtectionConfig(
                node.node("op-bypass").getBoolean(true),
                node.node("block-break").getBoolean(true),
                node.node("block-place").getBoolean(true),
                node.node("item-drop").getBoolean(true),
                node.node("item-pickup").getBoolean(true),
                node.node("player-damage").getBoolean(true),
                node.node("fall-damage").getBoolean(true),
                node.node("fire-damage").getBoolean(true),
                node.node("drowning-damage").getBoolean(true),
                node.node("void-damage").getBoolean(false),
                node.node("pvp").getBoolean(true),
                node.node("hunger").getBoolean(true),
                node.node("weather-clear").getBoolean(true),
                node.node("void-teleport").getBoolean(true),
                node.node("void-y-level").getInt(-64),
                spawnRegion
        );
    }

    private static ScoreboardConfig loadScoreboard(CommentedConfigurationNode node) {
        List<String> lines = new ArrayList<>();
        node.node("lines").childrenList().forEach(n -> lines.add(n.getString("")));

        return new ScoreboardConfig(
                node.node("enabled").getBoolean(true),
                node.node("update-interval").getInt(10),
                node.node("title").getString("<gradient:#FFD700:#FFA500><bold>YOUR SERVER</bold></gradient>"),
                lines
        );
    }

    private static TabListConfig loadTabList(CommentedConfigurationNode node) {
        List<String> header = new ArrayList<>();
        node.node("header").childrenList().forEach(n -> header.add(n.getString("")));

        List<String> footer = new ArrayList<>();
        node.node("footer").childrenList().forEach(n -> footer.add(n.getString("")));

        return new TabListConfig(
                node.node("enabled").getBoolean(true),
                node.node("update-interval").getInt(20),
                header,
                footer
        );
    }

    private static GUIConfig loadGUI(CommentedConfigurationNode node) {
        return new GUIConfig(
                node.node("enabled").getBoolean(true),
                node.node("give-compass").getBoolean(true)
        );
    }

    private static JoinItemsConfig loadJoinItems(CommentedConfigurationNode node) {
        List<JoinItem> items = new ArrayList<>();

        node.node("items").childrenList().forEach(itemNode -> {
            int slot = itemNode.node("slot").getInt(0);
            String material = itemNode.node("material").getString("STONE");
            String name = itemNode.node("name").getString("");

            List<String> lore = new ArrayList<>();
            itemNode.node("lore").childrenList().forEach(l -> lore.add(l.getString("")));

            items.add(new JoinItem(slot, material, name, lore));
        });

        return new JoinItemsConfig(
                node.node("enabled").getBoolean(true),
                node.node("clear-inventory").getBoolean(true),
                items
        );
    }

    private static CosmeticsConfig loadCosmetics(CommentedConfigurationNode node) {
        Map<String, TrailConfig> trails = new HashMap<>();

        CommentedConfigurationNode trailsNode = node.node("trails", "available");
        trailsNode.childrenMap().forEach((key, trailNode) -> {
            String id = key.toString();
            String name = trailNode.node("name").getString("");
            String particleStr = trailNode.node("particle").getString("FLAME");
            String permission = trailNode.node("permission").getString("");
            boolean vip = trailNode.node("vip").getBoolean(false);

            Particle particle;
            try {
                particle = Particle.valueOf(particleStr);
            } catch (IllegalArgumentException e) {
                particle = Particle.FLAME;
            }

            trails.put(id, new TrailConfig(name, particle, permission, vip));
        });

        return new CosmeticsConfig(
                node.node("enabled").getBoolean(true),
                node.node("trails", "enabled").getBoolean(true),
                node.node("trails", "spawn-rate").getInt(1),
                trails
        );
    }

    private static MessagesConfig loadMessages(CommentedConfigurationNode node) {
        return new MessagesConfig(
                node.node("prefix").getString("<gradient:#FFD700:#FFA500>[Lobby]</gradient> "),
                node.node("spawn-teleport").getString("<green>Teleported to spawn!"),
                node.node("spawn-set").getString("<green>Spawn location set!"),
                node.node("spawn-not-set").getString("<red>Spawn location not set!"),
                node.node("config-reload").getString("<green>Configuration reloaded!"),
                node.node("no-permission").getString("<red>You don't have permission!"),
                node.node("cosmetic-equipped").getString("<green>Equipped {cosmetic}!"),
                node.node("cosmetic-removed").getString("<gray>Cosmetic removed!"),
                node.node("cosmetic-locked").getString("<red>This cosmetic is locked!"),
                node.node("void-teleport").getString("<yellow>You fell into the void!")
        );
    }

    // Getters
    public SpawnLocation getSpawnLocation() { return spawnLocation; }
    public ProtectionConfig getProtectionConfig() { return protectionConfig; }
    public ScoreboardConfig getScoreboardConfig() { return scoreboardConfig; }
    public TabListConfig getTabListConfig() { return tabListConfig; }
    public GUIConfig getGuiConfig() { return guiConfig; }
    public JoinItemsConfig getJoinItemsConfig() { return joinItemsConfig; }
    public CosmeticsConfig getCosmeticsConfig() { return cosmeticsConfig; }
    public MessagesConfig getMessagesConfig() { return messagesConfig; }

    // Inner config classes
    public static class ProtectionConfig {
        private final boolean opBypass;
        private final boolean blockBreak;
        private final boolean blockPlace;
        private final boolean itemDrop;
        private final boolean itemPickup;
        private final boolean playerDamage;
        private final boolean fallDamage;
        private final boolean fireDamage;
        private final boolean drowningDamage;
        private final boolean voidDamage;
        private final boolean pvp;
        private final boolean hunger;
        private final boolean weatherClear;
        private final boolean voidTeleport;
        private final int voidYLevel;
        private final Region spawnRegion;

        public ProtectionConfig(boolean opBypass, boolean blockBreak, boolean blockPlace,
                                boolean itemDrop, boolean itemPickup, boolean playerDamage, boolean fallDamage,
                                boolean fireDamage, boolean drowningDamage, boolean voidDamage, boolean pvp,
                                boolean hunger, boolean weatherClear, boolean voidTeleport, int voidYLevel,
                                Region spawnRegion) {
            this.opBypass = opBypass;
            this.blockBreak = blockBreak;
            this.blockPlace = blockPlace;
            this.itemDrop = itemDrop;
            this.itemPickup = itemPickup;
            this.playerDamage = playerDamage;
            this.fallDamage = fallDamage;
            this.fireDamage = fireDamage;
            this.drowningDamage = drowningDamage;
            this.voidDamage = voidDamage;
            this.pvp = pvp;
            this.hunger = hunger;
            this.weatherClear = weatherClear;
            this.voidTeleport = voidTeleport;
            this.voidYLevel = voidYLevel;
            this.spawnRegion = spawnRegion;
        }

        public boolean isOpBypass() { return opBypass; }
        public boolean isBlockBreak() { return blockBreak; }
        public boolean isBlockPlace() { return blockPlace; }
        public boolean isItemDrop() { return itemDrop; }
        public boolean isItemPickup() { return itemPickup; }
        public boolean isPlayerDamage() { return playerDamage; }
        public boolean isFallDamage() { return fallDamage; }
        public boolean isFireDamage() { return fireDamage; }
        public boolean isDrowningDamage() { return drowningDamage; }
        public boolean isVoidDamage() { return voidDamage; }
        public boolean isPvp() { return pvp; }
        public boolean isHunger() { return hunger; }
        public boolean isWeatherClear() { return weatherClear; }
        public boolean isVoidTeleport() { return voidTeleport; }
        public int getVoidYLevel() { return voidYLevel; }
        public Region getSpawnRegion() { return spawnRegion; }
    }

    public static class Region {
        private final int minX, minY, minZ;
        private final int maxX, maxY, maxZ;

        public Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX &&
                    y >= minY && y <= maxY &&
                    z >= minZ && z <= maxZ;
        }

        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
    }

    public static class ScoreboardConfig {
        private final boolean enabled;
        private final int updateInterval;
        private final String title;
        private final List<String> lines;

        public ScoreboardConfig(boolean enabled, int updateInterval, String title, List<String> lines) {
            this.enabled = enabled;
            this.updateInterval = updateInterval;
            this.title = title;
            this.lines = lines;
        }

        public boolean isEnabled() { return enabled; }
        public int getUpdateInterval() { return updateInterval; }
        public String getTitle() { return title; }
        public List<String> getLines() { return lines; }
    }

    public static class TabListConfig {
        private final boolean enabled;
        private final int updateInterval;
        private final List<String> header;
        private final List<String> footer;

        public TabListConfig(boolean enabled, int updateInterval, List<String> header, List<String> footer) {
            this.enabled = enabled;
            this.updateInterval = updateInterval;
            this.header = header;
            this.footer = footer;
        }

        public boolean isEnabled() { return enabled; }
        public int getUpdateInterval() { return updateInterval; }
        public List<String> getHeader() { return header; }
        public List<String> getFooter() { return footer; }
    }

    public static class GUIConfig {
        private final boolean enabled;
        private final boolean giveCompass;

        public GUIConfig(boolean enabled, boolean giveCompass) {
            this.enabled = enabled;
            this.giveCompass = giveCompass;
        }

        public boolean isEnabled() { return enabled; }
        public boolean isGiveCompass() { return giveCompass; }
    }

    public static class JoinItemsConfig {
        private final boolean enabled;
        private final boolean clearInventory;
        private final List<JoinItem> items;

        public JoinItemsConfig(boolean enabled, boolean clearInventory, List<JoinItem> items) {
            this.enabled = enabled;
            this.clearInventory = clearInventory;
            this.items = items;
        }

        public boolean isEnabled() { return enabled; }
        public boolean isClearInventory() { return clearInventory; }
        public List<JoinItem> getItems() { return items; }
    }

    public static class JoinItem {
        private final int slot;
        private final String material;
        private final String name;
        private final List<String> lore;

        public JoinItem(int slot, String material, String name, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
        }

        public int getSlot() { return slot; }
        public String getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }

    public static class CosmeticsConfig {
        private final boolean enabled;
        private final boolean trailsEnabled;
        private final int spawnRate;
        private final Map<String, TrailConfig> trails;

        public CosmeticsConfig(boolean enabled, boolean trailsEnabled, int spawnRate, Map<String, TrailConfig> trails) {
            this.enabled = enabled;
            this.trailsEnabled = trailsEnabled;
            this.spawnRate = spawnRate;
            this.trails = trails;
        }

        public boolean isEnabled() { return enabled; }
        public boolean isTrailsEnabled() { return trailsEnabled; }
        public int getSpawnRate() { return spawnRate; }
        public Map<String, TrailConfig> getTrails() { return trails; }
    }

    public static class TrailConfig {
        private final String name;
        private final Particle particle;
        private final String permission;
        private final boolean vip;

        public TrailConfig(String name, Particle particle, String permission, boolean vip) {
            this.name = name;
            this.particle = particle;
            this.permission = permission;
            this.vip = vip;
        }

        public String getName() { return name; }
        public Particle getParticle() { return particle; }
        public String getPermission() { return permission; }
        public boolean isVip() { return vip; }
    }

    public static class MessagesConfig {
        private final String prefix;
        private final String spawnTeleport;
        private final String spawnSet;
        private final String spawnNotSet;
        private final String configReload;
        private final String noPermission;
        private final String cosmeticEquipped;
        private final String cosmeticRemoved;
        private final String cosmeticLocked;
        private final String voidTeleport;

        public MessagesConfig(String prefix, String spawnTeleport, String spawnSet,
                              String spawnNotSet, String configReload, String noPermission,
                              String cosmeticEquipped, String cosmeticRemoved, String cosmeticLocked,
                              String voidTeleport) {
            this.prefix = prefix;
            this.spawnTeleport = spawnTeleport;
            this.spawnSet = spawnSet;
            this.spawnNotSet = spawnNotSet;
            this.configReload = configReload;
            this.noPermission = noPermission;
            this.cosmeticEquipped = cosmeticEquipped;
            this.cosmeticRemoved = cosmeticRemoved;
            this.cosmeticLocked = cosmeticLocked;
            this.voidTeleport = voidTeleport;
        }

        public String getPrefix() { return prefix; }
        public String getSpawnTeleport() { return spawnTeleport; }
        public String getSpawnSet() { return spawnSet; }
        public String getSpawnNotSet() { return spawnNotSet; }
        public String getConfigReload() { return configReload; }
        public String getNoPermission() { return noPermission; }
        public String getCosmeticEquipped() { return cosmeticEquipped; }
        public String getCosmeticRemoved() { return cosmeticRemoved; }
        public String getCosmeticLocked() { return cosmeticLocked; }
        public String getVoidTeleport() { return voidTeleport; }
    }
}