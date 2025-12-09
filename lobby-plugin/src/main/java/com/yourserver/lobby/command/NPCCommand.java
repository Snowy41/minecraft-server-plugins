package com.yourserver.lobby.command;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.npc.CustomNPC;
import com.yourserver.lobby.npc.NPCManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command for managing custom NPCs.
 * /npc create <id> <name> - Creates NPC with player's skin
 * /npc remove <id>
 * /npc list
 * /npc action <id> <type> <data>
 * /npc hologram <id> add <line>
 * /npc hologram <id> clear
 * /npc teleporthere <id>
 */
public class NPCCommand implements CommandExecutor, TabCompleter {

    private final LobbyPlugin plugin;
    private final NPCManager npcManager;

    public NPCCommand(LobbyPlugin plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("lobby.npc")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "action" -> handleAction(sender, args);
            case "hologram", "holo" -> handleHologram(sender, args);
            case "teleporthere", "tphere" -> handleTeleportHere(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can create NPCs!", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /npc create <id> <username>",
                    NamedTextColor.RED
            ));
            sender.sendMessage(Component.text(
                    "Example: /npc create game_selector Steve",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "The NPC will use the skin of 'Steve' (real player username)",
                    NamedTextColor.GRAY
            ));
            return;
        }

        String id = args[1];
        String username = args[2];

        // Check if NPC already exists
        if (npcManager.getNPC(id) != null) {
            sender.sendMessage(Component.text("NPC with ID '" + id + "' already exists!", NamedTextColor.RED));
            return;
        }

        // Create NPC at player's location
        CustomNPC npc = new CustomNPC(
                id,
                username,
                player.getLocation(),
                null, // Skin will be fetched automatically
                null,
                new CustomNPC.NPCAction(CustomNPC.NPCAction.ActionType.MESSAGE, "§7This NPC has no action set!"),
                new ArrayList<>()
        );

        sender.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                        "<yellow>Creating NPC: <white>" + id + " <yellow>(fetching skin for " + username + "...)"
        ));

        npcManager.spawnNPC(npc);

        sender.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                        "<green>✓ Created NPC: <white>" + id
        ));
        sender.sendMessage(Component.text(
                "Next steps:",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "  1. Set an action: /npc action " + id + " GUI game_selector",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                "  2. Add hologram: /npc hologram " + id + " add <text>",
                NamedTextColor.GRAY
        ));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc remove <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];

        if (npcManager.getNPC(id) == null) {
            sender.sendMessage(Component.text("NPC '" + id + "' not found!", NamedTextColor.RED));
            sender.sendMessage(Component.text("Use /npc list to see all NPCs", NamedTextColor.GRAY));
            return;
        }

        npcManager.removeNPC(id);

        sender.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                        "<green>✓ Removed NPC: <white>" + id
        ));
    }

    private void handleList(CommandSender sender) {
        var npcs = npcManager.getAllNPCs();

        if (npcs.isEmpty()) {
            sender.sendMessage(Component.text("No NPCs found!", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Create one with: /npc create <id> <username>", NamedTextColor.GRAY));
            return;
        }

        sender.sendMessage(Component.text("=== NPCs (" + npcs.size() + ") ===", NamedTextColor.GOLD));

        for (CustomNPC npc : npcs.values()) {
            sender.sendMessage(Component.text(
                    "• " + npc.getId() + " §7- §f" + npc.getName() +
                            " §7(" + npc.getAction().getType() + ")",
                    NamedTextColor.GRAY
            ));

            // Show location
            var loc = npc.getLocation();
            sender.sendMessage(Component.text(
                    "  Location: " + loc.getWorld().getName() + " " +
                            loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                    NamedTextColor.DARK_GRAY
            ));
        }
    }

    private void handleAction(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "Usage: /npc action <id> <type> <data>",
                    NamedTextColor.RED
            ));
            sender.sendMessage(Component.text(
                    "Types:",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "  GUI <menu> - Opens a menu (game_selector, cosmetics, stats)",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "  MESSAGE <text> - Sends a message",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "  TELEPORT <world,x,y,z,yaw,pitch> - Teleports player",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "  COMMAND <command> - Runs a command",
                    NamedTextColor.GRAY
            ));
            return;
        }

        String id = args[1];
        String typeStr = args[2].toUpperCase();
        String data = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        CustomNPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC '" + id + "' not found!", NamedTextColor.RED));
            return;
        }

        try {
            CustomNPC.NPCAction.ActionType type = CustomNPC.NPCAction.ActionType.valueOf(typeStr);

            // Create new NPC with updated action
            CustomNPC updatedNPC = new CustomNPC(
                    npc.getId(),
                    npc.getName(),
                    npc.getLocation(),
                    npc.getSkinTexture(),
                    npc.getSkinSignature(),
                    new CustomNPC.NPCAction(type, data),
                    npc.getHologramLines()
            );

            npcManager.removeNPC(id);
            npcManager.spawnNPC(updatedNPC);

            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                            "<green>✓ Updated NPC action!"
            ));
            sender.sendMessage(Component.text(
                    "Action: " + type + " → " + data,
                    NamedTextColor.GRAY
            ));

        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(
                    "Invalid action type! Use: GUI, MESSAGE, TELEPORT, COMMAND",
                    NamedTextColor.RED
            ));
        }
    }

    private void handleHologram(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /npc hologram <id> <add|clear> [line]",
                    NamedTextColor.RED
            ));
            return;
        }

        String id = args[1];
        String action = args[2].toLowerCase();

        CustomNPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC '" + id + "' not found!", NamedTextColor.RED));
            return;
        }

        List<String> hologramLines = new ArrayList<>(npc.getHologramLines());

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /npc hologram <id> add <line>", NamedTextColor.RED));
                    return;
                }

                String line = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                hologramLines.add(line);

                sender.sendMessage(Component.text("✓ Added hologram line!", NamedTextColor.GREEN));
            }
            case "clear" -> {
                hologramLines.clear();
                sender.sendMessage(Component.text("✓ Cleared hologram lines!", NamedTextColor.GREEN));
            }
            default -> {
                sender.sendMessage(Component.text("Invalid action! Use: add, clear", NamedTextColor.RED));
                return;
            }
        }

        // Recreate NPC with updated hologram
        CustomNPC updatedNPC = new CustomNPC(
                npc.getId(),
                npc.getName(),
                npc.getLocation(),
                npc.getSkinTexture(),
                npc.getSkinSignature(),
                npc.getAction(),
                hologramLines
        );

        npcManager.removeNPC(id);
        npcManager.spawnNPC(updatedNPC);
    }

    private void handleTeleportHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc teleporthere <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        CustomNPC npc = npcManager.getNPC(id);

        if (npc == null) {
            sender.sendMessage(Component.text("NPC '" + id + "' not found!", NamedTextColor.RED));
            return;
        }

        // Recreate NPC at player's location
        CustomNPC updatedNPC = new CustomNPC(
                npc.getId(),
                npc.getName(),
                player.getLocation(),
                npc.getSkinTexture(),
                npc.getSkinSignature(),
                npc.getAction(),
                npc.getHologramLines()
        );

        npcManager.removeNPC(id);
        npcManager.spawnNPC(updatedNPC);

        sender.sendMessage(Component.text("✓ Teleported NPC to your location!", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== NPC Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/npc create <id> <username> - Create NPC with player skin", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc remove <id> - Remove NPC", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc list - List all NPCs", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc action <id> <type> <data> - Set NPC action", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc hologram <id> add <line> - Add hologram line", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc hologram <id> clear - Clear hologram", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/npc teleporthere <id> - Move NPC to you", NamedTextColor.GRAY));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list", "action", "hologram", "teleporthere");
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list")) {
            return new ArrayList<>(npcManager.getAllNPCs().keySet());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("action")) {
            return Arrays.asList("GUI", "MESSAGE", "TELEPORT", "COMMAND");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("hologram")) {
            return Arrays.asList("add", "clear");
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("action") && args[2].equalsIgnoreCase("GUI")) {
            return Arrays.asList("game_selector", "cosmetics", "stats");
        }

        return List.of();
    }
}