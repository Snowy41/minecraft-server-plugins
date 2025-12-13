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
     * @param viewer The player to send packets to
     * @param target The location to look at
     */
    public void lookAt(NPC npc, Player viewer, Location target) {
        Location npcLoc = npc.getLocation();

        // Calculate direction vector (use eye height for more natural look)
        Location npcEye = npcLoc.clone().add(0, 1.62, 0); // Player eye height
        Vector direction = target.toVector().subtract(npcEye.toVector()).normalize();

        // Calculate yaw (horizontal rotation)
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));

        // Calculate pitch (vertical rotation)
        // FIXED: Negative pitch = looking up, positive = looking down
        float pitch = (float) -Math.toDegrees(Math.asin(direction.getY()));

        try {
            // Convert to Minecraft's byte format
            byte headYawByte = (byte) ((yaw * 256.0F) / 360.0F);
            byte headPitchByte = (byte) ((pitch * 256.0F) / 360.0F);

            // Send entity head rotation packet
            PacketContainer headRotation = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_HEAD_ROTATION
            );
            headRotation.getIntegers().write(0, npc.getEntityId());
            headRotation.getBytes().write(0, headYawByte);
            protocolManager.sendServerPacket(viewer, headRotation);

            // Send entity look packet (body + pitch)
            PacketContainer entityLook = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_LOOK
            );
            entityLook.getIntegers().write(0, npc.getEntityId());
            entityLook.getBytes().write(0, headYawByte);
            entityLook.getBytes().write(1, headPitchByte);
            entityLook.getBooleans().write(0, true); // On ground
            protocolManager.sendServerPacket(viewer, entityLook);

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