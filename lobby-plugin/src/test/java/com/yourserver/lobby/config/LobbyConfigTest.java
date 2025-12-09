package com.yourserver.lobby.config;

import org.bukkit.Particle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LobbyConfig.
 */
class LobbyConfigTest {

    @Test
    void load_withValidConfig_loadsSuccessfully(@TempDir Path tempDir) throws IOException {
        // Create a minimal valid config
        String configContent = """
            spawn:
              world: "world"
              x: 0.5
              y: 65.0
              z: 0.5
              yaw: 0.0
              pitch: 0.0
            
            protection:
              op-bypass: true
              block-break: true
              block-place: true
              item-drop: true
              item-pickup: true
              player-damage: true
              fall-damage: true
              fire-damage: true
              drowning-damage: true
              void-damage: false
              pvp: true
              hunger: true
              weather-clear: true
              void-teleport: true
              void-y-level: -64
              regions:
                spawn:
                  enabled: true
                  min:
                    x: -50
                    y: 0
                    z: -50
                  max:
                    x: 50
                    y: 256
                    z: 50
            
            scoreboard:
              enabled: true
              update-interval: 10
              title: "Test Server"
              lines:
                - "Line 1"
                - "Line 2"
            
            tablist:
              enabled: true
              update-interval: 20
              header:
                - "Header"
              footer:
                - "Footer"
            
            gui:
              enabled: true
              give-compass: true
            
            join-items:
              enabled: true
              clear-inventory: true
              items:
                - slot: 0
                  material: COMPASS
                  name: "Test Item"
                  lore:
                    - "Test Lore"
            
            cosmetics:
              enabled: true
              trails:
                enabled: true
                spawn-rate: 1
                available:
                  flame:
                    name: "Flame Trail"
                    particle: FLAME
                    permission: "test.trail.flame"
                    vip: false
            
            messages:
              prefix: "[Lobby] "
              spawn-teleport: "Teleported!"
              spawn-set: "Spawn set!"
              spawn-not-set: "Spawn not set!"
              config-reload: "Reloaded!"
              no-permission: "No permission!"
              cosmetic-equipped: "Equipped!"
              cosmetic-removed: "Removed!"
              cosmetic-locked: "Locked!"
              void-teleport: "Void!"
            """;

        File configFile = tempDir.resolve("config.yml").toFile();
        Files.writeString(configFile.toPath(), configContent);

        // Load config
        LobbyConfig config = LobbyConfig.load(tempDir.toFile());

        // Verify basic values
        assertNotNull(config);
        assertNotNull(config.getSpawnLocation());
        assertEquals("world", config.getSpawnLocation().getWorldName());
        assertTrue(config.getScoreboardConfig().isEnabled());
        assertTrue(config.getTabListConfig().isEnabled());
    }

    @Test
    void protectionConfig_containsExpectedSettings(@TempDir Path tempDir) throws IOException {
        String configContent = """
            spawn:
              world: "world"
              x: 0.0
              y: 64.0
              z: 0.0
              yaw: 0.0
              pitch: 0.0
            
            protection:
              op-bypass: true
              block-break: true
              block-place: false
              item-drop: true
              item-pickup: false
              player-damage: true
              fall-damage: false
              fire-damage: true
              drowning-damage: false
              void-damage: false
              pvp: true
              hunger: false
              weather-clear: true
              void-teleport: true
              void-y-level: -100
              regions:
                spawn:
                  enabled: false
            
            scoreboard:
              enabled: false
              update-interval: 10
              title: "Test"
              lines: []
            
            tablist:
              enabled: false
              update-interval: 10
              header: []
              footer: []
            
            gui:
              enabled: false
              give-compass: false
            
            join-items:
              enabled: false
              clear-inventory: false
              items: []
            
            cosmetics:
              enabled: false
              trails:
                enabled: false
                spawn-rate: 1
                available: {}
            
            messages:
              prefix: ""
              spawn-teleport: ""
              spawn-set: ""
              spawn-not-set: ""
              config-reload: ""
              no-permission: ""
              cosmetic-equipped: ""
              cosmetic-removed: ""
              cosmetic-locked: ""
              void-teleport: ""
            """;

        File configFile = tempDir.resolve("config.yml").toFile();
        Files.writeString(configFile.toPath(), configContent);

        LobbyConfig config = LobbyConfig.load(tempDir.toFile());
        LobbyConfig.ProtectionConfig protection = config.getProtectionConfig();

        assertTrue(protection.isOpBypass());
        assertTrue(protection.isBlockBreak());
        assertFalse(protection.isBlockPlace());
        assertTrue(protection.isItemDrop());
        assertFalse(protection.isItemPickup());
        assertTrue(protection.isPlayerDamage());
        assertFalse(protection.isFallDamage());
        assertEquals(-100, protection.getVoidYLevel());
    }

    @Test
    void region_containsPoint_checksCorrectly() {
        LobbyConfig.Region region = new LobbyConfig.Region(-10, 0, -10, 10, 100, 10);

        assertTrue(region.contains(0, 50, 0));
        assertTrue(region.contains(-10, 0, -10));
        assertTrue(region.contains(10, 100, 10));
        assertFalse(region.contains(11, 50, 0));
        assertFalse(region.contains(0, -1, 0));
    }

    @Test
    void cosmeticsConfig_loadsTrailsCorrectly(@TempDir Path tempDir) throws IOException {
        String configContent = """
            spawn:
              world: "world"
              x: 0.0
              y: 64.0
              z: 0.0
              yaw: 0.0
              pitch: 0.0
            
            protection:
              op-bypass: true
              block-break: true
              block-place: true
              item-drop: true
              item-pickup: true
              player-damage: true
              fall-damage: true
              fire-damage: true
              drowning-damage: true
              void-damage: false
              pvp: true
              hunger: true
              weather-clear: true
              void-teleport: false
              void-y-level: -64
            
            scoreboard:
              enabled: false
              update-interval: 10
              title: "Test"
              lines: []
            
            tablist:
              enabled: false
              update-interval: 10
              header: []
              footer: []
            
            gui:
              enabled: false
              give-compass: false
            
            join-items:
              enabled: false
              clear-inventory: false
              items: []
            
            cosmetics:
              enabled: true
              trails:
                enabled: true
                spawn-rate: 2
                available:
                  flame:
                    name: "Flame"
                    particle: FLAME
                    permission: "test.flame"
                    vip: false
                  heart:
                    name: "Heart"
                    particle: HEART
                    permission: "test.heart"
                    vip: true
            
            messages:
              prefix: ""
              spawn-teleport: ""
              spawn-set: ""
              spawn-not-set: ""
              config-reload: ""
              no-permission: ""
              cosmetic-equipped: ""
              cosmetic-removed: ""
              cosmetic-locked: ""
              void-teleport: ""
            """;

        File configFile = tempDir.resolve("config.yml").toFile();
        Files.writeString(configFile.toPath(), configContent);

        LobbyConfig config = LobbyConfig.load(tempDir.toFile());
        LobbyConfig.CosmeticsConfig cosmetics = config.getCosmeticsConfig();

        assertTrue(cosmetics.isEnabled());
        assertTrue(cosmetics.isTrailsEnabled());
        assertEquals(2, cosmetics.getSpawnRate());
        assertEquals(2, cosmetics.getTrails().size());

        LobbyConfig.TrailConfig flameTrail = cosmetics.getTrails().get("flame");
        assertNotNull(flameTrail);
        assertEquals("Flame", flameTrail.getName());
        assertEquals(Particle.FLAME, flameTrail.getParticle());
        assertFalse(flameTrail.isVip());

        LobbyConfig.TrailConfig heartTrail = cosmetics.getTrails().get("heart");
        assertNotNull(heartTrail);
        assertTrue(heartTrail.isVip());
    }
}