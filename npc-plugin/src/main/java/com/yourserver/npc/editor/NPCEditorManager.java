package com.yourserver.npc.editor;

import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Manages active NPC editor sessions.
 */
public class NPCEditorManager {

    private final NPCPlugin plugin;
    private final Map<UUID, NPCEditorSession> activeSessions;

    public NPCEditorManager(NPCPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();
    }

    /**
     * Starts an editing session for a player.
     */
    public boolean startSession(@NotNull Player player, @NotNull NPC npc) {
        UUID uuid = player.getUniqueId();

        if (activeSessions.containsKey(uuid)) {
            player.sendMessage(Component.text(
                    "You're already editing an NPC! Use /npc editor exit first.",
                    NamedTextColor.RED
            ));
            return false;
        }

        NPCEditorSession session = new NPCEditorSession(player, npc);
        activeSessions.put(uuid, session);

        // Send instructions
        player.sendMessage(Component.text("=== NPC Editor Mode ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Editing: " + npc.getId(), NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Controls:", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  • SCROLL - Adjust current property", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  • F - Next edit mode", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  • SHIFT+F - Previous edit mode", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  • Q - Show current pose", NamedTextColor.WHITE));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Presets:", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  /npc editor preset <name>", NamedTextColor.WHITE));
        player.sendMessage(Component.text("  (standing, waving, pointing, sitting, crouching, dabbing)", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Exit: /npc editor exit", NamedTextColor.RED));

        session.sendModeUpdate();

        return true;
    }

    /**
     * Clears all active sessions (called on plugin disable).
     */
    public void clearAllSessions() {
        for (UUID uuid : new HashSet<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                endSession(player, true);
            }
        }
        activeSessions.clear();
    }

    /**
     * Ends an editing session and saves changes.
     */
    public boolean endSession(@NotNull Player player, boolean save) {
        UUID uuid = player.getUniqueId();
        NPCEditorSession session = activeSessions.remove(uuid);

        if (session == null) {
            return false;
        }

        if (save) {
            player.sendMessage(Component.text(
                    "✓ Saved changes to NPC: " + session.getNPC().getId(),
                    NamedTextColor.GREEN
            ));
        } else {
            player.sendMessage(Component.text(
                    "Discarded changes to NPC: " + session.getNPC().getId(),
                    NamedTextColor.YELLOW
            ));
        }

        return true;
    }

    /**
     * Gets active session for a player.
     */
    @Nullable
    public NPCEditorSession getSession(@NotNull Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /**
     * Checks if player is in editor mode.
     */
    public boolean isEditing(@NotNull Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Cleans up session on player quit.
     */
    public void cleanup(@NotNull Player player) {
        activeSessions.remove(player.getUniqueId());
    }
}