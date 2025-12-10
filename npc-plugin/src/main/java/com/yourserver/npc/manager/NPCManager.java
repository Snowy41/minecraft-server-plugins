package com.yourserver.npc.manager;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.storage.NPCStorage;
import com.yourserver.npc.util.SkinFetcher;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.comphenix.protocol.PacketType.Play.Server.*;

/**
 * Manages real player NPCs using ProtocolLib packets.
 * NPCs are CLIENT-SIDE ONLY (no server entity).
 */
public class NPCManager {

    private final NPCPlugin plugin;
    private final NPCStorage storage;
    private final ProtocolManager protocolManager;
    private final Map<String, NPC> npcs; // NPC ID -> NPC
    private final Map<Integer, String> entityIdToNPC; // Entity ID -> NPC ID

    public NPCManager(NPCPlugin plugin, NPCStorage storage, ProtocolManager protocolManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.protocolManager = protocolManager;
        this.npcs = new ConcurrentHashMap<>();
        this.entityIdToNPC = new ConcurrentHashMap<>();
    }

    /**
     * Loads all NPCs from storage and spawns them.
     */
    public void loadAllNPCs() {
        List<NPC> loadedNPCs = storage.loadNPCs();

        plugin.getLogger().info("Loading " + loadedNPCs.size() + " NPCs from storage...");

        for (NPC npc : loadedNPCs) {
            // Fetch skin if needed
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
     * Creates and spawns a new NPC.
     */
    public void createNPC(@NotNull String id, @NotNull String playerName,
                          @NotNull Location location, @NotNull NPC.Action action) {
        if (npcs.containsKey(id)) {
            plugin.getLogger().warning("NPC already exists: " + id);
            return;
        }

        NPC npc = new NPC(id, playerName, location, action);

        plugin.getLogger().info("Creating NPC: " + id + " (" + playerName + ")");

        // Fetch skin asynchronously
        SkinFetcher.fetchSkinAsync(playerName).thenAccept(skinData -> {
            if (skinData != null) {
                npc.setSkin(skinData[0], skinData[1]);
                plugin.getLogger().info("  ✓ Fetched skin for: " + playerName);
            } else {
                plugin.getLogger().warning("  ✗ Could not fetch skin for: " + playerName);
            }

            // Spawn for all online players
            spawnNPCForAllPlayers(npc);

            // Save to storage
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
        if (npc == null) {
            plugin.getLogger().warning("NPC not found: " + id);
            return;
        }

        entityIdToNPC.remove(npc.getEntityId());

        // Despawn for all players
        despawnNPCForAllPlayers(npc);

        // Save to storage
        storage.saveNPCs(new ArrayList<>(npcs.values()));

        plugin.getLogger().info("✓ Removed NPC: " + id);
    }

    /**
     * Spawns an NPC for all online players.
     */
    private void spawnNPCForAllPlayers(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnNPCForPlayer(player, npc);
        }
    }

    /**
     * Spawns an NPC for a specific player using packets.
     */
    public void spawnNPCForPlayer(@NotNull Player player, @NotNull NPC npc) {
        try {
            // 1. Add player to tab list
            sendPlayerInfoPacket(player, npc, true);

            // 2. Spawn player entity
            sendSpawnPlayerPacket(player, npc);

            // 3. Set head rotation
            sendHeadRotationPacket(player, npc);

            // 4. Remove from tab list after delay (so client renders the entity)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                sendPlayerInfoPacket(player, npc, false);
            }, 40L); // 2 second delay

            // 5. Spawn hologram if present
            if (!npc.getHologramLines().isEmpty()) {
                spawnHologramForPlayer(player, npc);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to spawn NPC for player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Despawns an NPC for all players.
     */
    private void despawnNPCForAllPlayers(@NotNull NPC npc) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            despawnNPCForPlayer(player, npc);
        }
    }

    /**
     * Despawns an NPC for a specific player.
     */
    private void despawnNPCForPlayer(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer destroyPacket = protocolManager.createPacket(ENTITY_DESTROY);
            destroyPacket.getIntLists().write(0, List.of(npc.getEntityId()));
            protocolManager.sendServerPacket(player, destroyPacket);

            // Despawn hologram
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
     * Sends player info packet (add to tab list).
     */
    private void sendPlayerInfoPacket(@NotNull Player player, @NotNull NPC npc, boolean add) {
        try {
            if (add) {
                // ADD player to tab list using PLAYER_INFO_UPDATE (1.19.3+)
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER);

                // Create WrappedGameProfile directly
                WrappedGameProfile gameProfile = new WrappedGameProfile(npc.getUuid(), npc.getName());

                // Add skin properties
                if (npc.hasSkin()) {
                    gameProfile.getProperties().put("textures",
                            new WrappedSignedProperty("textures", npc.getSkinTexture(), npc.getSkinSignature())
                    );
                }

                // Set the action - ADD_PLAYER
                packet.getPlayerInfoActions().write(0, EnumSet.of(
                        EnumWrappers.PlayerInfoAction.ADD_PLAYER,
                        EnumWrappers.PlayerInfoAction.UPDATE_LISTED
                ));

                // Create player info data
                PlayerInfoData data = new PlayerInfoData(
                        gameProfile,
                        0, // Ping
                        EnumWrappers.NativeGameMode.SURVIVAL,
                        WrappedChatComponent.fromText(npc.getName()),
                        null // No remote chat session
                );

                packet.getPlayerInfoDataLists().write(1, List.of(data));

                protocolManager.sendServerPacket(player, packet);

            } else {
                // REMOVE player from tab list using PLAYER_INFO_REMOVE (1.19.3+)
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);

                // Just need the UUID to remove
                packet.getUUIDLists().write(0, List.of(npc.getUuid()));

                protocolManager.sendServerPacket(player, packet);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send player info packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends spawn player packet.
     */
    private void sendSpawnPlayerPacket(@NotNull Player player, @NotNull NPC npc) {
        try {
            // FIX 2: In 1.21+, use SPAWN_ENTITY instead of NAMED_ENTITY_SPAWN
            PacketContainer packet = protocolManager.createPacket(SPAWN_ENTITY);

            Location loc = npc.getLocation();

            packet.getIntegers().write(0, npc.getEntityId()); // Entity ID
            packet.getUUIDs().write(0, npc.getUuid()); // UUID

            // Set entity type to PLAYER (use the correct ID for players)
            packet.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);

            packet.getDoubles().write(0, loc.getX()); // X
            packet.getDoubles().write(1, loc.getY()); // Y
            packet.getDoubles().write(2, loc.getZ()); // Z

            // Velocity (0, 0, 0)
            packet.getIntegers().write(1, 0); // Velocity X
            packet.getIntegers().write(2, 0); // Velocity Y
            packet.getIntegers().write(3, 0); // Velocity Z

            packet.getBytes().write(0, (byte) ((loc.getYaw() * 256.0F) / 360.0F)); // Yaw
            packet.getBytes().write(1, (byte) ((loc.getPitch() * 256.0F) / 360.0F)); // Pitch
            packet.getBytes().write(2, (byte) ((loc.getYaw() * 256.0F) / 360.0F)); // Head yaw

            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send spawn packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Sends head rotation packet.
     */
    private void sendHeadRotationPacket(@NotNull Player player, @NotNull NPC npc) {
        try {
            PacketContainer packet = protocolManager.createPacket(ENTITY_HEAD_ROTATION);

            Location loc = npc.getLocation();

            packet.getIntegers().write(0, npc.getEntityId());
            packet.getBytes().write(0, (byte) ((loc.getYaw() * 256.0F) / 360.0F));

            protocolManager.sendServerPacket(player, packet);

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send head rotation: " + e.getMessage());
        }
    }

    /**
     * Spawns hologram text above NPC.
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
                // Spawn armor stand (invisible, with custom name)
                PacketContainer spawnPacket = protocolManager.createPacket(SPAWN_ENTITY);
                spawnPacket.getIntegers().write(0, entityId);
                spawnPacket.getUUIDs().write(0, UUID.randomUUID());
                spawnPacket.getEntityTypeModifier().write(0, EntityType.ARMOR_STAND);
                spawnPacket.getDoubles().write(0, lineLoc.getX());
                spawnPacket.getDoubles().write(1, lineLoc.getY());
                spawnPacket.getDoubles().write(2, lineLoc.getZ());

                protocolManager.sendServerPacket(player, spawnPacket);

                // Set metadata (invisible, custom name visible, small)
                PacketContainer metaPacket = protocolManager.createPacket(ENTITY_METADATA);
                metaPacket.getIntegers().write(0, entityId);

                WrappedDataWatcher watcher = new WrappedDataWatcher();
                WrappedDataWatcher.Serializer chatSerializer =
                        WrappedDataWatcher.Registry.getChatComponentSerializer(true);
                WrappedDataWatcher.Serializer byteSerializer =
                        WrappedDataWatcher.Registry.get(Byte.class);

                watcher.setObject(0, byteSerializer, (byte) 0x20); // Invisible
                watcher.setObject(2, chatSerializer,
                        Optional.of(WrappedChatComponent.fromText(line).getHandle()));
                watcher.setObject(3, WrappedDataWatcher.Registry.get(Boolean.class), true); // Custom name visible
                watcher.setObject(15, byteSerializer, (byte) 0x01); // Small

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

    /**
     * Gets an NPC by entity ID (for click detection).
     */
    @Nullable
    public NPC getNPCByEntityId(int entityId) {
        String npcId = entityIdToNPC.get(entityId);
        return npcId != null ? npcs.get(npcId) : null;
    }

    /**
     * Gets an NPC by ID.
     */
    @Nullable
    public NPC getNPC(@NotNull String id) {
        return npcs.get(id);
    }

    /**
     * Gets all NPCs.
     */
    @NotNull
    public Collection<NPC> getAllNPCs() {
        return new ArrayList<>(npcs.values());
    }

    /**
     * Removes all NPCs.
     */
    public void removeAllNPCs() {
        new ArrayList<>(npcs.keySet()).forEach(this::removeNPC);
    }

    /**
     * Generates a unique entity ID.
     */
    private static int generateEntityId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }
}