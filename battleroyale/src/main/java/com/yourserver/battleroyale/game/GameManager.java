package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.player.GamePlayer;
import com.yourserver.core.CorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all battle royale game instances.
 *
 * Responsibilities:
 * - Create/delete games
 * - Track active games
 * - Player-to-game mapping
 * - Game lifecycle management
 */
public class GameManager {

    private final BattleRoyalePlugin plugin;
    private final CorePlugin corePlugin;

    // All active games (game ID -> Game)
    private final Map<String, Game> games;

    // Player to game mapping (player UUID -> game ID)
    private final Map<UUID, String> playerGames;

    // Game counter for unique IDs
    private int gameCounter;

    public GameManager(@NotNull BattleRoyalePlugin plugin, @NotNull CorePlugin corePlugin) {
        this.plugin = plugin;
        this.corePlugin = corePlugin;
        this.games = new ConcurrentHashMap<>();
        this.playerGames = new ConcurrentHashMap<>();
        this.gameCounter = 0;

        // Start game tick task (checks deathmatch triggers, etc.)
        startGameTickTask();
    }

    /**
     * Creates a new game with default configuration.
     */
    @NotNull
    public Game createGame() {
        return createGame(GameConfig.createDefault());
    }

    /**
     * Creates a new game with custom configuration.
     */
    @NotNull
    public Game createGame(@NotNull GameConfig config) {
        String gameId = "game-" + (++gameCounter);
        Game game = new Game(gameId, plugin, config);

        games.put(gameId, game);

        plugin.getLogger().info("Created new game: " + gameId +
                " (min: " + config.getMinPlayers() + ", max: " + config.getMaxPlayers() + ")");

        return game;
    }

    /**
     * Deletes a game and cleans up.
     */
    public void deleteGame(@NotNull String gameId) {
        Game game = games.remove(gameId);

        if (game != null) {
            // Remove all players from mapping
            for (UUID uuid : game.getPlayers().keySet()) {
                playerGames.remove(uuid);
            }

            // TODO: Cleanup world, arena, etc.

            plugin.getLogger().info("Deleted game: " + gameId);
        }
    }

    // ===== PLAYER MANAGEMENT =====

    /**
     * Adds a player to a game.
     */
    public boolean joinGame(@NotNull Player player, @NotNull Game game) {
        // Check if player is already in a game
        if (playerGames.containsKey(player.getUniqueId())) {
            return false;
        }

        // Create GamePlayer wrapper
        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), player.getName());

        // Add to game
        if (game.addPlayer(gamePlayer)) {
            playerGames.put(player.getUniqueId(), game.getId());

            // TODO: Teleport to pre-game lobby
            // TODO: Apply game effects (clear inventory, etc.)

            return true;
        }

        return false;
    }

    /**
     * Removes a player from their current game.
     */
    public void leaveGame(@NotNull Player player) {
        String gameId = playerGames.remove(player.getUniqueId());

        if (gameId != null) {
            Game game = games.get(gameId);
            if (game != null) {
                game.removePlayer(player.getUniqueId());

                // TODO: Restore player state
                // TODO: Teleport to lobby
            }
        }
    }

    /**
     * Gets the game a player is in.
     */
    @Nullable
    public Game getPlayerGame(@NotNull UUID playerUuid) {
        String gameId = playerGames.get(playerUuid);
        return gameId != null ? games.get(gameId) : null;
    }

    /**
     * Gets the game a player is in.
     */
    @Nullable
    public Game getPlayerGame(@NotNull Player player) {
        return getPlayerGame(player.getUniqueId());
    }

    /**
     * Checks if a player is in a game.
     */
    public boolean isInGame(@NotNull UUID playerUuid) {
        return playerGames.containsKey(playerUuid);
    }

    /**
     * Checks if a player is in a game.
     */
    public boolean isInGame(@NotNull Player player) {
        return isInGame(player.getUniqueId());
    }

    // ===== GAME QUERIES =====

    /**
     * Gets a game by ID.
     */
    @Nullable
    public Game getGame(@NotNull String gameId) {
        return games.get(gameId);
    }

    /**
     * Gets all active games.
     */
    @NotNull
    public Map<String, Game> getGames() {
        return Map.copyOf(games);
    }

    /**
     * Gets the number of active games.
     */
    public int getGameCount() {
        return games.size();
    }

    /**
     * Finds a game that players can join (WAITING state).
     */
    @Nullable
    public Game findJoinableGame() {
        return games.values().stream()
                .filter(g -> g.getState().canJoin())
                .filter(g -> g.getPlayerCount() < g.getMaxPlayers())
                .findFirst()
                .orElse(null);
    }

    // ===== GAME TICK =====

    /**
     * Starts the game tick task.
     * Checks for deathmatch triggers, game end conditions, etc.
     */
    private void startGameTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Game game : games.values()) {
                // Check deathmatch trigger
                if (game.shouldTriggerDeathmatch() && game.getState() == GameState.ACTIVE) {
                    game.setState(GameState.DEATHMATCH);
                }

                // TODO: Check other conditions
            }
        }, 20L, 20L); // Run every second
    }

    /**
     * Shuts down the game manager.
     */
    public void shutdown() {
        // End all active games
        for (Game game : games.values()) {
            if (game.getState().isInProgress()) {
                game.setState(GameState.ENDING);
            }
        }

        // Clear all mappings
        games.clear();
        playerGames.clear();

        plugin.getLogger().info("Game manager shut down");
    }
}