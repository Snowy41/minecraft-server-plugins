package com.yourserver.structure.api;

import com.yourserver.structure.StructurePlugin;
import com.yourserver.structure.model.Structure;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Handles structure placement logic with various options.
 */
public class StructurePlacement {

    private final Plugin plugin;
    private final Logger logger;

    public StructurePlacement(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Places a structure at the given location with options.
     *
     * @param location Target location
     * @param structure Structure to place
     * @param options Placement options
     * @return true if successful
     */
    public boolean place(@NotNull Location location,
                         @NotNull Structure structure,
                         @NotNull PlacementOptions options) {

        long startTime = System.currentTimeMillis();

        try {
            logger.info("Placing structure: " + structure.getFullId() +
                    " at " + formatLocation(location));

            // Get the appropriate format handler
            switch (structure.getFormat()) {
                case WORLDEDIT -> {
                    return placeWorldEditStructure(location, structure, options);
                }
                case NBT -> {
                    return placeNBTStructure(location, structure, options);
                }
                case LEGACY -> {
                    return placeLegacyStructure(location, structure, options);
                }
                default -> {
                    logger.warning("Unsupported structure format: " + structure.getFormat());
                    return false;
                }
            }

        } catch (Exception e) {
            logger.severe("Failed to place structure " + structure.getFullId() + ": " + e.getMessage());
            e.printStackTrace();
            return false;

        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            structure.updateLoadTime(elapsed);
            structure.incrementPlacementCount();

            logger.fine("Structure placement completed in " + elapsed + "ms");
        }
    }

    private boolean placeWorldEditStructure(Location location, Structure structure, PlacementOptions options) {
        // Check if WorldEdit is available
        if (plugin instanceof StructurePlugin structPlugin) {
            if (structPlugin.hasWorldEdit()) {
                return structPlugin.getWorldEditIntegration()
                        .pasteSchematic(structure.getFilePath(), location, options);
            }
        }

        logger.warning("WorldEdit not available - cannot place .schem structure");
        return false;
    }

    private boolean placeNBTStructure(Location location, Structure structure, PlacementOptions options) {
        logger.warning("NBT format not yet implemented");
        return false;
    }

    private boolean placeLegacyStructure(Location location, Structure structure, PlacementOptions options) {
        logger.warning("Legacy structure format not yet implemented");
        return false;
    }

    private String formatLocation(Location loc) {
        return String.format("%.0f, %.0f, %.0f in %s",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }
}
