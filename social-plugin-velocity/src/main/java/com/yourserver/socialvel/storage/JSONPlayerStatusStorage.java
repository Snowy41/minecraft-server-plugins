package com.yourserver.socialvel.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yourserver.socialvel.model.PlayerStatus;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JSONPlayerStatusStorage {

    private final File dataFile;
    private final Gson gson;
    private final ReadWriteLock lock;
    private final Logger logger;

    private Map<UUID, PlayerStatus> statusMap;

    public JSONPlayerStatusStorage(File dataFolder, Logger logger) {
        this.logger = logger;

        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.dataFile = new File(dataDir, "player-status.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.lock = new ReentrantReadWriteLock();
        this.statusMap = new HashMap<>();

        // Load existing data
        load().join();
    }

    public void updateStatus(PlayerStatus status) {
        lock.writeLock().lock();
        try {
            statusMap.put(status.getUuid(), status);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public PlayerStatus getStatus(UUID uuid) {
        lock.readLock().lock();
        try {
            return statusMap.get(uuid);
        } finally {
            lock.readLock().unlock();
        }
    }

    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                if (!dataFile.exists()) {
                    statusMap = new HashMap<>();
                    return;
                }

                try (Reader reader = new FileReader(dataFile)) {
                    Map<String, PlayerStatus> rawMap = gson.fromJson(reader,
                            new TypeToken<Map<String, PlayerStatus>>(){}.getType());

                    if (rawMap != null) {
                        statusMap = new HashMap<>();
                        rawMap.forEach((key, value) -> {
                            try {
                                UUID uuid = UUID.fromString(key);
                                statusMap.put(uuid, value);
                            } catch (IllegalArgumentException ignored) {}
                        });
                    } else {
                        statusMap = new HashMap<>();
                    }

                    logger.info("Loaded " + statusMap.size() + " player statuses");

                } catch (IOException e) {
                    logger.error("Failed to load player statuses", e);
                    statusMap = new HashMap<>();
                }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    public CompletableFuture<Void> save() {
        return CompletableFuture.runAsync(() -> {
            lock.readLock().lock();
            try {
                // Convert UUID keys to strings for JSON
                Map<String, PlayerStatus> rawMap = new HashMap<>();
                statusMap.forEach((uuid, status) -> rawMap.put(uuid.toString(), status));

                try (Writer writer = new FileWriter(dataFile)) {
                    gson.toJson(rawMap, writer);
                    logger.info("Saved " + statusMap.size() + " player statuses");
                } catch (IOException e) {
                    logger.error("Failed to save player statuses", e);
                }
            } finally {
                lock.readLock().unlock();
            }
        });
    }
}