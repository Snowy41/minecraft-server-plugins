package com.yourserver.npc.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.storage.NPCStorage;
import com.yourserver.npc.util.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.comphenix.protocol.PacketType.Play.Server.*;

/**
 * Enhanced NPC Manager with proper pose support and second skin layer.
 */
public class NPCManager {

    private final NPCPlugin plugin;
    private final NPCStorage storage;
    private final ProtocolManager protocolManager;
    private final Map<String, NPC> npcs;
    private final Map<Integer, String> entityIdToNPC;

    // Track players in editor mode
    private final Map<UUID, String> editMode; // Player UUID -> NPC ID being edited

    public NPCManager(NPCPlugin plugin, NPCStorage storage, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.protocolManager = protocolManager;
        this.npcs = new ConcurrentHashMap<>();
        this.entityIdToNPC = new ConcurrentHashMap<>();
        this.editMode = new ConcurrentHashMap<>();
    }

    /**
     * Loads all NPCs from storage.
     */
    public void loadAllNPCs() {
        List<NPC> loadedNPCs = storage.loadNPCs();
        plugin.getLogger().info("Loading " + loadedNPCs.size() + " NPCs from storage...");

        for (NPC npc : loadedNPCs) {
            if (!npc.hasSkin()) {
                plugin.getLogger().info("  Fetching skin for: " + npc.getId());
                SkinFetcher.fetchSkinAsync(npc.getName()).thenAccept(skinData -> {
                    if (skinData != null) {
                        npc.setSkin(skinData[0], skinData[1]);
                        storage.saveNPCs(new ArrayList<>(npcs.values()));
                    }
                    spawnNPCForAllPlayers(npc);
                });
            } else {
                spawnNPCForAllPlayers(npc);
            }

            npcs.put(npc.getId(), npc);
            entityIdToNPC.put(npc.getEntityId(), npc.getId());
        }

        plugin.getLogger().info("✓ Loaded " + npcs.size() + " NPCs");
    }

    /**
     * Creates a new NPC.
     */
    public void createNPC(@NotNull String id, @NotNull String playerName,
                          @NotNull Location location, @NotNull NPC.Action action) {
        if (npcs.containsKey(id)) {
            plugin.getLogger().warning("NPC already exists: " + id);
            return;
        }

        NPC npc = new NPC(id, playerName, location, action);
        plugin.getLogger().info("Creating NPC: " + id + " (" + playerName + ")");

        SkinFetcher.fetchSkinAsync(playerName).thenAccept(skinData -> {
            if (skinData != null) {
                npc.setSkin(skinData[0], skinData[1]);
                plugin.getLogger().info("  ✓ Fetched skin for: " + playerName);
            }
            spawnNPCForAllPlayers(npc);
            storage.saveNPCs(new ArrayList<>(npcs.values()));
        });

        npcs.put(id, npc);
        entityIdToNPC.put(npc.getEntityId(), id);
    }

    /**
     * Removes an NPC.
     */
    public void removeNPC(@NotNull String id) {
        NPC npc = npcs.remove(id);
        if (npc == null) return;

        entityIdToNPC.remove(npc.getEntityId());
        despawnNPCForAllPlayers(npc);
        storage.saveNPCs(new ArrayList<>(npcs.values()));
        plugin.getLogger().info("✓ Removed NPC: " + id);
    }

    /**
     * Updates NPC pose and refreshes for all players.
     */
    public void updateNPCPose(@NotNull String id, @NotNull NPC.NPCPose pose) {
        NPC npc = npcs.get(id);
        if (npc == null) return;

        npc.setPose(pose);

        // Refresh NPC for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEntityMetadataPacket(player, npc);
        }

        storage.saveNPCs(new ArrayList<>(npcs.values()));
    }

    /**
     * Enters editor mode for an NPC.
     */
    public void enterEditorMode(@NotNull Player player, @NotNull String npcId) {
        NPC npc = npcs.get(npcId);
        if (npc == null) {
            player.sendMessage("§cNPC not found!");
            return;
        }

        editMode.put(player.getUniqueId(), npcId);
        player.sendMessage("§a✓ Entered editor mode for: §f" + npcId);
        player.sendMessage("§7Use §f/npc edit <part> <axis> <degrees>§7 to adjust pose");
        player.sendMessage("§7Parts: head, body, rightarm, leftarm, rightleg, leftleg");
        player.sendMessage("§7Axes: pitch, yaw, roll");
        player.sendMessage("§7Example: §f/npc edit head yaw 45");
        player.sendMessage("§7Use §f/npc edit done§7 when finished");
    }

    /**
     * Exits editor mode.
     */
    public void exitEditorMode(@NotNull Player player) {
        String npcId = editMode.remove(player.getUniqueId());
        if (npcId != null) {
            player.sendMessage("§a✓ Exited editor mode");
            storage.saveNPCs(new ArrayList<>(npcs.values()));
        }
    }

    /**
     * Gets the NPC being edited by a player.
     */
    @Nullable
    public NPC getEditingNPC(@NotNull Player player) {
        String npcId = editMode.get(player.getUniqueId());
        return npcId != null ? npcs.get(npcId) : null;
    }

    /**
     * Checks if player is in editor mode.
     */
    public boolean isInEditorMode(@NotNull Player player) {
        return editMode.containsKey(player.getUniqueId());
    }

    /**
     * Spawns NPC for all online players.
     */
    private void spawnNPCForAllPlayers(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnNPCForPlayer(player, npc);
        }
    }

    /**
     * Spawns NPC for a specific player with proper pose and second layer.
     */
    public void spawnNPCForPlayer(@NotNull Player player, @NotNull NPC npc) {
        try {
            // 1. Add to tab list
            sendPlayerInfoPacket(player, npc, true);

            // 2. Spawn entity
            sendSpawnPlayerPacket(player, npc);

            // 3. Set entity metadata (pose + second layer)
            sendEntityMetadataPacket(player, npc);

            // 4. Remove from tab list after delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInfoPacket(player, npc, false);
            }, 40L);

            // 5. Spawn hologram if present
            if (!npc.getHologramLines().isEmpty()) {
                spawnHologramForPlayer(player, npc);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn NPC: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Despawns NPC for all players.
     */
    private void despawnNPCForAllPlayers(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            despawnNPCForPlayer(player, npc);
        }
    }

    /**
     * Despawns NPC for a specific player.
     */
    private void despawnNPCForPlayer(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer destroyPacket = protocolManager.createPacket(ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(npc.getEntityId()));
            protocolManager.sendServerPacket(player, destroyPacket);

            for (int hologramId : npc.getHologramEntityIds()) {
                PacketContainer hologramDestroyPacket = protocolManager.createPacket(ENTITY_DESTROY);
                hologramDestroyPacket.getIntLists().write(0, List.of(hologramId));
                protocolManager.sendServerPacket(player, hologramDestroyPacket);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to despawn NPC: " + e.getMessage());
        }
    }

    /**
     * Sends player info packet (tab list).
     */
    private void sendPlayerInfoPacket(@NotNull Player player, @NotNull NPC npc, boolean add) {
        try {
            if (add) {
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
                WrappedGameProfile gameProfile = new WrappedGameProfile(npc.getUuid(), npc.getName());

                if (npc.hasSkin()) {
                    gameProfile.getProperties().put("textures",
                            new WrappedSignedProperty("textures", npc.getSkinTexture(), npc.getSkinSignature())
                    );
                }

                packet.getPlayerInfoActions().write(0, EnumSet.of(
                        EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                        EnumWrappers.PlayerInfoAction.UPDATE_LISTED
                ));

                PlayerInfoData data = new PlayerInfoData(
                        gameProfile, 0, EnumWrappers.NativeGameMode.SURVIVAL,
                        WrappedChatComponent.fromText(npc.getName()), null
                );

                packet.getPlayerInfoDataLists().write(1, List.of(data));
                protocolManager.sendServerPacket(player, packet);

            } else {
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                packet.getUUIDLists().write(0, List.of(npc.getUuid()));
                protocolManager.sendServerPacket(player, packet);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send player info: " + e.getMessage());
        }
    }

    /**
     * Sends spawn player packet.
     */
    private void sendSpawnPlayerPacket(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer packet = protocolManager.createPacket(SPAWN_ENTITY);
            Location loc = npc.getLocation();

            packet.getIntegers().write(0, npc.getEntityId());
            packet.getUUIDs().write(0, npc.getUuid());
            packet.getEntityTypeModifier().write(0, EntityType.PLAYER);
            packet.getDoubles().write(0, loc.getX());
            packet.getDoubles().write(1, loc.getY());
            packet.getDoubles().write(2, loc.getZ());
            packet.getIntegers().write(1, 0); // Velocity X
            packet.getIntegers().write(2, 0); // Velocity Y
            packet.getIntegers().write(3, 0); // Velocity Z
            packet.getBytes().write(0, (byte) ((loc.getYaw() * 256.0F) / 360.0F));
            packet.getBytes().write(1, (byte) ((loc.getPitch() * 256.0F) / 360.0F));
            packet.getBytes().write(2, (byte) ((loc.getYaw() * 256.0F) / 360.0F));

            protocolManager.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send spawn packet: " + e.getMessage());
        }
    }

    /**
     * Sends entity metadata packet with pose and second skin layer.
     * THIS IS THE KEY METHOD FOR POSES!
     */
    private void sendEntityMetadataPacket(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer packet = protocolManager.createPacket(ENTITY_METADATA);
            packet.getIntegers().write(0, npc.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher();

            // Index 17: Skin layers (byte)
            // 0x01 = Cape, 0x02 = Jacket, 0x04 = Left Sleeve, 0x08 = Right Sleeve
            // 0x10 = Left Pants, 0x20 = Right Pants, 0x40 = Hat
            // 0x7F = All layers enabled
            byte skinLayers = (byte) 0x7F; // Enable ALL skin layers (including hat/jacket)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(17,
                    WrappedDataWatcher.Registry.get(Byte.class)), skinLayers);

            // Pose data (indices 18-23 for 1.21.8)
            NPC.NPCPose pose = npc.getPose();

            // Head pose (index 18)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(18,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getHeadPitch(), pose.getHeadYaw(), pose.getHeadRoll()));

            // Body pose (index 19)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(19,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getBodyPitch(), pose.getBodyYaw(), pose.getBodyRoll()));

            // Left arm (index 20)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(20,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getLeftArmPitch(), pose.getLeftArmYaw(), pose.getLeftArmRoll()));

            // Right arm (index 21)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(21,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getRightArmPitch(), pose.getRightArmYaw(), pose.getRightArmRoll()));

            // Left leg (index 22)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(22,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getLeftLegPitch(), pose.getLeftLegYaw(), pose.getLeftLegRoll()));

            // Right leg (index 23)
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(23,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getRightLegPitch(), pose.getRightLegYaw(), pose.getRightLegRoll()));

            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a rotation vector for entity metadata.
     * Converts degrees to the format Minecraft expects.
     */
    private Vector3f createRotationVector(float pitch, float yaw, float roll) {
        return new Vector3f(
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(yaw),
                (float) Math.toRadians(roll)
        );
    }

    /**
     * Spawns hologram for player.
     */
    private void spawnHologramForPlayer(@NotNull Player player, @NotNull NPC npc) {
        Location baseLoc = npc.getLocation().clone().add(0, 2.3, 0);
        List<Integer> hologramIds = new ArrayList<>();

        for (int i = 0; i < npc.getHologramLines().size(); i++) {
            String line = npc.getHologramLines().get(i);
            Location lineLoc = baseLoc.clone().subtract(0, i * 0.3, 0);
            int entityId = generateEntityId();
            hologramIds.add(entityId);

            try {
                PacketContainer spawnPacket = protocolManager.createPacket(SPAWN_ENTITY);
                spawnPacket.getIntegers().write(0, entityId);
                spawnPacket.getUUIDs().write(0, UUID.randomUUID());
                spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
                spawnPacket.getDoubles().write(0, lineLoc.getX());
                spawnPacket.getDoubles().write(1, lineLoc.getY());
                spawnPacket.getDoubles().write(2, lineLoc.getZ());
                protocolManager.sendServerPacket(player, spawnPacket);

                PacketContainer metaPacket = protocolManager.createPacket(ENTITY_METADATA);
                metaPacket.getIntegers().write(0, entityId);

                WrappedDataWatcher watcher = new WrappedDataWatcher();
                watcher.setObject(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20);
                watcher.setObject(2, WrappedDataWatcher.Registry.getChatComponentSerializer(true),
                        Optional.of(WrappedChatComponent.fromText(line).getHandle()));
                watcher.setObject(3, WrappedDataWatcher.Registry.get(Boolean.class), true);
                watcher.setObject(15, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x01);

                metaPacket.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
                protocolManager.sendServerPacket(player, metaPacket);

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to spawn hologram: " + e.getMessage());
            }
        }

        npc.setHologramEntityIds(hologramIds);
    }

    /**
     * Handles NPC interaction.
     */
    public void handleInteraction(@NotNull Player player, @NotNull NPC npc) {
        npc.getAction().execute(plugin, player);
    }

    @Nullable
    public NPC getNPCByEntityId(int entityId) {
        String npcId = entityIdToNPC.get(entityId);
        return npcId != null ? npcs.get(npcId) : null;
    }

    @Nullable
    public NPC getNPC(@NotNull String id) {
        return npcs.get(id);
    }

    @NotNull
    public Collection<NPC> getAllNPCs() {
        return new ArrayList<>(npcs.values());
    }

    public void removeAllNPCs() {
        new ArrayList<>(npcs.keySet()).forEach(this::removeNPC);
    }

    private static int generateEntityId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }
}