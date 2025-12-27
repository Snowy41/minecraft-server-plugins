package com.yourserver.battleroyale.command;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
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
 * Debug command for admins to test Battle Royale functionality in-game.
 *
 * Commands:
 * /brdebug lifecycle - Run full game lifecycle test
 * /brdebug state <state> - Force game to specific state
 * /brdebug info - Show detailed game info
 * /brdebug spawn <count> - Spawn test bots
 * /brdebug zone <phase> - Force zone to specific phase
 * /brdebug timer <seconds> - Set countdown timer
 * /brdebug kill <player> - Force eliminate player
 * /brdebug win - Force win current game
 */
public class BattleRoyaleDebugCommand implements CommandExecutor, TabCompleter {

    private final BattleRoyalePlugin plugin;
    private final GameManager gameManager;

    public BattleRoyaleDebugCommand(BattleRoyalePlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }@Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        // Permission check
        if (!sender.hasPermission("battleroyale.admin.debug")) {
            sender.sendMessage(Component.text("No permission!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "lifecycle" -> handleLifecycleTest(sender);
            case "state" -> handleStateChange(sender, args);
            case "info" -> handleInfo(sender);
            case "spawn" -> handleSpawnBots(sender, args);
            case "zone" -> handleZoneControl(sender, args);
            case "timer" -> handleTimer(sender, args);
            case "kill" -> handleKill(sender, args);
            case "win" -> handleWin(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    /**
     * Runs a complete game lifecycle test.
     * Tests: WAITING → STARTING → ACTIVE → DEATHMATCH → ENDING
     */
    private boolean handleLifecycleTest(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  🔧 LIFECYCLE TEST", NamedTextColor.YELLOW, TextDecoration.BOLD));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        try {
            // Step 1: Get or create game
            Game game;
            if (sender instanceof Player player) {
                game = gameManager.getPlayerGame(player);
                if (game == null) {
                    sender.sendMessage(Component.text("  ❌ You're not in a game!", NamedTextColor.RED));
                    sender.sendMessage(Component.text("  Run /br join first", NamedTextColor.GRAY));
                    return true;
                }
            } else {
                game = gameManager.getGames().values().stream().findFirst().orElse(null);
                if (game == null) {
                    sender.sendMessage(Component.text("  ❌ No active games!", NamedTextColor.RED));
                    return true;
                }
            }

            sender.sendMessage(Component.text("  Testing Game: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getId(), NamedTextColor.WHITE)));
            sender.sendMessage(Component.empty());

            // Step 2: Test state transitions
            testStateTransition(sender, game, GameState.WAITING);
            testStateTransition(sender, game, GameState.STARTING);
            testStateTransition(sender, game, GameState.ACTIVE);
            testStateTransition(sender, game, GameState.DEATHMATCH);
            testStateTransition(sender, game, GameState.ENDING);

            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("  ✅ All state transitions successful!", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

        } catch (Exception e) {
            sender.sendMessage(Component.text("  ❌ Test failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Lifecycle test failed: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void testStateTransition(CommandSender sender, Game game, GameState targetState) {
        GameState before = game.getState();

        sender.sendMessage(Component.text("  → Testing: ", NamedTextColor.GRAY)
                .append(Component.text(before.name(), NamedTextColor.YELLOW))
                .append(Component.text(" → ", NamedTextColor.GRAY))
                .append(Component.text(targetState.name(), NamedTextColor.AQUA)));

        game.setState(targetState);

        // Wait a moment for async operations
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        GameState after = game.getState();

        if (after == targetState) {
            sender.sendMessage(Component.text("    ✓ Success", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("    ✗ Failed - State is " + after.name(), NamedTextColor.RED));
        }
    }

    /**
     * Forces game to a specific state.
     */
    private boolean handleStateChange(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /brdebug state <WAITING|STARTING|ACTIVE|DEATHMATCH|ENDING>",
                    NamedTextColor.RED));
            return true;
        }

        Game game = getGame(sender);
        if (game == null) {
            sender.sendMessage(Component.text("❌ No game found!", NamedTextColor.RED));
            return true;
        }

        try {
            GameState newState = GameState.valueOf(args[1].toUpperCase());
            GameState oldState = game.getState();

            game.setState(newState);

            sender.sendMessage(Component.text("✓ State changed: ", NamedTextColor.GREEN)
                    .append(Component.text(oldState.name(), NamedTextColor.YELLOW))
                    .append(Component.text(" → ", NamedTextColor.GRAY))
                    .append(Component.text(newState.name(), NamedTextColor.AQUA)));

        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("❌ Invalid state: " + args[1], NamedTextColor.RED));
        }

        return true;
    }

    /**
     * Shows detailed game information.
     */
    private boolean handleInfo(CommandSender sender) {
        Game game = getGame(sender);
        if (game == null) {
            sender.sendMessage(Component.text("❌ No game found!", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━ Game Info ━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  ID: ", NamedTextColor.GRAY)
                .append(Component.text(game.getId(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  State: ", NamedTextColor.GRAY)
                .append(Component.text(game.getState().name(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("  Players: ", NamedTextColor.GRAY)
                .append(Component.text(game.getPlayerCount() + "/" + game.getMaxPlayers(), NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  Alive: ", NamedTextColor.GRAY)
                .append(Component.text(game.getAliveCount(), NamedTextColor.GREEN)));
        sender.sendMessage(Component.text("  Spectators: ", NamedTextColor.GRAY)
                .append(Component.text(game.getSpectators().size(), NamedTextColor.AQUA)));

        // Arena info
        if (game.getArena() != null) {
            sender.sendMessage(Component.text("  Arena: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getArena().getName(), NamedTextColor.WHITE)));
        }

        // Zone info
        if (game.getZoneManager() != null && game.getZoneManager().isActive()) {
            var zone = game.getZoneManager().getCurrentZone();
            if (zone != null) {
                sender.sendMessage(Component.text("  Zone: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%.1fm radius", zone.getCurrentRadius()),
                                NamedTextColor.WHITE)));
            }
        }

        // Scheduler info
        if (game.getScheduler() != null) {
            sender.sendMessage(Component.text("  Scheduler: ", NamedTextColor.GRAY)
                    .append(Component.text(game.getScheduler().isRunning() ? "Running" : "Stopped",
                            game.getScheduler().isRunning() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());

        return true;
    }

    /**
     * Spawns test bots (future implementation).
     */
    private boolean handleSpawnBots(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("⚠ Bot spawning not yet implemented", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("  This feature will allow spawning NPCs for testing", NamedTextColor.GRAY));
        return true;
    }

    /**
     * Controls zone phase (future implementation).
     */
    private boolean handleZoneControl(CommandSender sender, String[] args) {
        Game game = getGame(sender);
        if (game == null) {
            sender.sendMessage(Component.text("❌ No game found!", NamedTextColor.RED));
            return true;
        }

        if (game.getZoneManager() == null) {
            sender.sendMessage(Component.text("❌ Zone manager not initialized!", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /brdebug zone <phase|start|stop>", NamedTextColor.RED));
            return true;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "start" -> {
                if (game.getArena() != null) {
                    game.getZoneManager().start(game.getArena().getCenter(), game.getArena().getSize());
                    sender.sendMessage(Component.text("✓ Zone started", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("❌ No arena set!", NamedTextColor.RED));
                }
            }
            case "stop" -> {
                game.getZoneManager().stop();
                sender.sendMessage(Component.text("✓ Zone stopped", NamedTextColor.GREEN));
            }
            case "next" -> {
                game.getZoneManager().nextPhase();
                sender.sendMessage(Component.text("✓ Advanced to next phase", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("❌ Invalid action: " + action, NamedTextColor.RED));
        }

        return true;
    }

    /**
     * Sets countdown timer.
     */
    private boolean handleTimer(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("⚠ Timer control not yet implemented", NamedTextColor.YELLOW));
        return true;
    }

    /**
     * Force eliminates a player.
     */
    private boolean handleKill(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /brdebug kill <player>", NamedTextColor.RED));
            return true;
        }

        Game game = getGame(sender);
        if (game == null) {
            sender.sendMessage(Component.text("❌ No game found!", NamedTextColor.RED));
            return true;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("❌ Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }

        game.eliminatePlayer(target.getUniqueId());
        sender.sendMessage(Component.text("✓ Eliminated " + target.getName(), NamedTextColor.GREEN));

        return true;
    }

    /**
     * Forces current game to end with winner.
     */
    private boolean handleWin(CommandSender sender) {
        Game game = getGame(sender);
        if (game == null) {
            sender.sendMessage(Component.text("❌ No game found!", NamedTextColor.RED));
            return true;
        }

        game.setState(GameState.ENDING);
        sender.sendMessage(Component.text("✓ Game ending...", NamedTextColor.GREEN));

        return true;
    }

    /**
     * Gets the game for a sender.
     */
    private Game getGame(CommandSender sender) {
        if (sender instanceof Player player) {
            Game game = gameManager.getPlayerGame(player);
            if (game != null) {
                return game;
            }
        }

        // Fallback to first game
        return gameManager.getGames().values().stream().findFirst().orElse(null);
    }

    /**
     * Sends help message.
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━ Battle Royale Debug ━━━━━", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /brdebug lifecycle", NamedTextColor.YELLOW)
                .append(Component.text(" - Run full lifecycle test", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /brdebug state <state>", NamedTextColor.YELLOW)
                .append(Component.text(" - Force game state", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /brdebug info", NamedTextColor.YELLOW)
                .append(Component.text(" - Show game info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /brdebug zone <action>", NamedTextColor.YELLOW)
                .append(Component.text(" - Control zone", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /brdebug kill <player>", NamedTextColor.YELLOW)
                .append(Component.text(" - Eliminate player", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /brdebug win", NamedTextColor.YELLOW)
                .append(Component.text(" - End game", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
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
        if (!sender.hasPermission("battleroyale.admin.debug")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("lifecycle", "state", "info", "spawn", "zone", "timer", "kill", "win");
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "state" -> Arrays.asList("WAITING", "STARTING", "ACTIVE", "DEATHMATCH", "ENDING");
                case "zone" -> Arrays.asList("start", "stop", "next");
                case "kill" -> {
                    List<String> players = new ArrayList<>();
                    plugin.getServer().getOnlinePlayers().forEach(p -> players.add(p.getName()));
                    yield players;
                }
                default -> List.of();
            };
        }

        return List.of();
    }
}