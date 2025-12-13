package com.yourserver.social.command;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.gui.GUIManager;
import com.yourserver.social.manager.ClanManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ClanCommand implements CommandExecutor, TabCompleter {
    public ClanCommand(SocialPlugin plugin, ClanManager clanManager, GUIManager guiManager) {
        // TODO: Implement clan commands
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player) {
            player.sendMessage("§cClan system not yet implemented!");
        }
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}