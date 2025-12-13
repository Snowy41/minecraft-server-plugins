package com.yourserver.lobby.command;

import com.yourserver.lobby.LobbyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command to manually refresh all nametags and tab names.
 * Useful for testing or if nametags get out of sync.
 */
public class RefreshNametagsCommand implements CommandExecutor {

    private final LobbyPlugin plugin;

    public RefreshNametagsCommand(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.hasPermission("lobby.admin")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        // Get the rank display listener
        var corePlugin = plugin.getCorePlugin();
        if (corePlugin == null) {
            sender.sendMessage(Component.text("CorePlugin not found!", NamedTextColor.RED));
            return true;
        }

        // Refresh all players
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            // This will be handled by the RankDisplayListener we created
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Trigger a refresh by calling the listener directly
                // You'll need to store a reference to it in LobbyPlugin
                plugin.refreshPlayerDisplay(player);
            });
            count++;
        }

        sender.sendMessage(Component.text("Refreshed nametags for " + count + " players!", NamedTextColor.GREEN));
        return true;
    }
}