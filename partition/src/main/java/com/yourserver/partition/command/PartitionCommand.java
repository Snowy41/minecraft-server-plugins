package com.yourserver.partition.command;

import com.yourserver.partition.PartitionPlugin;
import com.yourserver.partition.manager.PartitionManager;
import com.yourserver.partition.model.Partition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for partition management.
 */
public class PartitionCommand implements CommandExecutor, TabCompleter {

    private final PartitionPlugin plugin;
    private final PartitionManager partitionManager;

    public PartitionCommand(PartitionPlugin plugin, PartitionManager partitionManager) {
        this.plugin = plugin;
        this.partitionManager = partitionManager;
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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "tp", "teleport" -> handleTeleport(sender, args);
            case "restart" -> handleRestart(sender, args);
            case "reload" -> handleReload(sender);
            case "worlds" -> handleWorlds(sender);
            case "plugins" -> handlePlugins(sender, args);
            case "add" -> handleAdd(sender, args);
            case "enable" -> handleEnable(sender, args);
            case "disable" -> handleDisable(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        if (!sender.hasPermission("partition.list")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        Collection<Partition> partitions = partitionManager.getAllPartitions();

        if (partitions.isEmpty()) {
            sender.sendMessage(Component.text("No partitions loaded!", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("=== Server Partitions (" + partitions.size() + ") ===",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        for (Partition partition : partitions) {
            int playerCount = partitionManager.getPlayersInPartition(partition.getId()).size();

            sender.sendMessage(Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(partition.getName(), NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Component.text(partition.getId(), NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GRAY)));

            sender.sendMessage(Component.text("  " + partition.getDescription(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Worlds: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(String.join(", ", partition.getWorlds()), NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Plugins: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(partition.getPlugins().size() + " loaded", NamedTextColor.WHITE)));
            sender.sendMessage(Component.text("  Players: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(String.valueOf(playerCount), NamedTextColor.WHITE)));
            sender.sendMessage(Component.empty());
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("partition.info")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /partition info <partition>", NamedTextColor.RED));
            return;
        }

        String partitionId = args[1];
        Partition partition = partitionManager.getPartition(partitionId);

        if (partition == null) {
            sender.sendMessage(Component.text("Partition not found: " + partitionId, NamedTextColor.RED));
            return;
        }

        List<Player> players = partitionManager.getPlayersInPartition(partitionId);
        Duration uptime = Duration.between(partition.getLastRestart(), Instant.now());

        sender.sendMessage(Component.text("=== Partition Info: " + partition.getName() + " ===",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("ID: ", NamedTextColor.GRAY)
                .append(Component.text(partition.getId(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Description: ", NamedTextColor.GRAY)
                .append(Component.text(partition.getDescription(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.text("Worlds (" + partition.getWorlds().size() + "):",
                NamedTextColor.AQUA, TextDecoration.BOLD));
        for (String worldName : partition.getWorlds()) {
            World world = Bukkit.getWorld(worldName);
            boolean loaded = world != null;
            sender.sendMessage(Component.text("  • " + worldName,
                    loaded ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.text("Plugins (" + partition.getPlugins().size() + "):",
                NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        for (String pluginName : partition.getPlugins()) {
            org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin(pluginName);
            boolean enabled = pl != null && pl.isEnabled();
            sender.sendMessage(Component.text("  • " + pluginName,
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.text("Players (" + players.size() + "):",
                NamedTextColor.YELLOW, TextDecoration.BOLD));
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("  None", NamedTextColor.GRAY));
        } else {
            for (Player player : players) {
                sender.sendMessage(Component.text("  • " + player.getName(), NamedTextColor.WHITE));
            }
        }
        sender.sendMessage(Component.empty());

        sender.sendMessage(Component.text("Uptime: ", NamedTextColor.GRAY)
                .append(Component.text(formatDuration(uptime), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("Persistent: ", NamedTextColor.GRAY)
                .append(Component.text(partition.isPersistent() ? "Yes" : "No",
                        partition.isPersistent() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport!", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("partition.teleport")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /partition tp <partition>", NamedTextColor.RED));
            return;
        }

        String partitionId = args[1];

        if (partitionManager.movePlayerToPartition(player, partitionId)) {
            player.sendMessage(Component.text("Teleported to partition: ", NamedTextColor.GREEN)
                    .append(Component.text(partitionId, NamedTextColor.YELLOW)));
        } else {
            player.sendMessage(Component.text("Failed to teleport to partition: " + partitionId,
                    NamedTextColor.RED));
        }
    }

    private void handleRestart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("partition.restart")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /partition restart <partition>", NamedTextColor.RED));
            return;
        }

        String partitionId = args[1];
        Partition partition = partitionManager.getPartition(partitionId);

        if (partition == null) {
            sender.sendMessage(Component.text("Partition not found: " + partitionId, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Restarting partition: " + partition.getName() + "...",
                NamedTextColor.YELLOW));

        // MUST restart synchronously on main thread!
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = partitionManager.restartPartition(partitionId);

            if (success) {
                sender.sendMessage(Component.text("✓ Partition restarted: " + partition.getName(),
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("✗ Failed to restart partition!",
                        NamedTextColor.RED));
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("partition.reload")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("Reloading partition configuration...", NamedTextColor.YELLOW));

        try {
            plugin.reloadConfiguration();
            sender.sendMessage(Component.text("✓ Configuration reloaded!", NamedTextColor.GREEN));
        } catch (Exception e) {
            sender.sendMessage(Component.text("✗ Failed to reload: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void handleWorlds(CommandSender sender) {
        if (!sender.hasPermission("partition.worlds")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("=== World → Partition Mapping ===",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        List<World> worlds = Bukkit.getWorlds();

        for (World world : worlds) {
            String partitionId = partitionManager.getPartitionForWorld(world.getName());

            if (partitionId != null) {
                Partition partition = partitionManager.getPartition(partitionId);
                String partitionName = partition != null ? partition.getName() : partitionId;

                sender.sendMessage(Component.text("• ", NamedTextColor.GRAY)
                        .append(Component.text(world.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(partitionName, NamedTextColor.GREEN))
                        .append(Component.text(" (" + partitionId + ")", NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(Component.text("• ", NamedTextColor.GRAY)
                        .append(Component.text(world.getName(), NamedTextColor.AQUA))
                        .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("(none)", NamedTextColor.RED)));
            }
        }
    }

    private void handlePlugins(CommandSender sender, String[] args) {
        if (!sender.hasPermission("partition.plugins")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /partition plugins <partition>", NamedTextColor.RED));
            return;
        }

        String partitionId = args[1];
        Set<String> plugins = plugin.getPluginIsolationManager().getPluginsInPartition(partitionId);

        sender.sendMessage(Component.text("=== Plugins in '" + partitionId + "' ===",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());

        if (plugins.isEmpty()) {
            sender.sendMessage(Component.text("No plugins configured for this partition", NamedTextColor.GRAY));
            return;
        }

        for (String pluginName : plugins) {
            Map<String, Object> info = plugin.getPluginIsolationManager().getPluginInfo(pluginName);
            boolean exists = (boolean) info.get("exists");
            boolean enabled = exists && (boolean) info.get("enabled");

            sender.sendMessage(Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(pluginName, enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text(" " + (enabled ? "✓" : "✗"),
                            enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command!", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("partition.add")) {
            player.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /partition add world [worldname] <partition>",
                    NamedTextColor.RED));
            player.sendMessage(Component.text("  If worldname is omitted, uses current world",
                    NamedTextColor.GRAY));
            return;
        }

        if (!args[1].equalsIgnoreCase("world")) {
            player.sendMessage(Component.text("Currently only 'world' can be added", NamedTextColor.RED));
            return;
        }

        // Determine world name and partition
        String worldName;
        String partitionId;

        if (args.length == 3) {
            // /partition add world <partition> - uses current world
            worldName = player.getWorld().getName();
            partitionId = args[2];
        } else {
            // /partition add world <worldname> <partition>
            worldName = args[2];
            partitionId = args[3];
        }

        player.sendMessage(Component.text("Feature coming soon: Add world '" + worldName +
                "' to partition '" + partitionId + "'", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("For now, please edit config.yml and use /partition reload",
                NamedTextColor.GRAY));
    }

    private void handleEnable(CommandSender sender, String[] args) {
        if (!sender.hasPermission("partition.plugins.manage")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 4 || !args[1].equalsIgnoreCase("plugin")) {
            sender.sendMessage(Component.text("Usage: /partition enable plugin <plugin> <partition>",
                    NamedTextColor.RED));
            return;
        }

        String pluginName = args[2];
        String partitionId = args[3];

        if (plugin.getPluginIsolationManager().enablePluginForPartition(pluginName, partitionId)) {
            sender.sendMessage(Component.text("✓ Enabled plugin '" + pluginName + "' for partition '" +
                    partitionId + "'", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("✗ Failed to enable plugin", NamedTextColor.RED));
        }
    }

    private void handleDisable(CommandSender sender, String[] args) {
        if (!sender.hasPermission("partition.plugins.manage")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return;
        }

        if (args.length < 4 || !args[1].equalsIgnoreCase("plugin")) {
            sender.sendMessage(Component.text("Usage: /partition disable plugin <plugin> <partition>",
                    NamedTextColor.RED));
            return;
        }

        String pluginName = args[2];
        String partitionId = args[3];

        if (plugin.getPluginIsolationManager().disablePluginForPartition(pluginName, partitionId)) {
            sender.sendMessage(Component.text("✓ Disabled plugin '" + pluginName + "' for partition '" +
                    partitionId + "'", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("✗ Failed to disable plugin", NamedTextColor.RED));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Partition Commands ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("/partition list", NamedTextColor.YELLOW)
                .append(Component.text(" - List all partitions", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition info <partition>", NamedTextColor.YELLOW)
                .append(Component.text(" - Show partition details", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition tp <partition>", NamedTextColor.YELLOW)
                .append(Component.text(" - Teleport to partition", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition restart <partition>", NamedTextColor.YELLOW)
                .append(Component.text(" - Restart partition", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition worlds", NamedTextColor.YELLOW)
                .append(Component.text(" - List world mappings", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition plugins <partition>", NamedTextColor.YELLOW)
                .append(Component.text(" - List partition plugins", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/partition reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload configuration", NamedTextColor.GRAY)));
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
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
        if (args.length == 1) {
            return Arrays.asList("list", "info", "tp", "restart", "reload", "worlds", "plugins",
                    "add", "enable", "disable");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("info") || subCommand.equals("tp") || subCommand.equals("restart") ||
                    subCommand.equals("plugins")) {
                return partitionManager.getAllPartitions().stream()
                        .map(Partition::getId)
                        .collect(Collectors.toList());
            }

            if (subCommand.equals("add")) {
                return List.of("world");
            }

            if (subCommand.equals("enable") || subCommand.equals("disable")) {
                return List.of("plugin");
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("world")) {
            // List all worlds OR partition names
            List<String> options = new ArrayList<>();
            options.addAll(Bukkit.getWorlds().stream().map(World::getName).toList());
            options.addAll(partitionManager.getAllPartitions().stream()
                    .map(Partition::getId).toList());
            return options;
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))
                && args[1].equalsIgnoreCase("plugin")) {
            return Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .map(org.bukkit.plugin.Plugin::getName)
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            if ((args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("disable"))
                    && args[1].equalsIgnoreCase("plugin")) {
                return partitionManager.getAllPartitions().stream()
                        .map(Partition::getId)
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("add") && args[1].equalsIgnoreCase("world")) {
                return partitionManager.getAllPartitions().stream()
                        .map(Partition::getId)
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}