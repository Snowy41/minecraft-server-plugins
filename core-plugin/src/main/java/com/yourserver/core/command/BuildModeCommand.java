package com.yourserver.core.command;

import com.yourserver.core.CorePlugin;
import com.yourserver.core.buildmode.BuildModeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Build mode command - QoL for building team.
 *
 * Features:
 * - Toggleable (run again to restore previous state)
 * - Clears inventory
 * - Sets creative mode
 * - Gives WorldEdit axe
 * - Gives VoxelSniper arrow
 * - Full saturation/health
 */
public class BuildModeCommand implements CommandExecutor {

    private final CorePlugin plugin;
    private final BuildModeManager buildModeManager;

    public BuildModeCommand(CorePlugin plugin, BuildModeManager buildModeManager) {
        this.plugin = plugin;
        this.buildModeManager = buildModeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!",
                    NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("core.build")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        // Toggle: If already in build mode, restore previous state
        if (buildModeManager.isInBuildMode(player)) {
            buildModeManager.restoreState(player);

            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("⚒ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Build Mode Deactivated", NamedTextColor.YELLOW, TextDecoration.BOLD)));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                    .append(Component.text("Previous state restored", NamedTextColor.GRAY)));
            player.sendMessage(Component.empty());

            plugin.getLogger().info("Player " + player.getName() + " deactivated build mode");
            return true;
        }

        // Save current state before activating build mode
        buildModeManager.saveState(player);

        // Clear inventory
        player.getInventory().clear();

        // Set creative mode
        player.setGameMode(GameMode.CREATIVE);

        // Full health and food
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Reset flight
        player.setAllowFlight(true);
        player.setFlying(false);

        // Give WorldEdit axe (slot 0)
        ItemStack worldEditAxe = createWorldEditAxe();
        player.getInventory().setItem(0, worldEditAxe);

        // Give VoxelSniper arrow (slot 1)
        ItemStack voxelArrow = createVoxelSniperArrow();
        player.getInventory().setItem(1, voxelArrow);

        // Give VoxelSniper brush (slot 2)
        ItemStack voxelBrush = createVoxelSniperBrush();
        player.getInventory().setItem(2, voxelBrush);

        // Optional: Give compass for navigation (slot 8)
        ItemStack compass = createCompass();
        player.getInventory().setItem(8, compass);

        // Send success message
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("⚒ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Build Mode Activated", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                .append(Component.text("Gamemode: ", NamedTextColor.GRAY))
                .append(Component.text("Creative", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                .append(Component.text("Inventory: ", NamedTextColor.GRAY))
                .append(Component.text("Cleared", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  ✓ ", NamedTextColor.GREEN)
                .append(Component.text("Tools: ", NamedTextColor.GRAY))
                .append(Component.text("WorldEdit + VoxelSniper", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Tip: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Run /build again to restore your previous state",
                        NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());

        plugin.getLogger().info("Player " + player.getName() + " activated build mode");

        return true;
    }

    /**
     * Creates the WorldEdit selection axe.
     */
    private ItemStack createWorldEditAxe() {
        ItemStack axe = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = axe.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("WorldEdit Wand", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Left Click", NamedTextColor.YELLOW)
                            .append(Component.text(" - Set Position 1", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right Click", NamedTextColor.YELLOW)
                            .append(Component.text(" - Set Position 2", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("//wand to get another", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            axe.setItemMeta(meta);
        }

        return axe;
    }

    /**
     * Creates the VoxelSniper arrow (for right-click actions).
     */
    private ItemStack createVoxelSniperArrow() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("VoxelSniper Arrow", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Right Click", NamedTextColor.YELLOW)
                            .append(Component.text(" - Execute Brush", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("/b [brush] to select brush", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/v for VoxelSniper help", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            arrow.setItemMeta(meta);
        }

        return arrow;
    }

    /**
     * Creates the VoxelSniper brush (for left-click actions).
     */
    private ItemStack createVoxelSniperBrush() {
        ItemStack brush = new ItemStack(Material.FEATHER);
        ItemMeta meta = brush.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("VoxelSniper Brush", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Left Click", NamedTextColor.YELLOW)
                            .append(Component.text(" - Execute Brush", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("/b [brush] to select brush", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/v for VoxelSniper help", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            brush.setItemMeta(meta);
        }

        return brush;
    }

    /**
     * Creates a compass for quick navigation.
     */
    private ItemStack createCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("Navigator", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(java.util.List.of(
                    Component.empty(),
                    Component.text("Right Click", NamedTextColor.YELLOW)
                            .append(Component.text(" - Quick Menu", NamedTextColor.GRAY))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Use for quick navigation", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            compass.setItemMeta(meta);
        }

        return compass;
    }
}