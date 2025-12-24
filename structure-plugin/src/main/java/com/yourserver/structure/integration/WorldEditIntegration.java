package com.yourserver.structure.integration;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.yourserver.structure.StructurePlugin;
import com.yourserver.structure.api.PlacementOptions;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Integration with WorldEdit for schematic operations.
 */
public class WorldEditIntegration {

    private final StructurePlugin plugin;
    private final WorldEdit worldEdit;

    public WorldEditIntegration(@NotNull StructurePlugin plugin) {
        this.plugin = plugin;
        this.worldEdit = WorldEdit.getInstance();
    }

    public void initialize() {
        plugin.getLogger().info("WorldEdit integration initialized");
    }

    /**
     * Pastes a schematic file at the given location.
     */
    public boolean pasteSchematic(@NotNull String filePath,
                                  @NotNull Location location,
                                  @NotNull PlacementOptions options) {

        File schematicFile = new File(plugin.getDataFolder(), filePath);

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic file not found: " + filePath);
            return false;
        }

        try {
            // Load clipboard
            Clipboard clipboard = loadSchematic(schematicFile);

            // Create edit session
            try (EditSession editSession = WorldEdit.getInstance()
                    .newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(location.getWorld()))
                    .maxBlocks(-1)
                    .build()) {

                // Create clipboard holder
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // Apply rotation if needed
                if (options.getRotation() != PlacementOptions.Rotation.NONE) {
                    AffineTransform transform = new AffineTransform();
                    transform = transform.rotateY(options.getRotation().getDegrees());
                    holder.setTransform(transform);
                }

                // Calculate paste location
                BlockVector3 to = BlockVector3.at(
                        location.getBlockX(),
                        location.getBlockY() + options.getYOffset(),
                        location.getBlockZ()
                );

                // Paste
                Operation operation = holder
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);

                plugin.getLogger().info("Successfully pasted schematic: " + filePath);
                return true;
            }

        } catch (IOException | WorldEditException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to paste schematic: " + filePath, e);
            return false;
        }
    }

    /**
     * Loads a schematic file into a Clipboard.
     */
    @NotNull
    private Clipboard loadSchematic(@NotNull File file) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);

        if (format == null) {
            throw new IOException("Unknown schematic format: " + file.getName());
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {

            return reader.read();
        }
    }
}