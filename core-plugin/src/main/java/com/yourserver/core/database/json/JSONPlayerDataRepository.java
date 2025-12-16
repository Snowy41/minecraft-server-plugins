package com.yourserver.core.database.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yourserver.api.model.PlayerData;
import com.yourserver.api.repository.PlayerDataRepository;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * JSON-based player data repository.
 * Stores all player data in a single JSON file: plugins/CorePlugin/data/players.json
 *
 * Thread-safe with read-write locks.
 * Automatic backups before each save.
 */
public class JSONPlayerDataRepository implements PlayerDataRepository {

    private final File dataFile;
    private final File backupFile;
    private final Gson gson;
    private final ReadWriteLock lock;
    private final Logger logger;

    public JSONPlayerDataRepository(@NotNull File pluginFolder, @NotNull Logger logger) {
        this.logger = logger;

        // Create data directory
        File dataDir = new File(pluginFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.dataFile = new File(dataDir, "players.json");
        this.backupFile = new File(dataDir, "players.json.backup");

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();

        this.lock = new ReentrantReadWriteLock();

        // Create file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                saveData(new PlayerDataStorage());
                logger.info("Created players.json file");
            } catch (IOException e) {
                logger.severe("Failed to create players.json: " + e.getMessage());
            }
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUuid(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerDataStorage storage = loadData();
            return Optional.ofNullable(storage.players.get(uuid.toString()));
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> findByUsername(@NotNull String username) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerDataStorage storage = loadData();
            return storage.players.values().stream()
                    .filter(p -> p.getUsername().equalsIgnoreCase(username))
                    .findFirst();
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            PlayerDataStorage storage = loadData();
            storage.players.put(data.getUuid().toString(), data);
            saveData(storage);
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerDataStorage storage = loadData();
            return storage.players.containsKey(uuid.toString());
        });
    }

    // ===== FILE OPERATIONS =====

    private PlayerDataStorage loadData() {
        lock.readLock().lock();
        try {
            if (!dataFile.exists() || dataFile.length() == 0) {
                return new PlayerDataStorage();
            }

            try (Reader reader = new FileReader(dataFile)) {
                PlayerDataStorage storage = gson.fromJson(reader, PlayerDataStorage.class);
                return storage != null ? storage : new PlayerDataStorage();
            } catch (IOException e) {
                logger.severe("Failed to load players.json: " + e.getMessage());

                // Try backup
                if (backupFile.exists()) {
                    logger.warning("Attempting to load from backup...");
                    try (Reader reader = new FileReader(backupFile)) {
                        PlayerDataStorage storage = gson.fromJson(reader, PlayerDataStorage.class);
                        return storage != null ? storage : new PlayerDataStorage();
                    } catch (IOException ex) {
                        logger.severe("Failed to load backup: " + ex.getMessage());
                    }
                }

                return new PlayerDataStorage();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void saveData(PlayerDataStorage storage) {
        lock.writeLock().lock();
        try {
            // Backup existing file
            if (dataFile.exists() && dataFile.length() > 0) {
                try {
                    Files.copy(dataFile.toPath(), backupFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    logger.warning("Failed to create backup: " + e.getMessage());
                }
            }

            // Write new data
            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(storage, writer);
            } catch (IOException e) {
                logger.severe("Failed to save players.json: " + e.getMessage());
                throw new RuntimeException("Failed to save player data", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ===== DATA MODEL =====

    private static class PlayerDataStorage {
        Map<String, PlayerData> players = new HashMap<>();
    }

    // ===== GSON TYPE ADAPTER FOR INSTANT =====

    private static class InstantTypeAdapter extends com.google.gson.TypeAdapter<Instant> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}