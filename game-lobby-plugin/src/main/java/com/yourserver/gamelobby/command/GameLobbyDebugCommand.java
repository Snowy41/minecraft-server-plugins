package com.yourserver.gamelobby.command;

import com.yourserver.gamelobby.GameLobbyPlugin;
import com.yourserver.gamelobby.manager.GameServiceManager;
import com.yourserver.gamelobby.model.GameService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Diagnostic command to debug GameLobby issues.
 *
 * Usage: /gamelobby debug
 */
public class GameLobbyDebugCommand implements CommandExecutor {

    private final GameLobbyPlugin plugin;
    private final GameServiceManager serviceManager;

    public GameLobbyDebugCommand(@NotNull GameLobbyPlugin plugin, @NotNull GameServiceManager serviceManager) {
        this.plugin = plugin;
        this.serviceManager = serviceManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("gamelobby.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        sender.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§lGameLobby Diagnostic Report");
        sender.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("");

        // CorePlugin Status
        sender.sendMessage("§6CorePlugin Status:");
        if (plugin.getCorePlugin() != null) {
            sender.sendMessage("  §a✓ CorePlugin loaded");
            sender.sendMessage("  §a✓ Redis: " +
                    (plugin.getCorePlugin().getRedisManager().isConnected() ? "Connected" : "§cDisconnected"));
        } else {
            sender.sendMessage("  §c✗ CorePlugin not found!");
        }
        sender.sendMessage("");

        // CloudNet Detection
        sender.sendMessage("§6CloudNet Detection:");
        String serviceName = System.getProperty("cloudnet.service.name");
        String serviceGroup = System.getProperty("cloudnet.service.group");
        String serviceTask = System.getProperty("cloudnet.service.task");

        if (serviceName != null) {
            sender.sendMessage("  §a✓ CloudNet detected");
            sender.sendMessage("  §7Service: §f" + serviceName);
            sender.sendMessage("  §7Group: §f" + serviceGroup);
            sender.sendMessage("  §7Task: §f" + serviceTask);
        } else {
            sender.sendMessage("  §c✗ CloudNet not detected!");
            sender.sendMessage("  §7This might be running in local mode");
        }
        sender.sendMessage("");

        // Enabled Gamemodes
        sender.sendMessage("§6Enabled Gamemodes:");
        for (String gamemode : serviceManager.getEnabledGamemodes()) {
            var config = serviceManager.getGamemodeConfig(gamemode);
            sender.sendMessage("  §a• §f" + gamemode + " §7(" + config.getDisplayName() + ")");
            sender.sendMessage("    §7Channels: " + config.getStateChannel() + ", " + config.getHeartbeatChannel());
        }
        sender.sendMessage("");

        // Detected Services
        sender.sendMessage("§6Detected Services:");
        boolean foundServices = false;
        for (String gamemode : serviceManager.getEnabledGamemodes()) {
            var services = serviceManager.getServices(gamemode);
            if (!services.isEmpty()) {
                foundServices = true;
                sender.sendMessage("  §e" + gamemode + ":");
                for (GameService service : services) {
                    String status = service.isOnline() ? "§aOnline" : "§cOffline";
                    String joinable = service.isJoinable() ? "§a[Joinable]" : "§7[Locked]";
                    sender.sendMessage("    " + joinable + " §f" + service.getServiceName() + " " + status);
                    sender.sendMessage("      §7State: " + service.getState().getColoredName());
                    sender.sendMessage("      §7Players: " + service.getCurrentPlayers() + "/" + service.getMaxPlayers());
                    if (service.getAlivePlayers() > 0) {
                        sender.sendMessage("      §7Alive: " + service.getAlivePlayers());
                    }
                    sender.sendMessage("      §7Last Update: " +
                            (System.currentTimeMillis() - service.getLastUpdate().toEpochMilli()) + "ms ago");
                }
            }
        }

        if (!foundServices) {
            sender.sendMessage("  §c✗ No services detected!");
            sender.sendMessage("  §7Possible causes:");
            sender.sendMessage("    §71. Game servers not broadcasting state");
            sender.sendMessage("    §72. Redis channel mismatch");
            sender.sendMessage("    §73. CloudNet service names don't match prefix");
        }
        sender.sendMessage("");

        // Redis Test
        sender.sendMessage("§6Redis Test:");
        try {
            var redis = plugin.getCorePlugin().getRedisManager();
            redis.set("gamelobby:test", "working", 60);
            String result = redis.get("gamelobby:test");
            if ("working".equals(result)) {
                sender.sendMessage("  §a✓ Redis read/write working");
            } else {
                sender.sendMessage("  §c✗ Redis read/write failed");
            }
        } catch (Exception e) {
            sender.sendMessage("  §c✗ Redis test failed: " + e.getMessage());
        }
        sender.sendMessage("");

        // Recommendations
        sender.sendMessage("§6Troubleshooting Tips:");
        sender.sendMessage("  §71. Check that BattleRoyale server is running");
        sender.sendMessage("  §72. Verify Redis channels match in config.yml");
        sender.sendMessage("  §73. Check BattleRoyale logs for broadcast messages");
        sender.sendMessage("  §74. Ensure CloudNet service names match prefixes");
        sender.sendMessage("");

        sender.sendMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return true;
    }
}