package com.yourserver.lobby.command;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.spawn.SpawnManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Quick spawn teleport command.
 * /spawn
 */
public class SpawnCommand implements CommandExecutor {

    private final LobbyPlugin plugin;
    private final SpawnManager spawnManager;

    public SpawnCommand(LobbyPlugin plugin, SpawnManager spawnManager) {
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
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
}