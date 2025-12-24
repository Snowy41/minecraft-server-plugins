package com.yourserver.structure.api;

import com.yourserver.structure.StructurePlugin;
import com.yourserver.structure.model.Structure;
import com.yourserver.structure.storage.StructureStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Central registry for all structures.
 * Provides API for other plugins to register, load, and place structures.
 */
public class StructureRegistry {

    private final StructurePlugin plugin;
    private final StructureStorage storage;
    private final Logger logger;

    // Structure cache: namespace:id -> Structure
    private final Map<String, Structure> structures;

    // Tag index: tag -> List of structure IDs
    private final Map<String, List<String>> tagIndex;

    public StructureRegistry(StructurePlugin plugin, StructureStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = plugin.getLogger();
        this.structures = new ConcurrentHashMap<>();
        this.tagIndex = new ConcurrentHashMap<>();
    }

    /**
     * Initialize the registry by loading all structures from storage.
     */
    public void initialize() {
        logger.info("Initializing structure registry...");

        // Load all structures from disk
        List<Structure> loaded = storage.loadAllStructures();

        for (Structure structure : loaded) {
            registerStructure(structure);
        }

        logger.info("Loaded " + structures.size() + " structures");
        rebuildTagIndex();
    }

    /**
     * Register a structure in the registry.
     */
    public void registerStructure(@NotNull Structure structure) {
        String key = structure.getFullId();
        structures.put(key, structure);

        // Add to tag index
        for (String tag : structure.getTags()) {
            tagIndex.computeIfAbsent(tag, k -> new ArrayList<>()).add(key);
        }

        logger.fine("Registered structure: " + key);
    }

    /**
     * Get a structure by full ID (namespace:id).
     */
    @Nullable
    public Structure getStructure(@NotNull String fullId) {
        return structures.get(fullId);
    }

    /**
     * Get a structure by namespace and ID.
     */
    @Nullable
    public Structure getStructure(@NotNull String namespace, @NotNull String id) {
        return structures.get(namespace + ":" + id);
    }

    /**
     * Get all structures with a specific tag.
     */
    @NotNull
    public List<Structure> getStructuresByTag(@NotNull String tag) {
        List<String> ids = tagIndex.get(tag.toLowerCase());
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return ids.stream()
                .map(structures::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get all structures in a namespace.
     */
    @NotNull
    public List<Structure> getStructuresByNamespace(@NotNull String namespace) {
        return structures.values().stream()
                .filter(s -> s.getNamespace().equals(namespace))
                .collect(Collectors.toList());
    }

    /**
     * Search structures by name or tag.
     */
    @NotNull
    public List<Structure> searchStructures(@NotNull String query) {
        String lowerQuery = query.toLowerCase();

        return structures.values().stream()
                .filter(s ->
                        s.getName().toLowerCase().contains(lowerQuery) ||
                                s.getId().toLowerCase().contains(lowerQuery) ||
                                s.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(lowerQuery))
                )
                .collect(Collectors.toList());
    }

    /**
     * Place a structure at a location with default options.
     */
    @NotNull
    public CompletableFuture<Boolean> placeStructure(
            @NotNull Location location,
            @NotNull String fullId
    ) {
        return placeStructure(location, fullId, PlacementOptions.builder().build());
    }

    /**
     * Place a structure at a location with custom options.
     */
    @NotNull
    public CompletableFuture<Boolean> placeStructure(
            @NotNull Location location,
            @NotNull String fullId,
            @NotNull PlacementOptions options
    ) {
        Structure structure = getStructure(fullId);
        if (structure == null) {
            return CompletableFuture.completedFuture(false);
        }

        return placeStructure(location, structure, options);
    }

    /**
     * Place a structure object at a location.
     */
    @NotNull
    public CompletableFuture<Boolean> placeStructure(
            @NotNull Location location,
            @NotNull Structure structure,
            @NotNull PlacementOptions options
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StructurePlacement placement = new StructurePlacement(plugin);
                return placement.place(location, structure, options);
            } catch (Exception e) {
                logger.severe("Failed to place structure " + structure.getFullId() + ": " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Get all registered structures.
     */
    @NotNull
    public Collection<Structure> getAllStructures() {
        return Collections.unmodifiableCollection(structures.values());
    }

    /**
     * Get structure count.
     */
    public int getStructureCount() {
        return structures.size();
    }

    /**
     * Rebuild the tag index.
     */
    private void rebuildTagIndex() {
        tagIndex.clear();

        for (Structure structure : structures.values()) {
            for (String tag : structure.getTags()) {
                tagIndex.computeIfAbsent(tag.toLowerCase(), k -> new ArrayList<>())
                        .add(structure.getFullId());
            }
        }

        logger.fine("Tag index rebuilt: " + tagIndex.size() + " tags");
    }

    /**
     * Reload all structures from disk.
     */
    public void reload() {
        structures.clear();
        tagIndex.clear();
        initialize();
        logger.info("Structure registry reloaded");
    }

    /**
     * Shutdown the registry.
     */
    public void shutdown() {
        structures.clear();
        tagIndex.clear();
    }

    /**
     * Get the structure registry API from any plugin.
     *
     * Usage from other plugins:
     * ```java
     * StructureRegistry api = StructureRegistry.getAPI();
     * if (api != null) {
     *     api.placeStructure(location, "battleroyale:lobby");
     * }
     * ```
     */
    @Nullable
    public static StructureRegistry getAPI() {
        RegisteredServiceProvider<StructureRegistry> provider =
                Bukkit.getServicesManager().getRegistration(StructureRegistry.class);

        return provider != null ? provider.getProvider() : null;
    }
}
