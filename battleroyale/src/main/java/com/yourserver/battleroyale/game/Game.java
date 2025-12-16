package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.player.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents a single battle royale game instance.
 *
 * Lifecycle:
 * 1. Create game (WAITING state)
 * 2. Players join pre-game lobby
 * 3. Start countdown when min players reached (STARTING)
 * 4. Players drop into map (ACTIVE)
 * 5. Zone shrinks, players fight
 * 6. After time/small zone: Deathmatch (DEATHMATCH)
 * 7. Winner determined, stats saved (ENDING)
 * 8. Game cleanup
 */
public class Game {

    private final String id;
    private final BattleRoyalePlugin plugin;

    // Game state
    private GameState state;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;

    // World and arena
    private World gameWorld;
    private GameConfig config;

    // Players
    private final Map<UUID, GamePlayer> players;
    private final Set<UUID> alivePlayers;
    private final Set<UUID> spectators;

    // Game settings
    private final int minPlayers;
    private final int maxPlayers;
    private final long gameDuration; // milliseconds (1 hour default)

    // Winner
    private UUID winner;

    public Game(@NotNull String id,
                @NotNull BattleRoyalePlugin plugin,
                @NotNull GameConfig config) {
        this.id = Objects.requireNonNull(id);
        this.plugin = Objects.requireNonNull(plugin);
        this.config = Objects.requireNonNull(config);

        this.state = GameState.WAITING;
        this.createdAt = Instant.now();

        this.players = new ConcurrentHashMap<>();
        this.alivePlayers = Collections.synchronizedSet(new HashSet<>());
        this.spectators = Collections.synchronizedSet(new HashSet<>());

        this.minPlayers = config.getMinPlayers();
        this.maxPlayers = config.getMaxPlayers();
        this.gameDuration = config.getGameDuration();
    }

    // ===== STATE MANAGEMENT =====

    /**
     * Transitions the game to a new state.
     */
    public void setState(@NotNull GameState newState) {
        GameState oldState = this.state;
        this.state = newState;

        plugin.getLogger().info("Game " + id + " state: " + oldState + " → " + newState);

        // Handle state entry
        switch (newState) {
            case STARTING -> onStarting();
            case ACTIVE -> onActive();
            case DEATHMATCH -> onDeathmatch();
            case ENDING -> onEnding();
        }
    }

    private void onStarting() {
        // Start countdown timer
        plugin.getLogger().info("Game " + id + " starting countdown...");
    }

    private void onActive() {
        this.startedAt = Instant.now();
        plugin.getLogger().info("Game " + id + " is now ACTIVE!");

        // Initialize alive players
        alivePlayers.addAll(players.keySet());

        // TODO: Start zone shrinking
        // TODO: Start game duration timer
    }

    private void onDeathmatch() {
        plugin.getLogger().info("Game " + id + " entering DEATHMATCH phase!");

        // TODO: Teleport remaining players to deathmatch arena
        // TODO: Remove zone damage
        // TODO: Force PvP
    }

    private void onEnding() {
        this.endedAt = Instant.now();
        plugin.getLogger().info("Game " + id + " is ENDING. Winner: " +
                (winner != null ? Bukkit.getOfflinePlayer(winner).getName() : "NONE"));

        // TODO: Display winner
        // TODO: Save statistics
        // TODO: Reward players
        // TODO: Schedule cleanup
    }

    // ===== PLAYER MANAGEMENT =====

    /**
     * Adds a player to the game.
     */
    public boolean addPlayer(@NotNull GamePlayer player) {
        if (!state.canJoin()) {
            return false;
        }

        if (players.size() >= maxPlayers) {
            return false;
        }

        players.put(player.getUuid(), player);

        plugin.getLogger().info("Player " + player.getName() + " joined game " + id +
                " (" + players.size() + "/" + maxPlayers + ")");

        // Check if we can start
        if (players.size() >= minPlayers && state == GameState.WAITING) {
            // Auto-start countdown
            setState(GameState.STARTING);
        }

        return true;
    }

    /**
     * Removes a player from the game.
     */
    public void removePlayer(@NotNull UUID uuid) {
        GamePlayer player = players.remove(uuid);
        alivePlayers.remove(uuid);
        spectators.remove(uuid);

        if (player != null) {
            plugin.getLogger().info("Player " + player.getName() + " left game " + id);
        }

        // Check if game should end
        checkGameEnd();
    }

    /**
     * Marks a player as dead (eliminated).
     */
    public void eliminatePlayer(@NotNull UUID uuid) {
        if (alivePlayers.remove(uuid)) {
            spectators.add(uuid);

            GamePlayer player = players.get(uuid);
            if (player != null) {
                player.setAlive(false);
                plugin.getLogger().info("Player " + player.getName() + " eliminated. " +
                        "Remaining: " + alivePlayers.size());
            }

            // Check if game should end
            checkGameEnd();
        }
    }

    /**
     * Checks if the game should end.
     */
    private void checkGameEnd() {
        if (!state.isInProgress()) {
            return;
        }

        // Solo mode: 1 player remaining
        if (alivePlayers.size() <= 1) {
            if (alivePlayers.size() == 1) {
                winner = alivePlayers.iterator().next();
            }
            setState(GameState.ENDING);
        }

        // TODO: Team mode: 1 team remaining
    }

    /**
     * Checks if deathmatch should be triggered.
     */
    public boolean shouldTriggerDeathmatch() {
        if (state != GameState.ACTIVE) {
            return false;
        }

        // Time limit reached (1 hour)
        long elapsedMillis = System.currentTimeMillis() - startedAt.toEpochMilli();
        if (elapsedMillis >= gameDuration) {
            return true;
        }

        // TODO: Check zone size (very small = trigger deathmatch)

        return false;
    }

    // ===== GETTERS =====

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public GameState getState() {
        return state;
    }

    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }

    @Nullable
    public Instant getEndedAt() {
        return endedAt;
    }

    @Nullable
    public World getGameWorld() {
        return gameWorld;
    }

    public void setGameWorld(@Nullable World world) {
        this.gameWorld = world;
    }

    @NotNull
    public Map<UUID, GamePlayer> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    @NotNull
    public Set<UUID> getAlivePlayers() {
        return Collections.unmodifiableSet(alivePlayers);
    }

    @NotNull
    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getAliveCount() {
        return alivePlayers.size();
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    @Nullable
    public UUID getWinner() {
        return winner;
    }

    @Nullable
    public GamePlayer getPlayer(@NotNull UUID uuid) {
        return players.get(uuid);
    }

    public boolean hasPlayer(@NotNull UUID uuid) {
        return players.containsKey(uuid);
    }

    public boolean isPlayerAlive(@NotNull UUID uuid) {
        return alivePlayers.contains(uuid);
    }

    /**
     * Gets all players as Bukkit Players (online only).
     */
    @NotNull
    public List<org.bukkit.entity.Player> getOnlinePlayers() {
        return players.values().stream()
                .map(gp -> Bukkit.getPlayer(gp.getUuid()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "Game{" +
                "id='" + id + '\'' +
                ", state=" + state +
                ", players=" + players.size() + "/" + maxPlayers +
                ", alive=" + alivePlayers.size() +
                '}';
    }
}