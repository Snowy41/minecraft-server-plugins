package com.yourserver.core.command;

import com.yourserver.core.CorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Core plugin administration command.
 * Provides reload, status, and debug subcommands.
 */
public class CoreCommand implements CommandExecutor, TabCompleter {

    private final CorePlugin plugin;

    public CoreCommand(CorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "debug" -> handleDebug(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("core.reload")) {
            sender.sendMessage(Component.text("No permission!")
                    .color(NamedTextColor.RED));
            return;
        }

        try {
            plugin.reloadConfiguration();
            sender.sendMessage(Component.text("Core plugin configuration reloaded!")
                    .color(NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration: " + e.getMessage())
                    .color(NamedTextColor.RED));
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("core.admin")) {
            sender.sendMessage(Component.text("No permission!")
                    .color(NamedTextColor.RED));
            return;
        }

        boolean dbConnected = plugin.getDatabaseManager().isConnected();
        boolean redisConnected = plugin.getRedisManager().isConnected();

        sender.sendMessage(Component.text("=== Core Plugin Status ===")
                .color(NamedTextColor.GOLD));

        sender.sendMessage(Component.text("Database: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(dbConnected ? "Connected" : "Disconnected")
                        .color(dbConnected ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.text("Redis: ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(redisConnected ? "Connected" : "Disconnected")
                        .color(redisConnected ? NamedTextColor.GREEN : NamedTextColor.RED)));

        sender.sendMessage(Component.text("Version: " + plugin.getDescription().getVersion())
                .color(NamedTextColor.GRAY));
    }

    private void handleDebug(CommandSender sender) {
        if (!sender.hasPermission("core.debug")) {
            sender.sendMessage(Component.text("No permission!")
                    .color(NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("=== Core Plugin Debug ===")
                .color(NamedTextColor.GOLD));

        // Show memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        sender.sendMessage(Component.text("Memory: " + usedMemory + "MB / " + maxMemory + "MB")
                .color(NamedTextColor.GRAY));

        // Show database config
        sender.sendMessage(Component.text("Database Host: " +
                        plugin.getDatabaseConfig().getMySQLConfig().getHost())
                .color(NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Redis Host: " +
                        plugin.getDatabaseConfig().getRedisConfig().getHost())
                .color(NamedTextColor.GRAY));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Core Plugin Commands ===")
                .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/core reload - Reload configuration")
                .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/core status - View plugin status")
                .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/core debug - View debug information")
                .color(NamedTextColor.GRAY));
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
            return Arrays.asList("reload", "status", "debug");
        }
        return List.of();
    }
}