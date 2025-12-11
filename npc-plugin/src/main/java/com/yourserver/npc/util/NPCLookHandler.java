package com.yourserver.npc.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.yourserver.npc.model.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Handles NPC look direction and head rotation.
 * Makes NPCs look at nearby players naturally.
 */
public class NPCLookHandler {

    private final ProtocolManager protocolManager;

    public NPCLookHandler(ProtocolManager protocolManager) {
        this.protocolManager = protocolManager;
    }

    /**
     * Makes an NPC look at a player.
     *
     * @param npc The NPC
     * @param player The player to send packets to
     * @param target The location to look at
     */
    public void lookAt(NPC npc, Player player, Location target) {
        Location npcLoc = npc.getLocation();

        // Calculate yaw and pitch
        Vector direction = target.toVector().subtract(npcLoc.toVector()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(Math.asin(-direction.getY()));

        try {
            // Send entity head rotation packet
            PacketContainer headRotation = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_HEAD_ROTATION
            );
            headRotation.getIntegers().write(0, npc.getEntityId());
            headRotation.getBytes().write(0, (byte) ((yaw * 256.0F) / 360.0F));

            protocolManager.sendServerPacket(player, headRotation);

            // Send entity look packet (body rotation)
            PacketContainer entityLook = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_LOOK
            );
            entityLook.getIntegers().write(0, npc.getEntityId());
            entityLook.getBytes().write(0, (byte) ((yaw * 256.0F) / 360.0F));
            entityLook.getBytes().write(1, (byte) ((pitch * 256.0F) / 360.0F));
            entityLook.getBooleans().write(0, true); // On ground

            protocolManager.sendServerPacket(player, entityLook);

        } catch (Exception e) {
            // Silent fail - not critical
        }
    }

    /**
     * Makes an NPC look at a player.
     */
    public void lookAtPlayer(NPC npc, Player viewer, Player target) {
        lookAt(npc, viewer, target.getEyeLocation());
    }

    /**
     * Resets NPC look direction to its spawn location direction.
     */
    public void resetLook(NPC npc, Player player) {
        Location loc = npc.getLocation();

        try {
            PacketContainer headRotation = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_HEAD_ROTATION
            );
            headRotation.getIntegers().write(0, npc.getEntityId());
            headRotation.getBytes().write(0, (byte) ((loc.getYaw() * 256.0F) / 360.0F));

            protocolManager.sendServerPacket(player, headRotation);

        } catch (Exception e) {
            // Silent fail
        }
    }
}