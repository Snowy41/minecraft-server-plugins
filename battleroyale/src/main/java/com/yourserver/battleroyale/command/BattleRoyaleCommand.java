package com.yourserver.battleroyale.command;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
import com.yourserver.battleroyale.player.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main command for Battle Royale.
 *
 * Usage:
 * /br join - Join a game
 * /br leave - Leave current game
 * /br stats [player] - View statistics
 * /br forcestart - Force start game (admin)
 * /br stop - Stop game (admin)
 */
public class BattleRoyaleCommand implements CommandExecutor, TabCompleter {

    private final BattleRoyalePlugin plugin;
    private final GameManager gameManager;

    public BattleRoyaleCommand(BattleRoyalePlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
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

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "stats" -> handleStats(sender, args);
            case "forcestart" -> handleForceStart(sender);
            case "stop" -> handleStop(sender);
            case "list" -> handleList(sender);
            case "cloudnet" -> handleCloudNetDebug(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleCloudNetDebug(CommandSender sender) {
        if (plugin.getBroadcaster() != null) {
            plugin.getBroadcaster().getCloudNetDetector().debugProperties();
            sender.sendMessage("§aCheck console for CloudNet debug output");
        }
        return true;
    }
    /**
     * Handles /br join
     */
    private boolean handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can join games!", NamedTextColor.RED));
            return true;
        }

        // Check if already in game
        if (gameManager.isInGame(player)) {
            player.sendMessage(Component.text("You are already in a game!", NamedTextColor.RED));
            return true;
        }

        // Find or create a joinable game
        Game game = gameManager.findJoinableGame();
        if (game == null) {
            game = gameManager.createGame();
        }

        // Join the game
        if (gameManager.joinGame(player, game)) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            player.sendMessage(Component.text("  ⚔ Battle Royale", NamedTextColor.YELLOW, TextDecoration.BOLD));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  Game ID: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getId(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("  Players: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getPlayerCount() + "/" + game.getMaxPlayers(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("  Status: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getState().name(), NamedTextColor.YELLOW)));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("  Waiting for ", NamedTextColor.GRAY)
                    .append(Component.text(game.getMinPlayers() - game.getPlayerCount(), NamedTextColor.WHITE))
                    .append(Component.text(" more players...", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
            player.sendMessage(Component.empty());
        } else {
            player.sendMessage(Component.text("Failed to join game!", NamedTextColor.RED));
        }

        return true;
    }

    /**
     * Handles /br leave
     */
    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can leave games!", NamedTextColor.RED));
            return true;
        }

        if (!gameManager.isInGame(player)) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }

        gameManager.leaveGame(player);
        player.sendMessage(Component.text("You left the game!", NamedTextColor.YELLOW));

        return true;
    }

    /**
     * Handles /br stats [player]
     */
    private boolean handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view stats!", NamedTextColor.RED));
            return true;
        }

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }

        GamePlayer gamePlayer = game.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            return true;
        }

        // Display stats
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━ Your Stats ━━━━━━━━", NamedTextColor.AQUA));
        player.sendMessage(Component.text("  Kills: ", NamedTextColor.GRAY)
                .append(Component.text(gamePlayer.getKills(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Assists: ", NamedTextColor.GRAY)
                .append(Component.text(gamePlayer.getAssists(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Damage Dealt: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", gamePlayer.getDamageDealt()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Damage Taken: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", gamePlayer.getDamageTaken()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  KDA: ", NamedTextColor.GRAY)
                .append(Component.text(gamePlayer.getKDAString(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        player.sendMessage(Component.empty());

        return true;
    }

    /**
     * Handles /br forcestart (admin)
     */
    private boolean handleForceStart(CommandSender sender) {
        if (!sender.hasPermission("battleroyale.admin.forcestart")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this!", NamedTextColor.RED));
            return true;
        }

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }

        if (game.getState() != GameState.WAITING && game.getState() != GameState.STARTING) {
            player.sendMessage(Component.text("Game already started!", NamedTextColor.RED));
            return true;
        }

        // Force start
        game.setState(GameState.ACTIVE);
        player.sendMessage(Component.text("Game force started!", NamedTextColor.GREEN));

        return true;
    }

    /**
     * Handles /br stop (admin)
     */
    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("battleroyale.admin.stop")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this!", NamedTextColor.RED));
            return true;
        }

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED));
            return true;
        }

        // Stop game
        game.setState(GameState.ENDING);
        player.sendMessage(Component.text("Game stopped!", NamedTextColor.YELLOW));

        return true;
    }

    /**
     * Handles /br list
     */
    private boolean handleList(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━━ Active Games ━━━━━━", NamedTextColor.GOLD));

        var games = gameManager.getGames();
        if (games.isEmpty()) {
            sender.sendMessage(Component.text("  No active games", NamedTextColor.GRAY));
        } else {
            for (Game game : games.values()) {
                sender.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
                        .append(Component.text(game.getId(), NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.GRAY))
                        .append(Component.text(game.getState().name(), NamedTextColor.AQUA))
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(game.getPlayerCount() + "/" + game.getMaxPlayers(), NamedTextColor.WHITE))
                        .append(Component.text(")", NamedTextColor.GRAY)));
            }
        }

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        return true;
    }

    /**
     * Sends help message.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━ Battle Royale Commands ━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /br join", NamedTextColor.YELLOW)
                .append(Component.text(" - Join a game", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /br leave", NamedTextColor.YELLOW)
                .append(Component.text(" - Leave current game", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /br stats", NamedTextColor.YELLOW)
                .append(Component.text(" - View your stats", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /br list", NamedTextColor.YELLOW)
                .append(Component.text(" - List active games", NamedTextColor.GRAY)));

        if (sender.hasPermission("battleroyale.admin")) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("  Admin Commands:", NamedTextColor.RED));
            sender.sendMessage(Component.text("  /br forcestart", NamedTextColor.YELLOW)
                    .append(Component.text(" - Force start game", NamedTextColor.GRAY)));
            sender.sendMessage(Component.text("  /br stop", NamedTextColor.YELLOW)
                    .append(Component.text(" - Stop current game", NamedTextColor.GRAY)));
        }

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());
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
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "join", "leave", "stats", "list"
            ));

            if (sender.hasPermission("battleroyale.admin")) {
                completions.addAll(Arrays.asList("forcestart", "stop"));
            }

            return completions;
        }

        return List.of();
    }
}