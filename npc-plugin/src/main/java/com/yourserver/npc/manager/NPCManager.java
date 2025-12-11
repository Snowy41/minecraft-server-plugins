package com.yourserver.npc.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.model.NPCEquipment;
import com.yourserver.npc.registry.NPCRegistry;
import com.yourserver.npc.storage.NPCStorage;
import com.yourserver.npc.util.NPCLookHandler;
import com.yourserver.npc.util.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.comphenix.protocol.PacketType.Play.Server.*;

/**
 * Enhanced NPC Manager with NPCRegistry, equipment support, and look handler.
 */
public class NPCManager {

    private final NPCPlugin plugin;
    private final NPCStorage storage;
    private final ProtocolManager protocolManager;
    private final NPCRegistry registry;
    private final NPCLookHandler lookHandler;
    private final Map<String, Boolean> autoLookEnabled = new ConcurrentHashMap<>();
    private BukkitTask lookTrackingTask;

    // Track players in editor mode
    private final Map<UUID, String> editMode; // Player UUID -> NPC ID being edited

    public NPCManager(NPCPlugin plugin, NPCStorage storage, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.protocolManager = protocolManager;
        this.registry = new NPCRegistry();
        this.lookHandler = new NPCLookHandler(protocolManager);
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
                        saveAllNPCs();
                    }
                    spawnNPCForAllPlayers(npc);
                });
            } else {
                spawnNPCForAllPlayers(npc);
            }

            registry.register(npc);
        }

        plugin.getLogger().info("✓ Loaded " + registry.size() + " NPCs");
    }

    /**
     * Creates a new NPC.
     */
    public void createNPC(@NotNull String id, @NotNull String playerName,
                          @NotNull Location location, @NotNull NPC.Action action) {
        if (registry.exists(id)) {
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
            saveAllNPCs();
        });

        registry.register(npc);
    }

    /**
     * Removes an NPC.
     */
    public void removeNPC(@NotNull String id) {
        NPC npc = registry.unregister(id);
        if (npc == null) return;

        despawnNPCForAllPlayers(npc);
        saveAllNPCs();
        plugin.getLogger().info("✓ Removed NPC: " + id);
    }

    /**
     * Refreshes an NPC for all players (pose, equipment, hologram).
     * Call this after ANY visual change.
     */
    private void refreshNPCForAllPlayers(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 1. Update metadata (pose + skin layer)
            sendEntityMetadataPacket(player, npc);

            // 2. Update equipment
            if (npc.getEquipment().hasEquipment()) {
                sendEquipmentPacket(player, npc);
            }

            // 3. Refresh hologram (despawn old, spawn new)
            if (!npc.getHologramLines().isEmpty()) {
                // Despawn old hologram
                for (int hologramId : npc.getHologramEntityIds()) {
                    try {
                        PacketContainer destroyPacket = protocolManager.createPacket(
                                PacketType.Play.Server.ENTITY_DESTROY
                        );
                        destroyPacket.getIntLists().write(0, List.of(hologramId));
                        protocolManager.sendServerPacket(player, destroyPacket);
                    } catch (Exception e) {
                        // Silent fail
                    }
                }

                // Spawn new hologram
                spawnHologramForPlayer(player, npc);
            }
        }
    }

    /**
     * Updates NPC pose and refreshes for all players.
     */
    public void updateNPCPose(@NotNull String id, @NotNull NPC.NPCPose pose) {
        NPC npc = registry.getNPC(id);
        if (npc == null) return;

        npc.setPose(pose);

        // Refresh NPC for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEntityMetadataPacket(player, npc);
        }

        refreshNPCForAllPlayers(npc);
        saveAllNPCs();
    }

    /**
     * Updates NPC pose (overload accepting NPC directly).
     */
    public void updateNPCPose(@NotNull NPC npc) {
        // Refresh NPC for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEntityMetadataPacket(player, npc);
        }

        refreshNPCForAllPlayers(npc);
        saveAllNPCs();
    }

    /**
     * Updates NPC equipment and refreshes visually.
     */
    public void updateNPCEquipment(@NotNull String id, @NotNull NPCEquipment equipment) {
        NPC npc = registry.getNPC(id);
        if (npc == null) return;

        npc.setEquipment(equipment);

        // Send equipment to all players INSTANTLY
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEquipmentPacket(player, npc);
        }

        refreshNPCForAllPlayers(npc);
        saveAllNPCs();
    }

    /**
     * Enables automatic look tracking for an NPC.
     * NPC will automatically look at nearest player within 5 blocks.
     */
    public void enableAutoLook(@NotNull String id) {
        autoLookEnabled.put(id, true);
        startLookTrackingTask();
    }

    /**
     * Disables automatic look tracking for an NPC.
     */
    public void disableAutoLook(@NotNull String id) {
        autoLookEnabled.remove(id);
    }

    public void updateNPCEquipment(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEquipmentPacket(player, npc);
        }
        saveAllNPCs();
    }

    /**
     * Starts the look tracking task (runs every 10 ticks = 0.5s).
     */
    private void startLookTrackingTask() {
        if (lookTrackingTask != null) return;

        lookTrackingTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (String npcId : autoLookEnabled.keySet()) {
                NPC npc = registry.getNPC(npcId);
                if (npc == null) continue;

                Player nearest = findNearestPlayer(npc, 5.0);
                if (nearest != null) {
                    // Make NPC look at nearest player for ALL viewers
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        lookHandler.lookAtPlayer(npc, viewer, nearest);
                    }
                } else {
                    // No player nearby, reset look
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        lookHandler.resetLook(npc, viewer);
                    }
                }
            }
        }, 0L, 10L); // Every 0.5 seconds
    }

    // In NPCManager.java
    public void refreshNPC(@NotNull String id) {
        NPC npc = registry.getNPC(id);
        if (npc == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEntityMetadataPacket(player, npc);
            if (npc.getEquipment().hasEquipment()) {
                sendEquipmentPacket(player, npc);
            }
        }
    }

    /**
     * Makes an NPC look at a player.
     */
    public void makeNPCLookAt(@NotNull String id, @NotNull Player viewer, @NotNull Location target) {
        NPC npc = registry.getNPC(id);
        if (npc == null) return;

        lookHandler.lookAt(npc, viewer, target);
    }

    public void makeNPCLookAtPlayer(@NotNull NPC npc, @NotNull Player target) {
        // Make NPC look at the target for all online players
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            lookHandler.lookAtPlayer(npc, viewer, target);
        }
    }

    /**
     * Finds the nearest player to an NPC within radius.
     */
    @Nullable
    private Player findNearestPlayer(@NotNull NPC npc, double maxDistance) {
        Location npcLoc = npc.getLocation();
        Player nearest = null;
        double nearestDistance = maxDistance;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(npcLoc.getWorld())) continue;

            double distance = player.getLocation().distance(npcLoc);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }


    /**
     * Resets NPC look direction.
     */
    public void resetNPCLook(@NotNull NPC npc) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            lookHandler.resetLook(npc, viewer);
        }
    }

    /**
     * Sends equipment packet to player.
     */
    private void sendEquipmentPacket(@NotNull Player player, @NotNull NPC npc) {
        if (!npc.getEquipment().hasEquipment()) {
            return;
        }

        try {
            PacketContainer packet = protocolManager.createPacket(
                    PacketType.Play.Server.ENTITY_EQUIPMENT
            );

            packet.getIntegers().write(0, npc.getEntityId());

            List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipment = new ArrayList<>();
            NPCEquipment eq = npc.getEquipment();

            if (eq.getMainHand() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, eq.getMainHand()));
            }
            if (eq.getOffHand() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, eq.getOffHand()));
            }
            if (eq.getHelmet() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.HEAD, eq.getHelmet()));
            }
            if (eq.getChestplate() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.CHEST, eq.getChestplate()));
            }
            if (eq.getLeggings() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.LEGS, eq.getLeggings()));
            }
            if (eq.getBoots() != null) {
                equipment.add(new Pair<>(EnumWrappers.ItemSlot.FEET, eq.getBoots()));
            }

            packet.getSlotStackPairLists().write(0, equipment);
            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send equipment: " + e.getMessage());
        }
    }

    /**
     * Enters editor mode for an NPC.
     */
    public void enterEditorMode(@NotNull Player player, @NotNull String npcId) {
        NPC npc = registry.getNPC(npcId);
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
            saveAllNPCs();
        }
    }

    /**
     * Gets the NPC being edited by a player.
     */
    @Nullable
    public NPC getEditingNPC(@NotNull Player player) {
        String npcId = editMode.get(player.getUniqueId());
        return npcId != null ? registry.getNPC(npcId) : null;  // ✅ USE REGISTRY
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
     * Spawns NPC for a specific player with proper pose and equipment.
     */
    public void spawnNPCForPlayer(@NotNull Player player, @NotNull NPC npc) {
        try {
            // 1. Add to tab list
            sendPlayerInfoPacket(player, npc, true);

            // 2. Spawn entity
            sendSpawnPlayerPacket(player, npc);

            // 3. Set metadata (pose + skin layer)
            sendEntityMetadataPacket(player, npc);

            // 4. Send equipment  ✅ ADD THIS
            if (npc.getEquipment().hasEquipment()) {
                sendEquipmentPacket(player, npc);
            }

            // 5. Remove from tab list
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInfoPacket(player, npc, false);
            }, 40L);

            // 6. Spawn hologram
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
     */
    private void sendEntityMetadataPacket(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer packet = protocolManager.createPacket(ENTITY_METADATA);
            packet.getIntegers().write(0, npc.getEntityId());

            WrappedDataWatcher watcher = new WrappedDataWatcher();

            // Index 17: Skin layers (byte)
            byte skinLayers = npc.getPose().isShowSecondLayer() ? (byte) 0x7F : (byte) 0x00;
            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(17,
                    WrappedDataWatcher.Registry.get(Byte.class)), skinLayers);

            // Pose data (indices 18-23 for 1.21.8)
            NPC.NPCPose pose = npc.getPose();

            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(18,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getHeadPitch(), pose.getHeadYaw(), pose.getHeadRoll()));

            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(19,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getBodyPitch(), pose.getBodyYaw(), pose.getBodyRoll()));

            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(20,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getLeftArmPitch(), pose.getLeftArmYaw(), pose.getLeftArmRoll()));

            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(21,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getRightArmPitch(), pose.getRightArmYaw(), pose.getRightArmRoll()));

            watcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(22,
                            WrappedDataWatcher.Registry.getVectorSerializer()),
                    createRotationVector(pose.getLeftLegPitch(), pose.getLeftLegYaw(), pose.getLeftLegRoll()));

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
     * Sets equipment for an NPC.
     */
    public void setNPCEquipment(@NotNull String id, @NotNull NPCEquipment equipment) {
        NPC npc = registry.getNPC(id);
        if (npc == null) return;

        npc.setEquipment(equipment);

        // Refresh equipment for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendEquipmentPacket(player, npc);
        }

        saveAllNPCs();
    }

    /**
     * Handles NPC interaction.
     */
    public void handleInteraction(@NotNull Player player, @NotNull NPC npc) {
        npc.getAction().execute(plugin, player);
    }

    /**
     * Saves all NPCs to storage.
     */
    private void saveAllNPCs() {
        storage.saveNPCs(new ArrayList<>(registry.getAllNPCs()));
    }

    // Public API methods
    @Nullable
    public NPC getNPCByEntityId(int entityId) {
        return registry.getNPCByEntityId(entityId);
    }

    @Nullable
    public NPC getNPC(@NotNull String id) {
        return registry.getNPC(id);
    }

    @NotNull
    public Collection<NPC> getAllNPCs() {
        return registry.getAllNPCs();
    }

    @NotNull
    public Collection<NPC> getNPCsInWorld(@NotNull String worldName) {
        return registry.getNPCsInWorld(worldName);
    }

    @NotNull
    public Collection<NPC> getNPCsNearby(@NotNull Location location, double radius) {
        return registry.getNPCsNearby(location, radius);
    }

    public void removeAllNPCs() {
        new ArrayList<>(registry.getAllIds()).forEach(this::removeNPC);
    }

    private static int generateEntityId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }
}