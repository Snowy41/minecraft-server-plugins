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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages custom NPCs that look like real players with skins.
 * NPCs are persisted to npcs.yml and respawn on server restart.
 */
public class NPCManager {

    private final LobbyPlugin plugin;
    private final Map<String, CustomNPC> npcs;
    private final Map<UUID, String> entityToNPC; // Maps entity UUID to NPC ID
    private final NPCStorage storage;

    public NPCManager(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.npcs = new ConcurrentHashMap<>();
        this.entityToNPC = new ConcurrentHashMap<>();
        this.storage = new NPCStorage(plugin);

        // Load NPCs from storage on initialization
        loadNPCs();
    }

    /**
     * Loads all NPCs from storage and spawns them.
     */
    private void loadNPCs() {
        List<CustomNPC> loadedNPCs = storage.loadNPCs();
        for (CustomNPC npc : loadedNPCs) {
            spawnNPC(npc);
        }
        plugin.getLogger().info("Loaded " + loadedNPCs.size() + " NPCs from storage");
    }

    /**
     * Spawns an NPC at the given location.
     *
     * @param npc The NPC to spawn
     */
    public void spawnNPC(@NotNull CustomNPC npc) {
        Location location = npc.getLocation();

        // Fetch skin asynchronously if needed
        if (npc.getSkinTexture() == null && npc.getName() != null) {
            // Try to fetch skin from Mojang API
            fetchSkinFromUsername(npc.getName()).thenAccept(skinData -> {
                if (skinData != null) {
                    // Update NPC with fetched skin
                    CustomNPC updatedNPC = new CustomNPC(
                            npc.getId(),
                            npc.getName(),
                            npc.getLocation(),
                            skinData[0], // texture
                            skinData[1], // signature
                            npc.getAction(),
                            npc.getHologramLines()
                    );

                    // Spawn the NPC with skin
                    spawnNPCEntity(updatedNPC);
                } else {
                    // Spawn without skin
                    spawnNPCEntity(npc);
                }
            });
        } else {
            // Spawn immediately with existing skin data
            spawnNPCEntity(npc);
        }
    }

    /**
     * Actually spawns the NPC entity in the world.
     */
    private void spawnNPCEntity(@NotNull CustomNPC npc) {
        Location location = npc.getLocation();

        // Spawn armor stand as the NPC body
        ArmorStand npcEntity = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);

        // Configure armor stand to look like a player
        npcEntity.setVisible(false); // Invisible armor stand
        npcEntity.setGravity(false);
        npcEntity.setInvulnerable(true);
        npcEntity.setCustomNameVisible(false);
        npcEntity.customName(plugin.getMiniMessage().deserialize(npc.getName()));
        npcEntity.setCollidable(false);
        npcEntity.setCanPickupItems(false);
        npcEntity.setMarker(true); // Smaller hitbox

        // Add player head with skin
        if (npc.hasSkin()) {
            addPlayerHead(npcEntity, npc);
        }

        // Mark as NPC
        npcEntity.setMetadata("CustomNPC", new FixedMetadataValue(plugin, npc.getId()));

        npc.setEntityUUID(npcEntity.getUniqueId());
        entityToNPC.put(npcEntity.getUniqueId(), npc.getId());

        // Spawn hologram above NPC
        if (!npc.getHologramLines().isEmpty()) {
            spawnHologram(npc, location.clone().add(0, 2.0, 0));
        }

        npcs.put(npc.getId(), npc);

        // Save to storage
        storage.saveNPCs(new ArrayList<>(npcs.values()));

        plugin.getLogger().info("Spawned NPC: " + npc.getId() + " at " +
                location.getWorld().getName() + " " +
                location.getBlockX() + ", " +
                location.getBlockY() + ", " +
                location.getBlockZ());
    }

    /**
     * Adds a player head to the armor stand using the NPC's skin.
     */
    private void addPlayerHead(ArmorStand stand, CustomNPC npc) {
        // Create player profile with skin
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), npc.getName());

        if (npc.getSkinTexture() != null && npc.getSkinSignature() != null) {
            profile.setProperty(new ProfileProperty(
                    "textures",
                    npc.getSkinTexture(),
                    npc.getSkinSignature()
            ));
        }

        // Create player head item
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);

            // Set as helmet
            stand.getEquipment().setHelmet(head);
        }
    }

    /**
     * Fetches skin data from Mojang API for a given username.
     * Returns [texture, signature] or null if not found.
     */
    private CompletableFuture<String[]> fetchSkinFromUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Try to get player profile from Bukkit (if they've joined before)
                Player onlinePlayer = Bukkit.getPlayerExact(username);
                if (onlinePlayer != null) {
                    PlayerProfile profile = onlinePlayer.getPlayerProfile();
                    if (profile.hasTextures()) {
                        Collection<ProfileProperty> textures = profile.getProperties();
                        for (ProfileProperty prop : textures) {
                            if (prop.getName().equals("textures")) {
                                return new String[]{prop.getValue(), prop.getSignature()};
                            }
                        }
                    }
                }

                // Try offline player
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
                if (offlinePlayer.hasPlayedBefore()) {
                    PlayerProfile profile = offlinePlayer.getPlayerProfile();
                    if (profile.hasTextures()) {
                        Collection<ProfileProperty> textures = profile.getProperties();
                        for (ProfileProperty prop : textures) {
                            if (prop.getName().equals("textures")) {
                                return new String[]{prop.getValue(), prop.getSignature()};
                            }
                        }
                    }
                }

                plugin.getLogger().warning("Could not fetch skin for username: " + username);
                return null;

            } catch (Exception e) {
                plugin.getLogger().warning("Error fetching skin for " + username + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Spawns a hologram (text display) above the NPC.
     */
    private void spawnHologram(CustomNPC npc, Location startLocation) {
        double spacing = 0.25; // Space between lines
        Location currentLoc = startLocation.clone();

        // Spawn lines from bottom to top
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
            hologramLine.setCanPickupItems(false);

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

        // Save to storage
        storage.saveNPCs(new ArrayList<>(npcs.values()));

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
        loadNPCs();
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