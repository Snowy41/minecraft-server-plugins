package com.yourserver.npc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Enhanced NPC model with proper pose support.
 *
 * Features:
 * - Full body pose (head, body, arms, legs)
 * - Second skin layer (hat, jacket, etc.)
 * - Persistent storage
 * - Editor mode for live pose adjustments
 */
public class NPC {

    private final String id;
    private final String name;
    private final UUID uuid;
    private final int entityId;
    private Location location;
    private final Action action;
    private final List<String> hologramLines;

    private String skinTexture;
    private String skinSignature;
    private List<Integer> hologramEntityIds;

    private NPCEquipment equipment;
    // Pose data (in degrees)
    private NPCPose pose;

    public NPC(@NotNull String id, @NotNull String name, @NotNull Location location, @NotNull Action action) {
        this.id = id;
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.entityId = generateEntityId();
        this.location = location;
        this.action = action;
        this.hologramLines = new ArrayList<>();
        this.hologramEntityIds = new ArrayList<>();
        this.pose = new NPCPose();
        this.equipment = new NPCEquipment();
    }

    // Full constructor for loading from storage
    public NPC(@NotNull String id, @NotNull String name, @NotNull UUID uuid, int entityId,
               @NotNull Location location, @Nullable String skinTexture, @Nullable String skinSignature,
               @NotNull Action action, @NotNull List<String> hologramLines, @NotNull NPCPose pose,
               @Nullable NPCEquipment equipment) {
        this.id = id;
        this.name = name;
        this.uuid = uuid;
        this.entityId = entityId;
        this.location = location;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.action = action;
        this.hologramLines = new ArrayList<>(hologramLines);
        this.hologramEntityIds = new ArrayList<>();
        this.pose = pose;
        this.equipment = equipment != null ? equipment : new NPCEquipment(); // NEW!
    }

    public void setSkin(@Nullable String texture, @Nullable String signature) {
        this.skinTexture = texture;
        this.skinSignature = signature;
    }

    public void setLocation(@NotNull Location location) {
        this.location = location;
    }

    public void setHologramEntityIds(@NotNull List<Integer> ids) {
        this.hologramEntityIds = new ArrayList<>(ids);
    }

    public void addHologramLine(@NotNull String line) {
        hologramLines.add(line);
    }

    public void clearHologramLines() {
        hologramLines.clear();
    }

    public boolean hasSkin() {
        return skinTexture != null && !skinTexture.isEmpty();
    }



    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public int getEntityId() { return entityId; }
    public Location getLocation() { return location.clone(); }
    public Action getAction() { return action; }
    public List<String> getHologramLines() { return new ArrayList<>(hologramLines); }
    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
    public List<Integer> getHologramEntityIds() { return new ArrayList<>(hologramEntityIds); }
    public NPCPose getPose() { return pose; }

    public void setPose(@NotNull NPCPose pose) {
        this.pose = pose;
    }

    public NPCEquipment getEquipment() {
        return equipment;
    }

    public void setEquipment(@NotNull NPCEquipment equipment) {
        this.equipment = equipment;
    }

    private static int generateEntityId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }

    /**
     * Represents the pose of an NPC.
     * All rotations in degrees (pitch, yaw, roll).
     */
    public static class NPCPose {
        // Head rotation
        private float headPitch;
        private float headYaw;
        private float headRoll;

        // Body rotation
        private float bodyPitch;
        private float bodyYaw;
        private float bodyRoll;

        // Right arm
        private float rightArmPitch;
        private float rightArmYaw;
        private float rightArmRoll;

        // Left arm
        private float leftArmPitch;
        private float leftArmYaw;
        private float leftArmRoll;

        // Right leg
        private float rightLegPitch;
        private float rightLegYaw;
        private float rightLegRoll;

        // Left leg
        private float leftLegPitch;
        private float leftLegYaw;
        private float leftLegRoll;

        // Second skin layer
        private boolean showSecondLayer;

        public NPCPose() {
            // Default T-pose
            this.headPitch = 0f;
            this.headYaw = 0f;
            this.headRoll = 0f;
            this.bodyPitch = 0f;
            this.bodyYaw = 0f;
            this.bodyRoll = 0f;
            this.rightArmPitch = 0f;
            this.rightArmYaw = 0f;
            this.rightArmRoll = 0f;
            this.leftArmPitch = 0f;
            this.leftArmYaw = 0f;
            this.leftArmRoll = 0f;
            this.rightLegPitch = 0f;
            this.rightLegYaw = 0f;
            this.rightLegRoll = 0f;
            this.leftLegPitch = 0f;
            this.leftLegYaw = 0f;
            this.leftLegRoll = 0f;
            this.showSecondLayer = true;
        }

        // Full constructor for loading from storage
        public NPCPose(float headPitch, float headYaw, float headRoll,
                       float bodyPitch, float bodyYaw, float bodyRoll,
                       float rightArmPitch, float rightArmYaw, float rightArmRoll,
                       float leftArmPitch, float leftArmYaw, float leftArmRoll,
                       float rightLegPitch, float rightLegYaw, float rightLegRoll,
                       float leftLegPitch, float leftLegYaw, float leftLegRoll,
                       boolean showSecondLayer) {
            this.headPitch = headPitch;
            this.headYaw = headYaw;
            this.headRoll = headRoll;
            this.bodyPitch = bodyPitch;
            this.bodyYaw = bodyYaw;
            this.bodyRoll = bodyRoll;
            this.rightArmPitch = rightArmPitch;
            this.rightArmYaw = rightArmYaw;
            this.rightArmRoll = rightArmRoll;
            this.leftArmPitch = leftArmPitch;
            this.leftArmYaw = leftArmYaw;
            this.leftArmRoll = leftArmRoll;
            this.rightLegPitch = rightLegPitch;
            this.rightLegYaw = rightLegYaw;
            this.rightLegRoll = rightLegRoll;
            this.leftLegPitch = leftLegPitch;
            this.leftLegYaw = leftLegYaw;
            this.leftLegRoll = leftLegRoll;
            this.showSecondLayer = showSecondLayer;
        }

        // Getters - ALL OF THEM
        public float getHeadPitch() { return headPitch; }
        public float getHeadYaw() { return headYaw; }
        public float getHeadRoll() { return headRoll; }

        public float getBodyPitch() { return bodyPitch; }
        public float getBodyYaw() { return bodyYaw; }
        public float getBodyRoll() { return bodyRoll; }

        public float getRightArmPitch() { return rightArmPitch; }
        public float getRightArmYaw() { return rightArmYaw; }
        public float getRightArmRoll() { return rightArmRoll; }
        public float getRightArmX() { return rightArmPitch; } // Alias for compatibility
        public float getRightArmY() { return rightArmYaw; }   // Alias for compatibility
        public float getRightArmZ() { return rightArmRoll; }  // Alias for compatibility

        public float getLeftArmPitch() { return leftArmPitch; }
        public float getLeftArmYaw() { return leftArmYaw; }
        public float getLeftArmRoll() { return leftArmRoll; }
        public float getLeftArmX() { return leftArmPitch; } // Alias for compatibility
        public float getLeftArmY() { return leftArmYaw; }   // Alias for compatibility
        public float getLeftArmZ() { return leftArmRoll; }  // Alias for compatibility

        public float getRightLegPitch() { return rightLegPitch; }
        public float getRightLegYaw() { return rightLegYaw; }
        public float getRightLegRoll() { return rightLegRoll; }
        public float getRightLegX() { return rightLegPitch; } // Alias for compatibility

        public float getLeftLegPitch() { return leftLegPitch; }
        public float getLeftLegYaw() { return leftLegYaw; }
        public float getLeftLegRoll() { return leftLegRoll; }
        public float getLeftLegX() { return leftLegPitch; } // Alias for compatibility

        public boolean isShowSecondLayer() { return showSecondLayer; }

        // Setters - ALL OF THEM
        public void setHeadPitch(float headPitch) { this.headPitch = clamp(headPitch); }
        public void setHeadYaw(float headYaw) { this.headYaw = clamp(headYaw); }
        public void setHeadRoll(float headRoll) { this.headRoll = clamp(headRoll); }
        public void setHeadX(float value) { this.headPitch = clamp(value); } // Alias
        public void setHeadY(float value) { this.headYaw = clamp(value); }   // Alias
        public void setHeadZ(float value) { this.headRoll = clamp(value); }  // Alias

        public void setBodyPitch(float bodyPitch) { this.bodyPitch = clamp(bodyPitch); }
        public void setBodyYaw(float bodyYaw) { this.bodyYaw = clamp(bodyYaw); }
        public void setBodyRoll(float bodyRoll) { this.bodyRoll = clamp(bodyRoll); }
        public void setBodyY(float value) { this.bodyYaw = clamp(value); } // Alias

        public void setRightArmPitch(float rightArmPitch) { this.rightArmPitch = clamp(rightArmPitch); }
        public void setRightArmYaw(float rightArmYaw) { this.rightArmYaw = clamp(rightArmYaw); }
        public void setRightArmRoll(float rightArmRoll) { this.rightArmRoll = clamp(rightArmRoll); }
        public void setRightArmX(float value) { this.rightArmPitch = clamp(value); } // Alias
        public void setRightArmY(float value) { this.rightArmYaw = clamp(value); }   // Alias
        public void setRightArmZ(float value) { this.rightArmRoll = clamp(value); }  // Alias

        public void setLeftArmPitch(float leftArmPitch) { this.leftArmPitch = clamp(leftArmPitch); }
        public void setLeftArmYaw(float leftArmYaw) { this.leftArmYaw = clamp(leftArmYaw); }
        public void setLeftArmRoll(float leftArmRoll) { this.leftArmRoll = clamp(leftArmRoll); }
        public void setLeftArmX(float value) { this.leftArmPitch = clamp(value); } // Alias
        public void setLeftArmY(float value) { this.leftArmYaw = clamp(value); }   // Alias
        public void setLeftArmZ(float value) { this.leftArmRoll = clamp(value); }  // Alias

        public void setRightLegPitch(float rightLegPitch) { this.rightLegPitch = clamp(rightLegPitch); }
        public void setRightLegYaw(float rightLegYaw) { this.rightLegYaw = clamp(rightLegYaw); }
        public void setRightLegRoll(float rightLegRoll) { this.rightLegRoll = clamp(rightLegRoll); }
        public void setRightLegX(float value) { this.rightLegPitch = clamp(value); } // Alias

        public void setLeftLegPitch(float leftLegPitch) { this.leftLegPitch = clamp(leftLegPitch); }
        public void setLeftLegYaw(float leftLegYaw) { this.leftLegYaw = clamp(leftLegYaw); }
        public void setLeftLegRoll(float leftLegRoll) { this.leftLegRoll = clamp(leftLegRoll); }
        public void setLeftLegX(float value) { this.leftLegPitch = clamp(value); } // Alias

        public void setShowSecondLayer(boolean showSecondLayer) { this.showSecondLayer = showSecondLayer; }

        /**
         * Clamps rotation values to reasonable limits (-180 to 180 degrees).
         */
        private float clamp(float value) {
            while (value > 180f) value -= 360f;
            while (value < -180f) value += 360f;
            return value;
        }

        /**
         * Predefined poses for quick setup.
         */
        public static NPCPose standing() {
            return new NPCPose(); // Default T-pose
        }

        public static NPCPose sitting() {
            NPCPose pose = new NPCPose();
            pose.rightLegPitch = -90f;
            pose.leftLegPitch = -90f;
            return pose;
        }

        public static NPCPose waving() {
            NPCPose pose = new NPCPose();
            pose.rightArmPitch = -110f;
            pose.rightArmYaw = -10f;
            return pose;
        }

        public static NPCPose pointing() {
            NPCPose pose = new NPCPose();
            pose.rightArmPitch = -90f;
            return pose;
        }

        public static NPCPose saluting() {
            NPCPose pose = new NPCPose();
            pose.rightArmPitch = -90f;
            pose.rightArmYaw = -45f;
            return pose;
        }

        public static NPCPose dabbing() {
            NPCPose pose = new NPCPose();
            pose.headYaw = -45f;
            pose.headPitch = 20f;
            pose.rightArmPitch = -110f;
            pose.rightArmYaw = -45f;
            pose.leftArmPitch = -20f;
            pose.leftArmYaw = 45f;
            return pose;
        }

        public NPCPose copy() {
            return new NPCPose(
                    headPitch, headYaw, headRoll,
                    bodyPitch, bodyYaw, bodyRoll,
                    rightArmPitch, rightArmYaw, rightArmRoll,
                    leftArmPitch, leftArmYaw, leftArmRoll,
                    rightLegPitch, rightLegYaw, rightLegRoll,
                    leftLegPitch, leftLegYaw, leftLegRoll,
                    showSecondLayer
            );
        }
    }

    /**
     * Represents an action that can be performed when clicking an NPC.
     */
    public static class Action {
        private final ActionType type;
        private final String data;
        private Consumer<Player> customHandler;

        public Action(@NotNull ActionType type, @NotNull String data) {
            this.type = type;
            this.data = data;
        }

        public Action(@NotNull Consumer<Player> customHandler) {
            this.type = ActionType.CUSTOM;
            this.data = "";
            this.customHandler = customHandler;
        }

        public void execute(@NotNull Plugin plugin, @NotNull Player player) {
            switch (type) {
                case TELEPORT -> handleTeleport(player);
                case COMMAND -> handleCommand(player);
                case GUI -> handleGUI(plugin, player);
                case MESSAGE -> handleMessage(player);
                case SERVER -> handleServer(player);
                case CUSTOM -> customHandler.accept(player);
            }
        }

        private void handleTeleport(Player player) {
            try {
                String[] parts = data.split(",");
                Location loc = new Location(
                        Bukkit.getWorld(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts.length > 4 ? Float.parseFloat(parts[4]) : 0f,
                        parts.length > 5 ? Float.parseFloat(parts[5]) : 0f
                );
                player.teleport(loc);
            } catch (Exception e) {
                player.sendMessage("§cInvalid teleport location!");
            }
        }

        private void handleCommand(Player player) {
            String command = data.replace("{player}", player.getName());
            player.performCommand(command);
        }

        private void handleGUI(Plugin plugin, Player player) {
            Plugin target = Bukkit.getPluginManager().getPlugin("LobbyPlugin");
            if (target != null) {
                try {
                    var method = target.getClass().getMethod("getGuiManager");
                    Object guiManager = method.invoke(target);

                    String guiType = data.toLowerCase();
                    String methodName = switch (guiType) {
                        case "game_selector", "games" -> "openGameSelector";
                        case "cosmetics" -> "openCosmetics";
                        case "stats" -> "openStats";
                        default -> null;
                    };

                    if (methodName != null) {
                        var openMethod = guiManager.getClass().getMethod(methodName, Player.class);
                        openMethod.invoke(guiManager, player);
                    }
                } catch (Exception e) {
                    player.sendMessage("§cFailed to open GUI!");
                }
            }
        }

        private void handleMessage(Player player) {
            player.sendMessage(data);
        }

        private void handleServer(Player player) {
            player.sendMessage("§eConnecting to " + data + "...");
        }

        public ActionType getType() { return type; }
        public String getData() { return data; }
    }

    public enum ActionType {
        TELEPORT,
        COMMAND,
        GUI,
        MESSAGE,
        SERVER,
        CUSTOM
    }
}