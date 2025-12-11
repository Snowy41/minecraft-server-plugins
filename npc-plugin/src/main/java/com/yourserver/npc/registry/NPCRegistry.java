package com.yourserver.npc.registry;

import com.yourserver.npc.model.NPC;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Centralized registry for all NPCs.
 * Thread-safe and provides efficient lookups by various criteria.
 */
public class NPCRegistry {

    // Primary storage
    private final Map<String, NPC> npcsById;

    // Index by entity ID for click detection
    private final Map<Integer, String> entityIdToNPCId;

    // Spatial index for nearby NPC queries
    private final Map<String, Set<String>> npcsByWorld;

    public NPCRegistry() {
        this.npcsById = new ConcurrentHashMap<>();
        this.entityIdToNPCId = new ConcurrentHashMap<>();
        this.npcsByWorld = new ConcurrentHashMap<>();
    }

    /**
     * Registers a new NPC.
     *
     * @param npc The NPC to register
     * @throws IllegalArgumentException if NPC with same ID already exists
     */
    public void register(@NotNull NPC npc) {
        if (npcsById.containsKey(npc.getId())) {
            throw new IllegalArgumentException("NPC already registered: " + npc.getId());
        }

        npcsById.put(npc.getId(), npc);
        entityIdToNPCId.put(npc.getEntityId(), npc.getId());

        // Add to world index
        String worldName = npc.getLocation().getWorld().getName();
        npcsByWorld.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet())
                .add(npc.getId());
    }

    /**
     * Unregisters an NPC.
     *
     * @param id The NPC ID
     * @return The removed NPC, or null if not found
     */
    @Nullable
    public NPC unregister(@NotNull String id) {
        NPC npc = npcsById.remove(id);
        if (npc == null) {
            return null;
        }

        entityIdToNPCId.remove(npc.getEntityId());

        String worldName = npc.getLocation().getWorld().getName();
        Set<String> worldNPCs = npcsByWorld.get(worldName);
        if (worldNPCs != null) {
            worldNPCs.remove(id);
            if (worldNPCs.isEmpty()) {
                npcsByWorld.remove(worldName);
            }
        }

        return npc;
    }

    /**
     * Gets an NPC by ID.
     */
    @Nullable
    public NPC getNPC(@NotNull String id) {
        return npcsById.get(id);
    }

    /**
     * Updates an NPC's location in the spatial index.
     * Call this when an NPC moves to keep the spatial index accurate.
     */
    public void updateLocation(@NotNull String id, @NotNull Location newLocation) {
        NPC npc = npcsById.get(id);
        if (npc == null) {
            return;
        }

        String oldWorld = npc.getLocation().getWorld().getName();
        String newWorld = newLocation.getWorld().getName();

        // If world changed, update world index
        if (!oldWorld.equals(newWorld)) {
            Set<String> oldWorldNPCs = npcsByWorld.get(oldWorld);
            if (oldWorldNPCs != null) {
                oldWorldNPCs.remove(id);
                if (oldWorldNPCs.isEmpty()) {
                    npcsByWorld.remove(oldWorld);
                }
            }

            npcsByWorld.computeIfAbsent(newWorld, k -> ConcurrentHashMap.newKeySet())
                    .add(id);
        }

        npc.setLocation(newLocation);
    }

    /**
     * Gets an NPC by entity ID (for click detection).
     */
    @Nullable
    public NPC getNPCByEntityId(int entityId) {
        String npcId = entityIdToNPCId.get(entityId);
        return npcId != null ? npcsById.get(npcId) : null;
    }

    /**
     * Gets all NPCs in a specific world.
     */
    @NotNull
    public Collection<NPC> getNPCsInWorld(@NotNull String worldName) {
        Set<String> npcIds = npcsByWorld.get(worldName);
        if (npcIds == null) {
            return Collections.emptyList();
        }

        return npcIds.stream()
                .map(npcsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Gets all NPCs within a radius of a location.
     */
    @NotNull
    public Collection<NPC> getNPCsNearby(@NotNull Location location, double radius) {
        String worldName = location.getWorld().getName();
        double radiusSquared = radius * radius;

        return getNPCsInWorld(worldName).stream()
                .filter(npc -> npc.getLocation().distanceSquared(location) <= radiusSquared)
                .collect(Collectors.toList());
    }

    /**
     * Gets all registered NPCs.
     */
    @NotNull
    public Collection<NPC> getAllNPCs() {
        return new ArrayList<>(npcsById.values());
    }

    /**
     * Checks if an NPC with the given ID exists.
     */
    public boolean exists(@NotNull String id) {
        return npcsById.containsKey(id);
    }

    /**
     * Gets the total number of registered NPCs.
     */
    public int size() {
        return npcsById.size();
    }

    /**
     * Clears all registered NPCs.
     */
    public void clear() {
        npcsById.clear();
        entityIdToNPCId.clear();
        npcsByWorld.clear();
    }

    /**
     * Gets all NPC IDs.
     */
    @NotNull
    public Set<String> getAllIds() {
        return new HashSet<>(npcsById.keySet());
    }
}