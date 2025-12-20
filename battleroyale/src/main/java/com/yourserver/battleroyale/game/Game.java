package com.yourserver.battleroyale.game;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.arena.Arena;
import com.yourserver.battleroyale.arena.DeathmatchArena;
import com.yourserver.battleroyale.arena.PregameLobby;
import com.yourserver.battleroyale.loot.LootManager;
import com.yourserver.battleroyale.player.GamePlayer;
import com.yourserver.battleroyale.player.SpectatorManager;
import com.yourserver.battleroyale.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
 *
 * FIXED:
 * - Added automatic cleanup after ENDING state
 * - Games properly remove themselves after completion
 * - Players are returned to spawn after game ends
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
    private Arena arena;
    private GameConfig config;

    // Players
    private final Map<UUID, GamePlayer> players;
    private final Set<UUID> alivePlayers;
    private final Set<UUID> spectators;

    // Game settings
    private final int minPlayers;
    private final int maxPlayers;
    private final long gameDuration; // milliseconds (1 hour default)

    // Game systems
    private ZoneManager zoneManager;
    private LootManager lootManager;
    private PregameLobby pregameLobby;
    private DeathmatchArena deathmatchArena;
    private GameScheduler scheduler;
    private SpectatorManager spectatorManager;

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

        // Initialize game systems
        this.zoneManager = new ZoneManager(plugin, this);
        this.lootManager = new LootManager(plugin);
        this.scheduler = new GameScheduler(plugin, this);
        this.spectatorManager = new SpectatorManager(this);
    }

    // ===== STATE MANAGEMENT =====

    /**
     * Transitions the game to a new state.
     */
    public void setState(@NotNull GameState newState) {
        GameState oldState = this.state;
        this.state = newState;

        plugin.getLogger().info("Game " + id + " state: " + oldState + " → " + newState);

        // Notify scheduler of state change
        if (scheduler != null) {
            scheduler.onStateChange(newState);
        }

        // Handle state entry
        switch (newState) {
            case STARTING -> {
                onStarting();
                // Start the scheduler (automatic countdown)
                if (scheduler != null) {
                    scheduler.start();
                }
            }
            case ACTIVE -> onActive();
            case DEATHMATCH -> {
                onDeathmatch();
                // Teleport spectators to watch deathmatch
                if (deathmatchArena != null && spectatorManager != null) {
                    spectatorManager.teleportSpectatorsToArena(deathmatchArena.getCenter());
                }
            }
            case ENDING -> {
                onEnding();
                // Stop scheduler
                if (scheduler != null) {
                    scheduler.stop();
                }
                // FIXED: Schedule automatic cleanup
                scheduleCleanup();
            }
        }
    }

    private void onStarting() {
        plugin.getLogger().info("Game " + id + " starting countdown...");

        // Build pre-game lobby if arena is set
        if (arena != null && pregameLobby == null) {
            pregameLobby = PregameLobby.createDefault(arena.getCenter());
            pregameLobby.build();
            plugin.getLogger().info("Pre-game lobby built for game " + id);

            // Teleport all players to lobby
            int spawnIndex = 0;
            for (UUID uuid : players.keySet()) {
                org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    pregameLobby.teleportPlayer(player, spawnIndex++);
                }
            }
        }

        // NOTE: Scheduler automatically starts countdown - no manual timer needed!
    }

    private void onActive() {
        this.startedAt = Instant.now();
        plugin.getLogger().info("Game " + id + " is now ACTIVE!");

        // Initialize alive players
        alivePlayers.addAll(players.keySet());

        // Teleport players to spawn points on the map
        if (arena != null) {
            List<Location> spawnPoints = arena.getSpawnPoints(players.size());
            int i = 0;
            for (UUID uuid : players.keySet()) {
                org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                if (player != null && i < spawnPoints.size()) {
                    player.teleport(spawnPoints.get(i++));

                    // Send game start message
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
                    player.sendMessage(Component.text("  ⚔ GAME START!", NamedTextColor.GREEN, TextDecoration.BOLD));
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("  Survive and be the last one standing!", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));
                    player.sendMessage(Component.empty());
                }
            }
        }

        // Remove pre-game lobby
        if (pregameLobby != null) {
            pregameLobby.remove();
            pregameLobby = null;
        }

        // Start zone system
        if (arena != null && zoneManager != null) {
            zoneManager.start(arena.getCenter(), arena.getSize());
            plugin.getLogger().info("Zone system started for game " + id);
        }

        // Spawn loot chests
        if (arena != null && lootManager != null) {
            lootManager.spawnLoot(arena);
            plugin.getLogger().info("Loot spawned for game " + id);
        }
    }

    private void onDeathmatch() {
        plugin.getLogger().info("Game " + id + " entering DEATHMATCH phase!");

        // Build deathmatch arena
        if (arena != null && deathmatchArena == null) {
            deathmatchArena = DeathmatchArena.createDefault(arena.getCenter());
            deathmatchArena.build();
            plugin.getLogger().info("Deathmatch arena built for game " + id);
        }

        // Teleport remaining players to deathmatch arena
        if (deathmatchArena != null) {
            int spawnIndex = 0;
            for (UUID uuid : alivePlayers) {
                org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    deathmatchArena.teleportPlayer(player, spawnIndex++);

                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
                    player.sendMessage(Component.text("  ⚔ ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("DEATHMATCH", NamedTextColor.GOLD, TextDecoration.BOLD)));
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("  No more hiding - FIGHT!", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.RED));
                    player.sendMessage(Component.empty());
                }
            }
        }

        // Stop zone damage
        if (zoneManager != null) {
            zoneManager.stop();
        }
    }

    private void onEnding() {
        this.endedAt = Instant.now();
        plugin.getLogger().info("Game " + id + " is ENDING. Winner: " +
                (winner != null ? Bukkit.getOfflinePlayer(winner).getName() : "NONE"));

        // Announce winner to all players
        if (winner != null) {
            org.bukkit.entity.Player winnerPlayer = Bukkit.getPlayer(winner);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "Unknown";

            Component winMessage = Component.empty()
                    .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(Component.text("  👑 ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("VICTORY ROYALE!", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("  Winner: ", NamedTextColor.GRAY))
                    .append(Component.text(winnerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD));

            for (org.bukkit.entity.Player player : getOnlinePlayers()) {
                player.sendMessage(Component.empty());
                player.sendMessage(winMessage);
                player.sendMessage(Component.empty());
            }
        }

        // Clean up spectators
        if (spectatorManager != null) {
            spectatorManager.clearAll();
        }

        // Stop zone system
        if (zoneManager != null) {
            zoneManager.stop();
        }

        // Clear loot
        if (lootManager != null) {
            lootManager.clearLoot();
        }

        // Remove pre-game lobby
        if (pregameLobby != null) {
            pregameLobby.remove();
            pregameLobby = null;
        }

        // Remove deathmatch arena
        if (deathmatchArena != null) {
            deathmatchArena.remove();
            deathmatchArena = null;
        }

        // TODO: Display detailed statistics
        // TODO: Save statistics to database
        // TODO: Reward players
        // TODO: Schedule cleanup and world deletion
    }

    /**
     * FIXED: Schedules game cleanup after ENDING state.
     * Waits 10 seconds to display winner/stats, then:
     * - Kicks all players back to spawn
     * - Clears all data structures
     * - Prepares game for removal by GameManager
     */
    private void scheduleCleanup() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().info("Cleaning up game " + id + "...");

            // Kick all remaining players back to spawn
            for (org.bukkit.entity.Player player : getOnlinePlayers()) {
                player.sendMessage(Component.text("Game ended! Returning to spawn...", NamedTextColor.YELLOW));

                // Teleport to world spawn (or lobby if you have one)
                World world = player.getWorld();
                if (world != null) {
                    player.teleport(world.getSpawnLocation());
                } else {
                    // Fallback to main world spawn
                    World mainWorld = Bukkit.getWorlds().get(0);
                    player.teleport(mainWorld.getSpawnLocation());
                }
            }

            // Clear all data structures
            players.clear();
            alivePlayers.clear();
            spectators.clear();

            plugin.getLogger().info("Game " + id + " cleanup complete - ready for removal");

        }, 200L); // 10 seconds (200 ticks)
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
                player.setPlacement(alivePlayers.size() + 1);

                plugin.getLogger().info("Player " + player.getName() + " eliminated. " +
                        "Remaining: " + alivePlayers.size());

                // Convert to spectator
                org.bukkit.entity.Player bukkitPlayer = Bukkit.getPlayer(uuid);
                if (bukkitPlayer != null) {
                    if (spectatorManager != null) {
                        spectatorManager.makeSpectator(bukkitPlayer);
                    }

                    // Show elimination message
                    bukkitPlayer.sendMessage(Component.empty());
                    bukkitPlayer.sendMessage(Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("You were eliminated!", NamedTextColor.RED)));
                    bukkitPlayer.sendMessage(Component.text("  Placement: ", NamedTextColor.GRAY)
                            .append(Component.text("#" + player.getPlacement(), NamedTextColor.YELLOW)));
                    bukkitPlayer.sendMessage(Component.text("  Kills: ", NamedTextColor.GRAY)
                            .append(Component.text(player.getKills(), NamedTextColor.WHITE)));
                    bukkitPlayer.sendMessage(Component.empty());
                }
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
        if (startedAt != null) {
            long elapsedMillis = System.currentTimeMillis() - startedAt.toEpochMilli();
            if (elapsedMillis >= gameDuration) {
                return true;
            }
        }

        // Zone has reached final phase
        if (zoneManager != null && zoneManager.shouldTriggerDeathmatch()) {
            return true;
        }

        return false;
    }

    // ===== ARENA MANAGEMENT =====

    /**
     * Sets the arena for this game.
     */
    public void setArena(@NotNull Arena arena) {
        this.arena = Objects.requireNonNull(arena);
        plugin.getLogger().info("Arena set for game " + id + ": " + arena.getName());
    }

    @Nullable
    public Arena getArena() {
        return arena;
    }

    // ===== SYSTEM GETTERS =====

    @Nullable
    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    @Nullable
    public LootManager getLootManager() {
        return lootManager;
    }

    @Nullable
    public PregameLobby getPregameLobby() {
        return pregameLobby;
    }

    @Nullable
    public DeathmatchArena getDeathmatchArena() {
        return deathmatchArena;
    }

    @Nullable
    public GameScheduler getScheduler() {
        return scheduler;
    }

    @Nullable
    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
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