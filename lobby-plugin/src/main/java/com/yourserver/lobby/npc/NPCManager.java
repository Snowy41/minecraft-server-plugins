package com.yourserver.lobby.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yourserver.lobby.LobbyPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages custom NPCs (player entities with skins).
 */
public class NPCManager {

    private final LobbyPlugin plugin;
    private final Map<String, CustomNPC> npcs;
    private final Map<UUID, String> entityToNPC; // Maps entity UUID to NPC ID

    public NPCManager(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.npcs = new ConcurrentHashMap<>();
        this.entityToNPC = new ConcurrentHashMap<>();
    }

    /**
     * Spawns an NPC at the given location.
     *
     * @param npc The NPC to spawn
     */
    public void spawnNPC(@NotNull CustomNPC npc) {
        Location location = npc.getLocation();

        // Spawn the NPC as a player entity
        Player npcPlayer = spawnPlayerNPC(npc);

        if (npcPlayer != null) {
            npc.setEntityUUID(npcPlayer.getUniqueId());
            entityToNPC.put(npcPlayer.getUniqueId(), npc.getId());

            // Spawn hologram above NPC
            if (!npc.getHologramLines().isEmpty()) {
                spawnHologram(npc, location.clone().add(0, 2.3, 0));
            }

            npcs.put(npc.getId(), npc);
            plugin.getLogger().info("Spawned NPC: " + npc.getId() + " at " +
                    location.getWorld().getName() + " " +
                    location.getBlockX() + ", " +
                    location.getBlockY() + ", " +
                    location.getBlockZ());
        }
    }

    /**
     * Spawns a player NPC using packets (Paper API).
     * Note: This creates an actual player entity with custom skin.
     */
    private Player spawnPlayerNPC(CustomNPC npc) {
        Location loc = npc.getLocation();

        // Create player profile with custom skin
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), npc.getName());

        if (npc.hasSkin()) {
            profile.setProperty(new ProfileProperty(
                    "textures",
                    npc.getSkinTexture(),
                    npc.getSkinSignature()
            ));
        }

        // For now, we'll use armor stands with player heads as a workaround
        // A full implementation would require NMS/ProtocolLib for actual player NPCs
        // This is a simplified version that works without additional dependencies

        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.customName(plugin.getMiniMessage().deserialize(npc.getName()));
        stand.setCollidable(false);
        stand.setMarker(true);

        // Mark as NPC
        stand.setMetadata("CustomNPC", new FixedMetadataValue(plugin, npc.getId()));

        return null; // TODO: Implement full player NPC with packets
    }

    /**
     * Spawns a hologram (text display) above the NPC.
     */
    private void spawnHologram(CustomNPC npc, Location startLocation) {
        double spacing = 0.3; // Space between lines
        Location currentLoc = startLocation.clone();

        for (int i = npc.getHologramLines().size() - 1; i >= 0; i--) {
            String line = npc.getHologramLines().get(i);

            ArmorStand hologramLine = (ArmorStand) currentLoc.getWorld()
                    .spawnEntity(currentLoc, EntityType.ARMOR_STAND);

            hologramLine.setVisible(false);
            hologramLine.setGravity(false);
            hologramLine.setInvulnerable(true);
            hologramLine.setCustomNameVisible(true);
            hologramLine.customName(plugin.getMiniMessage().deserialize(line));
            hologramLine.setMarker(true);
            hologramLine.setCollidable(false);

            // Mark as hologram
            hologramLine.setMetadata("CustomNPC_Hologram", new FixedMetadataValue(plugin, npc.getId()));

            currentLoc.add(0, spacing, 0);
        }
    }

    /**
     * Removes an NPC by ID.
     *
     * @param npcId The NPC ID
     */
    public void removeNPC(@NotNull String npcId) {
        CustomNPC npc = npcs.remove(npcId);
        if (npc == null) {
            return;
        }

        // Remove the entity
        if (npc.getEntityUUID() != null) {
            entityToNPC.remove(npc.getEntityUUID());

            // Find and remove the entity
            npc.getLocation().getWorld().getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(npc.getEntityUUID()))
                    .findFirst()
                    .ifPresent(Entity::remove);
        }

        // Remove hologram lines
        npc.getLocation().getWorld().getEntities().stream()
                .filter(e -> e.hasMetadata("CustomNPC_Hologram"))
                .filter(e -> e.getMetadata("CustomNPC_Hologram").get(0).asString().equals(npcId))
                .forEach(Entity::remove);

        plugin.getLogger().info("Removed NPC: " + npcId);
    }

    /**
     * Removes all NPCs.
     */
    public void removeAllNPCs() {
        new ArrayList<>(npcs.keySet()).forEach(this::removeNPC);
    }

    /**
     * Gets an NPC by entity UUID.
     *
     * @param entityUUID The entity UUID
     * @return The NPC, or null if not found
     */
    @Nullable
    public CustomNPC getNPCByEntity(@NotNull UUID entityUUID) {
        String npcId = entityToNPC.get(entityUUID);
        return npcId != null ? npcs.get(npcId) : null;
    }

    /**
     * Gets an NPC by entity.
     *
     * @param entity The entity
     * @return The NPC, or null if not found
     */
    @Nullable
    public CustomNPC getNPCByEntity(@NotNull Entity entity) {
        if (entity.hasMetadata("CustomNPC")) {
            String npcId = entity.getMetadata("CustomNPC").get(0).asString();
            return npcs.get(npcId);
        }
        return null;
    }

    /**
     * Gets an NPC by ID.
     *
     * @param npcId The NPC ID
     * @return The NPC, or null if not found
     */
    @Nullable
    public CustomNPC getNPC(@NotNull String npcId) {
        return npcs.get(npcId);
    }

    /**
     * Gets all NPCs.
     *
     * @return Map of NPC ID to NPC
     */
    public Map<String, CustomNPC> getAllNPCs() {
        return new HashMap<>(npcs);
    }

    /**
     * Reloads all NPCs from config.
     */
    public void reloadNPCs() {
        removeAllNPCs();
        // Load NPCs from config (implemented in config loader)
    }

    /**
     * Handles NPC interaction.
     *
     * @param player The player who interacted
     * @param npc The NPC that was interacted with
     */
    public void handleInteraction(@NotNull Player player, @NotNull CustomNPC npc) {
        CustomNPC.NPCAction action = npc.getAction();

        switch (action.getType()) {
            case TELEPORT -> handleTeleport(player, action.getData());
            case COMMAND -> handleCommand(player, action.getData());
            case SERVER -> handleServer(player, action.getData());
            case GUI -> handleGUI(player, action.getData());
            case MESSAGE -> handleMessage(player, action.getData());
        }
    }

    private void handleTeleport(Player player, String data) {
        try {
            String[] parts = data.split(",");
            String worldName = parts[0];
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;

            Location location = new Location(
                    Bukkit.getWorld(worldName),
                    x, y, z, yaw, pitch
            );

            player.teleport(location);
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                            "<green>Teleported!"
            ));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid teleport data for NPC: " + data);
        }
    }

    private void handleCommand(Player player, String command) {
        // Replace {player} placeholder
        String finalCommand = command.replace("{player}", player.getName());
        player.performCommand(finalCommand);
    }

    private void handleServer(Player player, String serverName) {
        player.sendMessage(Component.text("Sending you to " + serverName + "..."));
        // TODO: Implement BungeeCord/Velocity server switch
    }

    private void handleGUI(Player player, String guiType) {
        switch (guiType.toLowerCase()) {
            case "game_selector", "games" -> plugin.getGuiManager().openGameSelector(player);
            case "cosmetics" -> plugin.getGuiManager().openCosmetics(player);
            case "stats", "statistics" -> plugin.getGuiManager().openStats(player);
            default -> player.sendMessage(Component.text("Unknown GUI: " + guiType));
        }
    }

    private void handleMessage(Player player, String message) {
        player.sendMessage(plugin.getMiniMessage().deserialize(message));
    }
}