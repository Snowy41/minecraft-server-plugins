package com.yourserver.gamelobby.command;

import com.yourserver.gamelobby.manager.GameMenuManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Generic command for opening gamemode menus.
 *
 * Commands:
 * - /battleroyale (opens BattleRoyale menu)
 * - /skywars (opens SkyWars menu)
 * - /bedwars (opens BedWars menu)
 * - etc.
 */
public class GameMenuCommand implements CommandExecutor, TabCompleter {

    private final GameMenuManager menuManager;
    private final String gamemodeId;

    public GameMenuCommand(@NotNull GameMenuManager menuManager, @NotNull String gamemodeId) {
        this.menuManager = menuManager;
        this.gamemodeId = gamemodeId;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Only players can use this command
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        // Open gamemode menu
        menuManager.openGameMenu(player, gamemodeId);

        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}