package com.yourserver.npc.editor;

import com.yourserver.npc.model.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an active NPC editing session for a player.
 */
public class NPCEditorSession {

    private final Player editor;
    private final NPC npc;
    private EditMode currentMode;
    private float rotationStep = 5.0f; // Degrees per scroll

    public enum EditMode {
        HEAD_YAW("Head Yaw (Left/Right)", "Scroll to rotate head horizontally"),
        HEAD_PITCH("Head Pitch (Up/Down)", "Scroll to rotate head vertically"),
        BODY_YAW("Body Yaw (Twist)", "Scroll to twist body"),
        RIGHT_ARM_X("Right Arm X", "Scroll to move right arm forward/back"),
        RIGHT_ARM_Y("Right Arm Y", "Scroll to move right arm left/right"),
        RIGHT_ARM_Z("Right Arm Z", "Scroll to move right arm up/down"),
        LEFT_ARM_X("Left Arm X", "Scroll to move left arm forward/back"),
        LEFT_ARM_Y("Left Arm Y", "Scroll to move left arm left/right"),
        LEFT_ARM_Z("Left Arm Z", "Scroll to move left arm up/down"),
        RIGHT_LEG_X("Right Leg X", "Scroll to move right leg forward/back"),
        LEFT_LEG_X("Left Leg X", "Scroll to move left leg forward/back"),
        TOGGLE_LAYER("Toggle Skin Layer", "Click to toggle 3D layer on/off"),
        PRESET("Apply Preset", "Select pre-made pose");

        private final String displayName;
        private final String description;

        EditMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public NPCEditorSession(@NotNull Player editor, @NotNull NPC npc) {
        this.editor = editor;
        this.npc = npc;
        this.currentMode = EditMode.HEAD_YAW;
    }

    public Player getEditor() { return editor; }
    public NPC getNPC() { return npc; }
    public EditMode getCurrentMode() { return currentMode; }
    public float getRotationStep() { return rotationStep; }

    public void setCurrentMode(EditMode mode) {
        this.currentMode = mode;
        sendModeUpdate();
    }

    public void setRotationStep(float step) {
        this.rotationStep = Math.max(1.0f, Math.min(45.0f, step));
    }

    /**
     * Cycles to the next edit mode.
     */
    public void nextMode() {
        EditMode[] modes = EditMode.values();
        int currentIndex = currentMode.ordinal();
        currentMode = modes[(currentIndex + 1) % modes.length];
        sendModeUpdate();
    }

    /**
     * Cycles to the previous edit mode.
     */
    public void previousMode() {
        EditMode[] modes = EditMode.values();
        int currentIndex = currentMode.ordinal();
        currentMode = modes[(currentIndex - 1 + modes.length) % modes.length];
        sendModeUpdate();
    }

    /**
     * Handles scroll input (positive = scroll up, negative = scroll down).
     */
    public void handleScroll(int scrollAmount) {
        float change = scrollAmount * rotationStep;
        NPC.NPCPose pose = npc.getPose();

        switch (currentMode) {
            case HEAD_YAW -> pose.setHeadYaw(pose.getHeadYaw() + change);
            case HEAD_PITCH -> pose.setHeadPitch(pose.getHeadPitch() + change);
            case BODY_YAW -> pose.setBodyYaw(pose.getBodyYaw() + change);
            case RIGHT_ARM_X -> pose.setRightArmX(pose.getRightArmX() + change);
            case RIGHT_ARM_Y -> pose.setRightArmY(pose.getRightArmY() + change);
            case RIGHT_ARM_Z -> pose.setRightArmZ(pose.getRightArmZ() + change);
            case LEFT_ARM_X -> pose.setLeftArmX(pose.getLeftArmX() + change);
            case LEFT_ARM_Y -> pose.setLeftArmY(pose.getLeftArmY() + change);
            case LEFT_ARM_Z -> pose.setLeftArmZ(pose.getLeftArmZ() + change);
            case RIGHT_LEG_X -> pose.setRightLegX(pose.getRightLegX() + change);
            case LEFT_LEG_X -> pose.setLeftLegX(pose.getLeftLegX() + change);
        }
    }

    /**
     * Handles click input for special modes.
     */
    public void handleClick() {
        NPC.NPCPose pose = npc.getPose();

        if (currentMode == EditMode.TOGGLE_LAYER) {
            pose.setShowSecondLayer(!pose.isShowSecondLayer());
            editor.sendMessage(Component.text(
                    "3D Layer: " + (pose.isShowSecondLayer() ? "ON" : "OFF"),
                    NamedTextColor.GREEN
            ));
        }
    }

    /**
     * Sends action bar update showing current mode.
     */
    void sendModeUpdate() {
        editor.sendActionBar(Component.text()
                .append(Component.text("Edit Mode: ", NamedTextColor.GRAY))
                .append(Component.text(currentMode.getDisplayName(), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(currentMode.getDescription(), NamedTextColor.GRAY))
                .build()
        );
    }

    /**
     * Sends current pose values to editor.
     */
    public void sendPoseInfo() {
        NPC.NPCPose pose = npc.getPose();

        editor.sendMessage(Component.text("=== NPC Pose ===", NamedTextColor.GOLD));
        editor.sendMessage(Component.text(String.format("Head: Yaw=%.1f° Pitch=%.1f°",
                pose.getHeadYaw(), pose.getHeadPitch()), NamedTextColor.GRAY));
        editor.sendMessage(Component.text(String.format("Body: Yaw=%.1f°",
                pose.getBodyYaw()), NamedTextColor.GRAY));
        editor.sendMessage(Component.text(String.format("Right Arm: X=%.1f° Y=%.1f° Z=%.1f°",
                pose.getRightArmX(), pose.getRightArmY(), pose.getRightArmZ()), NamedTextColor.GRAY));
        editor.sendMessage(Component.text(String.format("Left Arm: X=%.1f° Y=%.1f° Z=%.1f°",
                pose.getLeftArmX(), pose.getLeftArmY(), pose.getLeftArmZ()), NamedTextColor.GRAY));
        editor.sendMessage(Component.text("3D Layer: " + (pose.isShowSecondLayer() ? "ON" : "OFF"),
                NamedTextColor.GRAY));
    }
}