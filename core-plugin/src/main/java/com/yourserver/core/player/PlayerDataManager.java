package com.yourserver.core.player;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yourserver.api.model.PlayerData;
import com.yourserver.api.model.PlayerStats;
import com.yourserver.api.repository.PlayerDataRepository;
import com.yourserver.api.repository.PlayerStatsRepository;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages player data with in-memory caching for performance.
 * Handles loading, saving, and updating player data and statistics.
 */
public class PlayerDataManager {

    private final PlayerDataRepository playerDataRepository;
    private final PlayerStatsRepository playerStatsRepository;

    // Caffeine cache for player data (10 minute expiry)
    private final Cache<UUID, PlayerData> playerDataCache;

    // Caffeine cache for player stats (10 minute expiry)
    private final Cache<UUID, PlayerStats> playerStatsCache;

    // Track join times for playtime calculation
    private final ConcurrentHashMap<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    public PlayerDataManager(
            PlayerDataRepository playerDataRepository,
            PlayerStatsRepository playerStatsRepository
    ) {
        this.playerDataRepository = playerDataRepository;
        this.playerStatsRepository = playerStatsRepository;

        // Initialize caches
        this.playerDataCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        this.playerStatsCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
    }

    /**
     * Loads player data from cache or database.
     * Creates default data if player doesn't exist.
     *
     * @param uuid Player UUID
     * @param username Player username
     * @return CompletableFuture containing player data
     */
    @NotNull
    public CompletableFuture<PlayerData> loadPlayerData(@NotNull UUID uuid, @NotNull String username) {
        // Check cache first
        PlayerData cached = playerDataCache.getIfPresent(uuid);
        if (cached != null) {
            // Update username if changed
            if (!cached.getUsername().equals(username)) {
                cached = cached.withUsername(username);
                playerDataCache.put(uuid, cached);
                // Save updated username to database
                playerDataRepository.save(cached);
            }
            return CompletableFuture.completedFuture(cached);
        }

        // Load from database
        return playerDataRepository.findByUuid(uuid)
                .thenApply(optional -> {
                    PlayerData data;
                    if (optional.isPresent()) {
                        data = optional.get();
                        // Update username if changed
                        if (!data.getUsername().equals(username)) {
                            data = data.withUsername(username);
                        }
                    } else {
                        // Create new player data
                        data = PlayerData.createDefault(uuid, username);
                    }

                    // Cache the data
                    playerDataCache.put(uuid, data);
                    return data;
                });
    }

    /**
     * Loads player statistics from cache or database.
     * Creates default stats if player doesn't exist.
     *
     * @param uuid Player UUID
     * @return CompletableFuture containing player stats
     */
    @NotNull
    public CompletableFuture<PlayerStats> loadPlayerStats(@NotNull UUID uuid) {
        // Check cache first
        PlayerStats cached = playerStatsCache.getIfPresent(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Load from database
        return playerStatsRepository.findByUuid(uuid)
                .thenApply(optional -> {
                    PlayerStats stats = optional.orElseGet(() -> PlayerStats.createDefault(uuid));
                    playerStatsCache.put(uuid, stats);
                    return stats;
                });
    }

    /**
     * Called when a player joins the server.
     *
     * @param uuid Player UUID
     * @param username Player username
     * @return CompletableFuture that completes when data is loaded
     */
    @NotNull
    public CompletableFuture<Void> handlePlayerJoin(@NotNull UUID uuid, @NotNull String username) {
        // Record join time for playtime tracking
        joinTimes.put(uuid, System.currentTimeMillis());

        // Load player data and update last join
        return loadPlayerData(uuid, username)
                .thenCompose(data -> {
                    PlayerData updated = data.withLastJoin(Instant.now());
                    playerDataCache.put(uuid, updated);
                    return playerDataRepository.save(updated);
                })
                .thenCompose(v -> loadPlayerStats(uuid))
                .thenApply(stats -> null);
    }

    /**
     * Called when a player leaves the server.
     * Saves data and updates playtime.
     *
     * @param uuid Player UUID
     * @return CompletableFuture that completes when data is saved
     */
    @NotNull
    public CompletableFuture<Void> handlePlayerQuit(@NotNull UUID uuid) {
        // Calculate session playtime
        Long joinTime = joinTimes.remove(uuid);
        if (joinTime == null) {
            return CompletableFuture.completedFuture(null);
        }

        long sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000;

        // Update playtime in cache
        PlayerData data = playerDataCache.getIfPresent(uuid);
        if (data != null) {
            PlayerData updated = data.withPlaytime(data.getPlaytimeSeconds() + sessionSeconds);
            playerDataCache.put(uuid, updated);
            return playerDataRepository.save(updated);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Gets cached player data without loading from database.
     *
     * @param uuid Player UUID
     * @return Optional containing cached data, or empty
     */
    @NotNull
    public Optional<PlayerData> getCachedPlayerData(@NotNull UUID uuid) {
        return Optional.ofNullable(playerDataCache.getIfPresent(uuid));
    }

    /**
     * Gets cached player stats without loading from database.
     *
     * @param uuid Player UUID
     * @return Optional containing cached stats, or empty
     */
    @NotNull
    public Optional<PlayerStats> getCachedPlayerStats(@NotNull UUID uuid) {
        return Optional.ofNullable(playerStatsCache.getIfPresent(uuid));
    }

    /**
     * Updates player statistics (adds to existing values).
     *
     * @param uuid Player UUID
     * @param gamesPlayed Games to add
     * @param gamesWon Games won to add
     * @param kills Kills to add
     * @param deaths Deaths to add
     * @param damageDealt Damage dealt to add
     * @param damageTaken Damage taken to add
     * @return CompletableFuture that completes when stats are updated
     */
    @NotNull
    public CompletableFuture<Void> updateStats(
            @NotNull UUID uuid,
            int gamesPlayed,
            int gamesWon,
            int kills,
            int deaths,
            double damageDealt,
            double damageTaken
    ) {
        return playerStatsRepository.updateStats(
                uuid, gamesPlayed, gamesWon, kills, deaths, damageDealt, damageTaken
        ).thenRun(() -> {
            // Invalidate cache so fresh data is loaded next time
            playerStatsCache.invalidate(uuid);
        });
    }

    /**
     * Saves all cached player data to database.
     * Should be called on server shutdown.
     *
     * @return CompletableFuture that completes when all data is saved
     */
    @NotNull
    public CompletableFuture<Void> saveAll() {
        CompletableFuture<Void>[] futures = playerDataCache.asMap().values().stream()
                .map(playerDataRepository::save)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    /**
     * Shuts down the manager and saves all data.
     */
    public void shutdown() {
        // Save all cached data
        saveAll().join();

        // Clear caches
        playerDataCache.invalidateAll();
        playerStatsCache.invalidateAll();
        joinTimes.clear();
    }
}