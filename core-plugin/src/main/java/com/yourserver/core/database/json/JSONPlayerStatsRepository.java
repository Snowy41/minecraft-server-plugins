package com.yourserver.core.database.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yourserver.api.model.PlayerStats;
import com.yourserver.api.repository.PlayerStatsRepository;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * JSON-based player stats repository.
 * Stores all player statistics in: plugins/CorePlugin/data/player-stats.json
 */
public class JSONPlayerStatsRepository implements PlayerStatsRepository {

    private final File dataFile;
    private final File backupFile;
    private final Gson gson;
    private final ReadWriteLock lock;
    private final Logger logger;

    public JSONPlayerStatsRepository(@NotNull File pluginFolder, @NotNull Logger logger) {
        this.logger = logger;

        File dataDir = new File(pluginFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.dataFile = new File(dataDir, "player-stats.json");
        this.backupFile = new File(dataDir, "player-stats.json.backup");

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        this.lock = new ReentrantReadWriteLock();

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                saveData(new PlayerStatsStorage());
                logger.info("Created player-stats.json file");
            } catch (IOException e) {
                logger.severe("Failed to create player-stats.json: " + e.getMessage());
            }
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerStats>> findByUuid(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerStatsStorage storage = loadData();
            return Optional.ofNullable(storage.stats.get(uuid.toString()));
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerStats stats) {
        return CompletableFuture.runAsync(() -> {
            PlayerStatsStorage storage = loadData();
            storage.stats.put(stats.getUuid().toString(), stats);
            saveData(storage);
        });
    }

    @Override
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
        return CompletableFuture.runAsync(() -> {
            PlayerStatsStorage storage = loadData();

            PlayerStats existing = storage.stats.get(uuid.toString());
            PlayerStats updated;

            if (existing == null) {
                // Create new stats
                updated = new PlayerStats(
                        uuid,
                        gamesPlayed,
                        gamesWon,
                        kills,
                        deaths,
                        damageDealt,
                        damageTaken
                );
            } else {
                // Add to existing stats
                updated = new PlayerStats(
                        uuid,
                        existing.getGamesPlayed() + gamesPlayed,
                        existing.getGamesWon() + gamesWon,
                        existing.getKills() + kills,
                        existing.getDeaths() + deaths,
                        existing.getDamageDealt() + damageDealt,
                        existing.getDamageTaken() + damageTaken
                );
            }

            storage.stats.put(uuid.toString(), updated);
            saveData(storage);
        });
    }

    // ===== FILE OPERATIONS =====

    private PlayerStatsStorage loadData() {
        lock.readLock().lock();
        try {
            if (!dataFile.exists() || dataFile.length() == 0) {
                return new PlayerStatsStorage();
            }

            try (Reader reader = new FileReader(dataFile)) {
                PlayerStatsStorage storage = gson.fromJson(reader, PlayerStatsStorage.class);
                return storage != null ? storage : new PlayerStatsStorage();
            } catch (IOException e) {
                logger.severe("Failed to load player-stats.json: " + e.getMessage());

                if (backupFile.exists()) {
                    logger.warning("Attempting to load from backup...");
                    try (Reader reader = new FileReader(backupFile)) {
                        PlayerStatsStorage storage = gson.fromJson(reader, PlayerStatsStorage.class);
                        return storage != null ? storage : new PlayerStatsStorage();
                    } catch (IOException ex) {
                        logger.severe("Failed to load backup: " + ex.getMessage());
                    }
                }

                return new PlayerStatsStorage();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveData(PlayerStatsStorage storage) {
        lock.writeLock().lock();
        try {
            if (dataFile.exists() && dataFile.length() > 0) {
                try {
                    Files.copy(dataFile.toPath(), backupFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    logger.warning("Failed to create backup: " + e.getMessage());
                }
            }

            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(storage, writer);
            } catch (IOException e) {
                logger.severe("Failed to save player-stats.json: " + e.getMessage());
                throw new RuntimeException("Failed to save player stats", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ===== DATA MODEL =====

    private static class PlayerStatsStorage {
        Map<String, PlayerStats> stats = new HashMap<>();
    }
}