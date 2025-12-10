package com.yourserver.npc.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.manager.NPCManager;
import com.yourserver.npc.model.NPC;
import org.bukkit.entity.Player;

public class NPCInteractListener extends PacketAdapter {

    private final NPCManager npcManager;

    public NPCInteractListener(NPCPlugin plugin, NPCManager npcManager) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY);
        this.npcManager = npcManager;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();

        int entityId = event.getPacket().getIntegers().read(0);
        EnumWrappers.EntityUseAction action = event.getPacket().getEnumEntityUseActions().read(0).getAction();

        // Only handle right-clicks
        if (action != EnumWrappers.EntityUseAction.INTERACT &&
                action != EnumWrappers.EntityUseAction.INTERACT_AT) {
            return;
        }

        NPC npc = npcManager.getNPCByEntityId(entityId);
        if (npc != null) {
            event.setCancelled(true);
            npcManager.handleInteraction(player, npc);
        }
    }
}