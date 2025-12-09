package com.yourserver.lobby.npc;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Represents a custom NPC with player skin.
 */
public class CustomNPC {

    private final String id;
    private final String name;
    private final Location location;
    private final String skinTexture;
    private final String skinSignature;
    private final NPCAction action;
    private final List<String> hologramLines;
    private UUID entityUUID; // The spawned entity's UUID

    public CustomNPC(
            @NotNull String id,
            @NotNull String name,
            @NotNull Location location,
            @Nullable String skinTexture,
            @Nullable String skinSignature,
            @NotNull NPCAction action,
            @NotNull List<String> hologramLines
    ) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.action = action;
        this.hologramLines = hologramLines;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location.clone();
    }

    public String getSkinTexture() {
        return skinTexture;
    }

    public String getSkinSignature() {
        return skinSignature;
    }

    public NPCAction getAction() {
        return action;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public void setEntityUUID(UUID entityUUID) {
        this.entityUUID = entityUUID;
    }

    public boolean hasSkin() {
        return skinTexture != null && !skinTexture.isEmpty();
    }

    /**
     * Represents an action that can be performed when interacting with the NPC.
     */
    public static class NPCAction {
        private final ActionType type;
        private final String data;

        public NPCAction(ActionType type, String data) {
            this.type = type;
            this.data = data;
        }

        public ActionType getType() {
            return type;
        }

        public String getData() {
            return data;
        }

        public enum ActionType {
            TELEPORT,        // Teleport to location (format: "world,x,y,z,yaw,pitch")
            COMMAND,         // Execute command as player
            SERVER,          // Send to server (if using BungeeCord/Velocity)
            GUI,             // Open custom GUI
            MESSAGE          // Send message to player
        }
    }
}