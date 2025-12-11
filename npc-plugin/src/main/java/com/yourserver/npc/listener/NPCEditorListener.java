package com.yourserver.npc.listener;

import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.editor.NPCEditorManager;
import com.yourserver.npc.editor.NPCEditorSession;
import com.yourserver.npc.manager.NPCManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Handles editor mode controls.
 */
public class NPCEditorListener implements Listener {

    private final NPCPlugin plugin;
    private final NPCEditorManager editorManager;
    private final NPCManager npcManager;

    public NPCEditorListener(NPCPlugin plugin, NPCEditorManager editorManager, NPCManager npcManager) {
        this.plugin = plugin;
        this.editorManager = editorManager;
        this.npcManager = npcManager;
    }

    /**
     * Handle scroll input (change hotbar slot).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        NPCEditorSession session = editorManager.getSession(player);

        if (session == null) return;

        event.setCancelled(true);

        int previous = event.getPreviousSlot();
        int current = event.getNewSlot();

        // Determine scroll direction
        int scrollAmount = current - previous;

        // Handle wraparound (slot 8 -> 0 = scroll up, slot 0 -> 8 = scroll down)
        if (scrollAmount > 4) scrollAmount -= 9;
        if (scrollAmount < -4) scrollAmount += 9;

        session.handleScroll(scrollAmount);

        // Update NPC visually
        npcManager.updateNPCPose(session.getNPC());

        // Reset to middle slot
        player.getInventory().setHeldItemSlot(4);
    }

    /**
     * Handle F key (swap hand items) -> cycle edit mode.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        NPCEditorSession session = editorManager.getSession(player);

        if (session == null) return;

        event.setCancelled(true);

        if (player.isSneaking()) {
            session.previousMode();
        } else {
            session.nextMode();
        }
    }

    /**
     * Handle Q key (drop item) -> show pose info.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        NPCEditorSession session = editorManager.getSession(player);

        if (session == null) return;

        event.setCancelled(true);
        session.sendPoseInfo();
    }

    /**
     * Cleanup on quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        NPCEditorSession session = editorManager.getSession(event.getPlayer());
        if (session != null) {
            // Save changes before cleanup
            npcManager.updateNPCPose(session.getNPC());
            editorManager.endSession(event.getPlayer(), true);
            editorManager.cleanup(event.getPlayer());
        }
    }
}