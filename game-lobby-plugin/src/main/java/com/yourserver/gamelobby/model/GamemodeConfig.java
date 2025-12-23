package com.yourserver.gamelobby.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for a gamemode.
 * Defines how a gamemode appears in the GUI and how it behaves.
 *
 * Examples:
 * - BattleRoyale
 * - SkyWars
 * - BedWars
 * - Duels
 */
public class GamemodeConfig {

    private final String id;                    // "battleroyale", "skywars", etc.
    private final String displayName;            // "⚔ BattleRoyale"
    private final String servicePrefix;          // "BattleRoyale-" (CloudNet service name prefix)
    private final Material iconMaterial;         // GUI icon material
    private final List<String> description;      // GUI description/lore
    private final boolean enabled;               // Is this gamemode enabled?

    // Redis channels
    private final String stateChannel;           // "br:state", "sw:state", etc.
    private final String heartbeatChannel;       // "br:heartbeat", "sw:heartbeat", etc.
    private final String controlChannel;         // "br:control", "sw:control", etc.

    private GamemodeConfig(Builder builder) {
        this.id = Objects.requireNonNull(builder.id);
        this.displayName = Objects.requireNonNull(builder.displayName);
        this.servicePrefix = Objects.requireNonNull(builder.servicePrefix);
        this.iconMaterial = Objects.requireNonNull(builder.iconMaterial);
        this.description = new ArrayList<>(builder.description);
        this.enabled = builder.enabled;
        this.stateChannel = builder.stateChannel != null ? builder.stateChannel : id + ":state";
        this.heartbeatChannel = builder.heartbeatChannel != null ? builder.heartbeatChannel : id + ":heartbeat";
        this.controlChannel = builder.controlChannel != null ? builder.controlChannel : id + ":control";
    }

    // ===== GETTERS =====

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public String getServicePrefix() {
        return servicePrefix;
    }

    @NotNull
    public Material getIconMaterial() {
        return iconMaterial;
    }

    @NotNull
    public List<String> getDescription() {
        return new ArrayList<>(description);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NotNull
    public String getStateChannel() {
        return stateChannel;
    }

    @NotNull
    public String getHeartbeatChannel() {
        return heartbeatChannel;
    }

    @NotNull
    public String getControlChannel() {
        return controlChannel;
    }

    /**
     * Checks if a service name belongs to this gamemode.
     *
     * @param serviceName CloudNet service name (e.g., "BattleRoyale-1")
     * @return true if matches this gamemode
     */
    public boolean matchesService(@NotNull String serviceName) {
        return serviceName.startsWith(servicePrefix);
    }

    @Override
    public String toString() {
        return "GamemodeConfig{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", servicePrefix='" + servicePrefix + '\'' +
                ", enabled=" + enabled +
                '}';
    }

    // ===== BUILDER =====

    public static class Builder {
        private String id;
        private String displayName;
        private String servicePrefix;
        private Material iconMaterial = Material.DIAMOND_SWORD;
        private List<String> description = new ArrayList<>();
        private boolean enabled = true;
        private String stateChannel;
        private String heartbeatChannel;
        private String controlChannel;

        public Builder id(@NotNull String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(@NotNull String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder servicePrefix(@NotNull String servicePrefix) {
            this.servicePrefix = servicePrefix;
            return this;
        }

        public Builder iconMaterial(@NotNull Material iconMaterial) {
            this.iconMaterial = iconMaterial;
            return this;
        }

        public Builder description(@NotNull List<String> description) {
            this.description = new ArrayList<>(description);
            return this;
        }

        public Builder addDescription(@NotNull String line) {
            this.description.add(line);
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder stateChannel(@NotNull String stateChannel) {
            this.stateChannel = stateChannel;
            return this;
        }

        public Builder heartbeatChannel(@NotNull String heartbeatChannel) {
            this.heartbeatChannel = heartbeatChannel;
            return this;
        }

        public Builder controlChannel(@NotNull String controlChannel) {
            this.controlChannel = controlChannel;
            return this;
        }

        public GamemodeConfig build() {
            return new GamemodeConfig(this);
        }
    }
}