package com.yourserver.lobby.command;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.spawn.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Main lobby command for admin operations.
 * /lobby [reload|setspawn]
 */
public class LobbyCommand implements CommandExecutor, TabCompleter {

    private final LobbyPlugin plugin;
    private final SpawnManager spawnManager;

    public LobbyCommand(LobbyPlugin plugin, SpawnManager spawnManager) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        // No arguments - teleport to spawn
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can teleport!", NamedTextColor.RED));
                return true;
            }

            if (!player.hasPermission("lobby.spawn")) {
                player.sendMessage(Component.text(
                        plugin.getLobbyConfig().getMessagesConfig().getNoPermission(),
                        NamedTextColor.RED
                ));
                return true;
            }

            if (spawnManager.teleportToSpawn(player)) {
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                                plugin.getLobbyConfig().getMessagesConfig().getSpawnTeleport()
                ));
            } else {
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                                plugin.getLobbyConfig().getMessagesConfig().getSpawnNotSet()
                ));
            }
            return true;
        }

        // Subcommands
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "setspawn" -> handleSetSpawn(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("lobby.admin")) {
            sender.sendMessage(Component.text(
                    plugin.getLobbyConfig().getMessagesConfig().getNoPermission(),
                    NamedTextColor.RED
            ));
            return;
        }

        try {
            plugin.reloadConfiguration();
            sender.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                            plugin.getLobbyConfig().getMessagesConfig().getConfigReload()
            ));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to reload configuration!", NamedTextColor.RED));
            plugin.getLogger().severe("Failed to reload configuration: " + e.getMessage());
        }
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!sender.hasPermission("lobby.admin")) {
            sender.sendMessage(Component.text(
                    plugin.getLobbyConfig().getMessagesConfig().getNoPermission(),
                    NamedTextColor.RED
            ));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set spawn!", NamedTextColor.RED));
            return;
        }

        spawnManager.setSpawn(player);
        player.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getLobbyConfig().getMessagesConfig().getPrefix() +
                        plugin.getLobbyConfig().getMessagesConfig().getSpawnSet()
        ));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Lobby Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/lobby - Teleport to spawn", NamedTextColor.GRAY));

        if (sender.hasPermission("lobby.admin")) {
            sender.sendMessage(Component.text("/lobby setspawn - Set spawn location", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/lobby reload - Reload configuration", NamedTextColor.GRAY));
        }
    }

    @Override
    @Nullable
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1 && sender.hasPermission("lobby.admin")) {
            return Arrays.asList("reload", "setspawn");
        }
        return List.of();
    }
}