package com.yourserver.structure.command;

import com.yourserver.structure.StructurePlugin;
import com.yourserver.structure.api.PlacementOptions;
import com.yourserver.structure.api.StructureRegistry;
import com.yourserver.structure.model.Structure;
import com.yourserver.structure.storage.StructureStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main command handler for structure operations.
 */
public class StructureCommand implements CommandExecutor, TabCompleter {

    private final StructurePlugin plugin;
    private final StructureRegistry registry;
    private final StructureStorage storage;

    public StructureCommand(@NotNull StructurePlugin plugin,
                            @NotNull StructureRegistry registry,
                            @NotNull StructureStorage storage) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "place" -> handlePlace(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        var structures = registry.getAllStructures();

        if (structures.isEmpty()) {
            sender.sendMessage(Component.text("No structures loaded", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("═══ Structures (" + structures.size() + ") ═══",
                NamedTextColor.GOLD));

        for (Structure structure : structures) {
            sender.sendMessage(Component.text("  • ", NamedTextColor.GRAY)
                    .append(Component.text(structure.getFullId(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(structure.getName(), NamedTextColor.GRAY)));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /structure info <id>", NamedTextColor.RED));
            return;
        }

        Structure structure = registry.getStructure(args[1]);

        if (structure == null) {
            sender.sendMessage(Component.text("Structure not found: " + args[1], NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("═══ Structure Info ═══", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  ID: ", NamedTextColor.GRAY)
                .append(Component.text(structure.getFullId(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Name: ", NamedTextColor.GRAY)
                .append(Component.text(structure.getName(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Format: ", NamedTextColor.GRAY)
                .append(Component.text(structure.getFormat().name(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Size: ", NamedTextColor.GRAY)
                .append(Component.text(structure.getDimensions().toString(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Times Placed: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(structure.getTimesPlaced()), NamedTextColor.WHITE)));
    }

    private void handlePlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can place structures", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /structure place <id>", NamedTextColor.RED));
            return;
        }

        Structure structure = registry.getStructure(args[1]);

        if (structure == null) {
            sender.sendMessage(Component.text("Structure not found: " + args[1], NamedTextColor.RED));
            return;
        }

        Location location = player.getLocation();

        sender.sendMessage(Component.text("Placing structure...", NamedTextColor.YELLOW));

        registry.placeStructure(location, structure, PlacementOptions.builder().build())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(Component.text("✓ Structure placed successfully!",
                                NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("✗ Failed to place structure",
                                NamedTextColor.RED));
                    }
                });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("structure.admin.reload")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        registry.reload();
        sender.sendMessage(Component.text("✓ Structures reloaded", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("═══ Structure Commands ═══", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /structure list", NamedTextColor.YELLOW)
                .append(Component.text(" - List all structures", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /structure info <id>", NamedTextColor.YELLOW)
                .append(Component.text(" - View structure info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /structure place <id>", NamedTextColor.YELLOW)
                .append(Component.text(" - Place structure at your location", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /structure reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload structures", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {

        if (args.length == 1) {
            return List.of("list", "info", "place", "reload");
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("info") ||
                args[0].equalsIgnoreCase("place"))) {
            return registry.getAllStructures().stream()
                    .map(Structure::getFullId)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}