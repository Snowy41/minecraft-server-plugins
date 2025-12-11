package com.yourserver.npc.command;

import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.manager.NPCManager;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.model.NPCEquipment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Enhanced NPC Command with editor mode.
 *
 * Commands:
 * /npc create <id> <username>
 * /npc remove <id>
 * /npc list
 * /npc edit <id> - Enter editor mode
 * /npc edit <part> <axis> <degrees> - Adjust pose (when in editor mode)
 * /npc edit done - Exit editor mode
 * /npc pose <id> <preset> - Apply preset pose
 * /npc move <id> - Move NPC to your location
 * /npc action <id> <type> <data>
 * /npc hologram <id> add <line>
 * /npc hologram <id> clear
 */
public class NPCCommand implements CommandExecutor, TabCompleter {

    private final NPCPlugin plugin;
    private final NPCManager npcManager;

    public NPCCommand(NPCPlugin plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("npc.admin")) {
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
            case "edit" -> handleEdit(sender, args);
            case "pose" -> handlePose(sender, args);
            case "move" -> handleMove(sender, args);
            case "action" -> handleAction(sender, args);
            case "hologram", "holo" -> handleHologram(sender, args);
            case "look" -> handleLook(sender, args);
            case "equip" -> handleEquip(sender, args);
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
            sender.sendMessage(Component.text("Usage: /npc create <id> <username>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        String username = args[2];

        if (npcManager.getNPC(id) != null) {
            sender.sendMessage(Component.text("NPC already exists!", NamedTextColor.RED));
            return;
        }

        NPC.Action defaultAction = new NPC.Action(NPC.ActionType.MESSAGE, "§7This NPC has no action set!");
        npcManager.createNPC(id, username, player.getLocation(), defaultAction);

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("✓ Created NPC: ", NamedTextColor.GREEN)
                .append(Component.text(id, NamedTextColor.WHITE, TextDecoration.BOLD)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Next steps:", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  1. ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/npc edit " + id, NamedTextColor.WHITE))
                .append(Component.text(" - Adjust pose", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  2. ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/npc action " + id + " GUI game_selector", NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  3. ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/npc hologram " + id + " add <text>", NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc remove <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        if (npcManager.getNPC(id) == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        npcManager.removeNPC(id);
        sender.sendMessage(Component.text("✓ Removed NPC: " + id, NamedTextColor.GREEN));
    }

    private void handleList(CommandSender sender) {
        var npcs = npcManager.getAllNPCs();

        if (npcs.isEmpty()) {
            sender.sendMessage(Component.text("No NPCs found!", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("=== NPCs (" + npcs.size() + ") ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        for (NPC npc : npcs) {
            sender.sendMessage(Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(npc.getId(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(npc.getName(), NamedTextColor.GRAY)));

            var loc = npc.getLocation();
            sender.sendMessage(Component.text("  Location: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(loc.getWorld().getName() + " " +
                                    loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                            NamedTextColor.GRAY)));
            sender.sendMessage(Component.empty());
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit NPCs!", NamedTextColor.RED));
            return;
        }

        // Check if already in editor mode
        if (npcManager.isInEditorMode(player)) {
            // /npc edit done - exit editor mode
            if (args.length == 2 && args[1].equalsIgnoreCase("done")) {
                npcManager.exitEditorMode(player);
                return;
            }

            // /npc edit <part> <axis> <degrees> - adjust pose
            if (args.length == 4) {
                handleEditAdjust(player, args);
                return;
            }

            sender.sendMessage(Component.text("Usage: /npc edit <part> <axis> <degrees>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Or: /npc edit done", NamedTextColor.GRAY));
            return;
        }

        // /npc edit <id> - enter editor mode
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc edit <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        npcManager.enterEditorMode(player, id);
    }

    private void handleEditAdjust(Player player, String[] args) {
        NPC npc = npcManager.getEditingNPC(player);
        if (npc == null) return;

        String part = args[1].toLowerCase();
        String axis = args[2].toLowerCase();
        float degrees;

        try {
            degrees = Float.parseFloat(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid number!", NamedTextColor.RED));
            return;
        }

        NPC.NPCPose pose = npc.getPose();

        // Apply adjustment based on part and axis
        switch (part) {
            case "head" -> {
                switch (axis) {
                    case "pitch" -> pose.setHeadPitch(degrees);
                    case "yaw" -> pose.setHeadYaw(degrees);
                    case "roll" -> pose.setHeadRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis! Use: pitch, yaw, roll", NamedTextColor.RED));
                        return;
                    }
                }
            }
            case "body" -> {
                switch (axis) {
                    case "pitch" -> pose.setBodyPitch(degrees);
                    case "yaw" -> pose.setBodyYaw(degrees);
                    case "roll" -> pose.setBodyRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            case "rightarm" -> {
                switch (axis) {
                    case "pitch" -> pose.setRightArmPitch(degrees);
                    case "yaw" -> pose.setRightArmYaw(degrees);
                    case "roll" -> pose.setRightArmRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            case "leftarm" -> {
                switch (axis) {
                    case "pitch" -> pose.setLeftArmPitch(degrees);
                    case "yaw" -> pose.setLeftArmYaw(degrees);
                    case "roll" -> pose.setLeftArmRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            case "rightleg" -> {
                switch (axis) {
                    case "pitch" -> pose.setRightLegPitch(degrees);
                    case "yaw" -> pose.setRightLegYaw(degrees);
                    case "roll" -> pose.setRightLegRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            case "leftleg" -> {
                switch (axis) {
                    case "pitch" -> pose.setLeftLegPitch(degrees);
                    case "yaw" -> pose.setLeftLegYaw(degrees);
                    case "roll" -> pose.setLeftLegRoll(degrees);
                    default -> {
                        player.sendMessage(Component.text("Invalid axis!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            default -> {
                player.sendMessage(Component.text("Invalid part! Use: head, body, rightarm, leftarm, rightleg, leftleg",
                        NamedTextColor.RED));
                return;
            }
        }

        npcManager.updateNPCPose(npc.getId(), pose);
        player.sendMessage(Component.text("✓ Updated ", NamedTextColor.GREEN)
                .append(Component.text(part + " " + axis, NamedTextColor.WHITE))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text(degrees + "°", NamedTextColor.YELLOW)));
    }

    private void handlePose(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /npc pose <id> <preset>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Presets: standing, sitting, waving, pointing, saluting, dabbing",
                    NamedTextColor.GRAY));
            return;
        }

        String id = args[1];
        String preset = args[2].toLowerCase();

        NPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        NPC.NPCPose pose = switch (preset) {
            case "standing" -> NPC.NPCPose.standing();
            case "sitting" -> NPC.NPCPose.sitting();
            case "waving" -> NPC.NPCPose.waving();
            case "pointing" -> NPC.NPCPose.pointing();
            case "saluting" -> NPC.NPCPose.saluting();
            case "dabbing" -> NPC.NPCPose.dabbing();
            default -> null;
        };

        if (pose == null) {
            sender.sendMessage(Component.text("Invalid preset!", NamedTextColor.RED));
            return;
        }

        npcManager.updateNPCPose(id, pose);
        sender.sendMessage(Component.text("✓ Applied pose: ", NamedTextColor.GREEN)
                .append(Component.text(preset, NamedTextColor.YELLOW)));
    }

    private void handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can move NPCs!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc move <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        NPC npc = npcManager.getNPC(id);

        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        // Remove and recreate at player location
        npcManager.removeNPC(id);
        npcManager.createNPC(id, npc.getName(), player.getLocation(), npc.getAction());

        // Re-apply pose and hologram
        NPC updatedNPC = npcManager.getNPC(id);
        updatedNPC.setPose(npc.getPose());
        for (String line : npc.getHologramLines()) {
            updatedNPC.addHologramLine(line);
        }

        sender.sendMessage(Component.text("✓ Moved NPC to your location!", NamedTextColor.GREEN));
    }

    private void handleAction(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("Usage: /npc action <id> <type> <data>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Types: GUI, MESSAGE, TELEPORT, COMMAND", NamedTextColor.GRAY));
            return;
        }

        String id = args[1];
        String typeStr = args[2].toUpperCase();
        String data = String.join(" ", Arrays.copyOfRange(args, 3, args.length));

        NPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        try {
            NPC.ActionType type = NPC.ActionType.valueOf(typeStr);
            NPC.Action newAction = new NPC.Action(type, data);

            npcManager.removeNPC(id);
            npcManager.createNPC(id, npc.getName(), npc.getLocation(), newAction);

            NPC updatedNPC = npcManager.getNPC(id);
            updatedNPC.setPose(npc.getPose());
            for (String line : npc.getHologramLines()) {
                updatedNPC.addHologramLine(line);
            }

            sender.sendMessage(Component.text("✓ Updated action!", NamedTextColor.GREEN));

        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid action type!", NamedTextColor.RED));
        }
    }

    private void handleHologram(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /npc hologram <id> <add|clear> [line]", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        String action = args[2].toLowerCase();

        NPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /npc hologram <id> add <line>", NamedTextColor.RED));
                    return;
                }
                String line = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                npc.addHologramLine(line);

                // Refresh NPC
                npcManager.removeNPC(id);
                npcManager.createNPC(id, npc.getName(), npc.getLocation(), npc.getAction());
                NPC updatedNPC = npcManager.getNPC(id);
                updatedNPC.setPose(npc.getPose());
                for (String l : npc.getHologramLines()) {
                    updatedNPC.addHologramLine(l);
                }

                sender.sendMessage(Component.text("✓ Added hologram line!", NamedTextColor.GREEN));
            }
            case "clear" -> {
                npc.clearHologramLines();
                npcManager.removeNPC(id);
                npcManager.createNPC(id, npc.getName(), npc.getLocation(), npc.getAction());
                sender.sendMessage(Component.text("✓ Cleared hologram!", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Invalid action! Use: add, clear", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("=== NPC Commands ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("/npc create <id> <username>", NamedTextColor.WHITE)
                .append(Component.text(" - Create NPC", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc remove <id>", NamedTextColor.WHITE)
                .append(Component.text(" - Remove NPC", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc list", NamedTextColor.WHITE)
                .append(Component.text(" - List all NPCs", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc edit <id>", NamedTextColor.WHITE)
                .append(Component.text(" - Enter editor mode", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc pose <id> <preset>", NamedTextColor.WHITE)
                .append(Component.text(" - Apply preset pose", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc move <id>", NamedTextColor.WHITE)
                .append(Component.text(" - Move NPC to you", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc action <id> <type> <data>", NamedTextColor.WHITE)
                .append(Component.text(" - Set action", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc hologram <id> add <line>", NamedTextColor.WHITE)
                .append(Component.text(" - Add hologram", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/npc equip <id> <slot>", NamedTextColor.WHITE)
                .append(Component.text(" - Equip item (hold item)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "list", "edit", "pose", "move",
                    "action", "hologram", "equip");
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("create") && !args[0].equalsIgnoreCase("list")) {
            return new ArrayList<>(npcManager.getAllNPCs().stream().map(NPC::getId).toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("edit") &&
                sender instanceof Player player && npcManager.isInEditorMode(player)) {
            return Arrays.asList("head", "body", "rightarm", "leftarm", "rightleg", "leftleg", "done");
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("pose")) {
                return Arrays.asList("standing", "sitting", "waving", "pointing", "saluting", "dabbing");
            }
            if (args[0].equalsIgnoreCase("action")) {
                return Arrays.asList("GUI", "MESSAGE", "TELEPORT", "COMMAND");
            }
            if (args[0].equalsIgnoreCase("hologram")) {
                return Arrays.asList("add", "clear");
            }
            if (args[0].equalsIgnoreCase("edit") && sender instanceof Player player &&
                    npcManager.isInEditorMode(player)) {
                return Arrays.asList("pitch", "yaw", "roll");
            }
            if(args[0].equalsIgnoreCase("equip") ) {
                return Arrays.asList("mainhand", "offhand", "helmet", "chestplate", "leggings", "boots");
            }
        }

        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("action") && args[2].equalsIgnoreCase("GUI")) {
                return Arrays.asList("game_selector", "cosmetics", "stats");
            }
            if (args[0].equalsIgnoreCase("edit") && sender instanceof Player player &&
                    npcManager.isInEditorMode(player)) {
                return Arrays.asList("0", "45", "90", "-45", "-90");
            }
        }

        return List.of();
    }

    private void handleLook(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /npc look <id>", NamedTextColor.RED));
            return;
        }

        String id = args[1];
        NPC npc = npcManager.getNPC(id);

        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        npcManager.makeNPCLookAtPlayer(npc, player);
        sender.sendMessage(Component.text("✓ NPC is now looking at you!", NamedTextColor.GREEN));
    }

    private void handleEquip(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can equip NPCs!", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /npc equip <id> <slot>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Slots: mainhand, offhand, helmet, chestplate, leggings, boots",
                    NamedTextColor.GRAY));
            return;
        }

        String id = args[1];
        String slot = args[2].toLowerCase();

        NPC npc = npcManager.getNPC(id);
        if (npc == null) {
            sender.sendMessage(Component.text("NPC not found!", NamedTextColor.RED));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sender.sendMessage(Component.text("Hold an item to equip!", NamedTextColor.RED));
            return;
        }

        NPCEquipment equipment = npc.getEquipment();

        switch (slot) {
            case "mainhand", "hand" -> equipment.setMainHand(item.clone());
            case "offhand" -> equipment.setOffHand(item.clone());
            case "helmet", "head" -> equipment.setHelmet(item.clone());
            case "chestplate", "chest" -> equipment.setChestplate(item.clone());
            case "leggings", "legs" -> equipment.setLeggings(item.clone());
            case "boots", "feet" -> equipment.setBoots(item.clone());
            default -> {
                sender.sendMessage(Component.text("Invalid slot!", NamedTextColor.RED));
                return;
            }
        }

        npcManager.setNPCEquipment(id, equipment);
        sender.sendMessage(Component.text("✓ Equipped " + item.getType().name() + " in " + slot,
                NamedTextColor.GREEN));
    }


}