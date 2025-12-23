package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.player.GamePlayer;
import com.yourserver.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all battle royale game instances.
 *
 * FIXED:
 * - Auto-removes games after they END
 * - Cleans up stuck ENDING games
 * - Properly handles game lifecycle
 */
public class GameManager {

    private final BattleRoyalePlugin plugin;
    private final CorePlugin corePlugin;

    private final Map<String, Game> games;
    private final Map<UUID, String> playerGames;

    private int gameCounter;

    public GameManager(@NotNull BattleRoyalePlugin plugin, @NotNull CorePlugin corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.games = new ConcurrentHashMap<>();
        this.playerGames = new ConcurrentHashMap<>();
        this.gameCounter = 0;

        startGameTickTask();
        startCleanupTask();
    }

    @NotNull
    public Game createGame() {
        return createGame(GameConfig.createDefault());
    }

    @NotNull
    public Game createGame(@NotNull GameConfig config) {
        String gameId = "game-" + (++gameCounter);
        Game game = new Game(gameId, plugin, config);

        games.put(gameId, game);

        plugin.getLogger().info("Created new game: " + gameId +
                " (min: " + config.getMinPlayers() + ", max: " + config.getMaxPlayers() + ")");

        return game;
    }

    public void deleteGame(@NotNull String gameId) {
        Game game = games.remove(gameId);

        if (game != null) {
            for (UUID uuid : game.getPlayers().keySet()) {
                playerGames.remove(uuid);
            }

            plugin.getLogger().info("Deleted game: " + gameId);
        }
    }

    // ===== PLAYER MANAGEMENT =====

    public boolean joinGame(@NotNull Player player, @NotNull Game game) {
        if (playerGames.containsKey(player.getUniqueId())) {
            return false;
        }

        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), player.getName());

        if (game.addPlayer(gamePlayer)) {
            playerGames.put(player.getUniqueId(), game.getId());
            return true;
        }

        return false;
    }

    public void leaveGame(@NotNull Player player) {
        String gameId = playerGames.remove(player.getUniqueId());

        if (gameId != null) {
            Game game = games.get(gameId);
            if (game != null) {
                game.removePlayer(player.getUniqueId());
            }
        }
    }

    @Nullable
    public Game getPlayerGame(@NotNull UUID playerUuid) {
        String gameId = playerGames.get(playerUuid);
        return gameId != null ? games.get(gameId) : null;
    }

    @Nullable
    public Game getPlayerGame(@NotNull Player player) {
        return getPlayerGame(player.getUniqueId());
    }

    public boolean isInGame(@NotNull UUID playerUuid) {
        return playerGames.containsKey(playerUuid);
    }

    public boolean isInGame(@NotNull Player player) {
        return isInGame(player.getUniqueId());
    }

    // ===== GAME QUERIES =====

    @Nullable
    public Game getGame(@NotNull String gameId) {
        return games.get(gameId);
    }

    @NotNull
    public Map<String, Game> getGames() {
        return Map.copyOf(games);
    }

    public int getGameCount() {
        return games.size();
    }

    @Nullable
    public Game findJoinableGame() {
        return games.values().stream()
                .filter(g -> g.getState().canJoin())
                .filter(g -> g.getPlayerCount() < g.getMaxPlayers())
                .findFirst()
                .orElse(null);
    }

    // ===== GAME TICK =====

    private void startGameTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Game game : games.values()) {
                if (game.shouldTriggerDeathmatch() && game.getState() == GameState.ACTIVE) {
                    game.setState(GameState.DEATHMATCH);
                }
            }
        }, 20L, 20L);
    }

    /**
     * NEW: Cleanup task that removes ENDING games after 15 seconds.
     * Prevents games from getting stuck in ENDING state forever.
     */
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<String> toRemove = new ArrayList<>();

            for (Game game : games.values()) {
                if (game.getState() == GameState.ENDING) {
                    if (game.getEndedAt() != null) {
                        long secondsSinceEnd = (System.currentTimeMillis() - game.getEndedAt().toEpochMilli()) / 1000;

                        if (secondsSinceEnd >= 15) {
                            plugin.getLogger().info("Auto-removing ended game: " + game.getId() +
                                    " (ended " + secondsSinceEnd + "s ago)");
                            toRemove.add(game.getId());
                        }
                    }
                }

                if (game.getState() == GameState.WAITING && game.getPlayerCount() == 0) {
                    long secondsSinceCreated = (System.currentTimeMillis() - game.getCreatedAt().toEpochMilli()) / 1000;

                    if (secondsSinceCreated >= 300) {
                        plugin.getLogger().info("Auto-removing empty game: " + game.getId() +
                                " (empty for " + secondsSinceCreated + "s)");
                        toRemove.add(game.getId());
                    }
                }
            }

            for (String gameId : toRemove) {
                deleteGame(gameId);
            }

        }, 20L * 5, 20L * 5);
    }

    public void shutdown() {
        for (Game game : games.values()) {
            if (game.getState().isInProgress()) {
                game.setState(GameState.ENDING);
            }
        }

        games.clear();
        playerGames.clear();

        plugin.getLogger().info("Game manager shut down");
    }
}