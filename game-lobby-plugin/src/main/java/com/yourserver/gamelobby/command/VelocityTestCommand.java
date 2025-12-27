package com.yourserver.gamelobby.command;

import com.yourserver.gamelobby.GameLobbyPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Debug command to test Velocity connection directly.
 *
 * Usage: /velocitytest <server>
 */
public class VelocityTestCommand implements CommandExecutor {

    private final GameLobbyPlugin plugin;

    public VelocityTestCommand(@NotNull GameLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /velocitytest <server>");
            player.sendMessage("§7Example: /velocitytest BattleRoyale-1");
            return true;
        }

        String targetServer = args[0];

        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§6Testing Velocity Connection");
        player.sendMessage("§7Target: §f" + targetServer);
        player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Test 1: Check if player can send plugin messages
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
            byte[] data = b.toByteArray();

            player.sendMessage("§aTest 1: Creating plugin message... §a✓");

            // Test 2: Try velocity:main
            boolean velocitySuccess = false;
            try {
                player.sendPluginMessage(plugin, "velocity:main", data);
                player.sendMessage("§aTest 2: Sent via velocity:main... §a✓");
                velocitySuccess = true;
            } catch (Exception e) {
                player.sendMessage("§cTest 2: velocity:main failed: " + e.getMessage());
            }

            // Test 3: Try BungeeCord
            boolean bungeecordSuccess = false;
            try {
                player.sendPluginMessage(plugin, "BungeeCord", data);
                player.sendMessage("§aTest 3: Sent via BungeeCord... §a✓");
                bungeecordSuccess = true;
            } catch (Exception e) {
                player.sendMessage("§cTest 3: BungeeCord failed: " + e.getMessage());
            }

            player.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (!velocitySuccess && !bungeecordSuccess) {
                player.sendMessage("§c✗ Both channels failed!");
                player.sendMessage("§7Check that channels are registered.");
            } else {
                player.sendMessage("§aMessage sent successfully!");
                player.sendMessage("§7If you didn't teleport, check:");
                player.sendMessage("§71. Velocity is receiving the message");
                player.sendMessage("§72. Server name is registered in Velocity");
                player.sendMessage("§73. Velocity logs for errors");
            }

        } catch (Exception e) {
            player.sendMessage("§c✗ Failed to create message: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }
}