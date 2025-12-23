package com.yourserver.battleroyale.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;

/**
 * Configuration loader for BattleRoyale plugin.
 * Loads settings from config.yml
 */
public class BattleRoyaleConfig {

    private final int minPlayers;
    private final int maxPlayers;
    private final int countdownSeconds;
    private final int pregameLobbyHeight;
    private final int worldSize;
    private final int zonePhases;
    private final int zoneGracePeriod;
    private final boolean deathmatchEnabled;
    private final int deathmatchTimeLimit;
    private final int deathmatchArenaSize;
    private final boolean teamsEnabled;

    private BattleRoyaleConfig(int minPlayers, int maxPlayers, int countdownSeconds,
                               int pregameLobbyHeight, int worldSize, int zonePhases,
                               int zoneGracePeriod, boolean deathmatchEnabled,
                               int deathmatchTimeLimit, int deathmatchArenaSize,
                               boolean teamsEnabled) {
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.countdownSeconds = countdownSeconds;
        this.pregameLobbyHeight = pregameLobbyHeight;
        this.worldSize = worldSize;
        this.zonePhases = zonePhases;
        this.zoneGracePeriod = zoneGracePeriod;
        this.deathmatchEnabled = deathmatchEnabled;
        this.deathmatchTimeLimit = deathmatchTimeLimit;
        this.deathmatchArenaSize = deathmatchArenaSize;
        this.teamsEnabled = teamsEnabled;
    }

    /**
     * Loads configuration from config.yml
     */
    public static BattleRoyaleConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            CommentedConfigurationNode game = root.node("game");
            int minPlayers = game.node("min-players").getInt(25);
            int maxPlayers = game.node("max-players").getInt(100);
            int countdownSeconds = game.node("countdown-seconds").getInt(30);
            int pregameLobbyHeight = game.node("pregame-lobby", "height").getInt(320);

            CommentedConfigurationNode world = root.node("world");
            int worldSize = world.node("size").getInt(2000);

            CommentedConfigurationNode zone = root.node("zone");
            int zonePhases = zone.node("phases").getInt(8);
            int zoneGracePeriod = zone.node("grace-period").getInt(120);

            CommentedConfigurationNode deathmatch = game.node("deathmatch");
            boolean deathmatchEnabled = deathmatch.node("enabled").getBoolean(true);
            int deathmatchTimeLimit = deathmatch.node("time-limit").getInt(3600);
            int deathmatchArenaSize = deathmatch.node("arena-size").getInt(50);

            boolean teamsEnabled = root.node("teams", "enabled").getBoolean(false);

            return new BattleRoyaleConfig(
                    minPlayers, maxPlayers, countdownSeconds, pregameLobbyHeight,
                    worldSize, zonePhases, zoneGracePeriod, deathmatchEnabled,
                    deathmatchTimeLimit, deathmatchArenaSize, teamsEnabled
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    // ===== GETTERS =====

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public int getPregameLobbyHeight() {
        return pregameLobbyHeight;
    }

    public int getWorldSize() {
        return worldSize;
    }

    public int getZonePhaseCount() {
        return zonePhases;
    }

    public int getZoneGracePeriod() {
        return zoneGracePeriod;
    }

    public boolean isDeathmatchEnabled() {
        return deathmatchEnabled;
    }

    public int getDeathmatchTimeLimit() {
        return deathmatchTimeLimit;
    }

    public int getDeathmatchArenaSize() {
        return deathmatchArenaSize;
    }

    public boolean isTeamsEnabled() {
        return teamsEnabled;
    }
}