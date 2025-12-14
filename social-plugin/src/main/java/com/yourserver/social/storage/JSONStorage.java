package com.yourserver.social.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * JSON file storage system with automatic backups.
 * Thread-safe with read-write locks.
 *
 * Features:
 * - Automatic file creation
 * - Pretty-printed JSON
 * - Backup before write
 * - Async operations
 * - Thread-safe
 */
public class JSONStorage<T> {

    private final File dataFile;
    private final File backupFile;
    private final Type typeToken;
    private final Gson gson;
    private final ReadWriteLock lock;
    private final Logger logger;
    private final T emptyValue;

    /**
     * Creates a JSON storage for a specific type.
     *
     * @param dataFolder Plugin data folder
     * @param fileName File name (e.g., "friends.json")
     * @param typeToken Type token for deserialization
     * @param emptyValue Empty value to return if file doesn't exist
     * @param logger Logger for errors
     */
    public JSONStorage(@NotNull File dataFolder,
                       @NotNull String fileName,
                       @NotNull TypeToken<T> typeToken,
                       @NotNull T emptyValue,
                       @NotNull Logger logger) {
        // Create data directory
        File dataDir = new File(dataFolder, "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        this.dataFile = new File(dataDir, fileName);
        this.backupFile = new File(dataDir, fileName + ".backup");
        this.typeToken = typeToken.getType();
        this.emptyValue = emptyValue;
        this.logger = logger;

        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        this.lock = new ReentrantReadWriteLock();

        // Create file if it doesn't exist
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                // Write empty value
                save(emptyValue).join();
                logger.info("Created data file: " + fileName);
            } catch (IOException e) {
                logger.severe("Failed to create data file: " + e.getMessage());
            }
        }
    }

    /**
     * Loads data from the JSON file asynchronously.
     *
     * @return CompletableFuture with the loaded data
     */
    @NotNull
    public CompletableFuture<T> load() {
        return CompletableFuture.supplyAsync(() -> {
            lock.readLock().lock();
            try {
                if (!dataFile.exists() || dataFile.length() == 0) {
                    return emptyValue;
                }

                try (Reader reader = new FileReader(dataFile)) {
                    T data = gson.fromJson(reader, typeToken);
                    return data != null ? data : emptyValue;
                } catch (IOException e) {
                    logger.severe("Failed to load data from " + dataFile.getName() + ": " + e.getMessage());

                    // Try to load from backup
                    if (backupFile.exists()) {
                        logger.warning("Attempting to load from backup...");
                        try (Reader reader = new FileReader(backupFile)) {
                            T data = gson.fromJson(reader, typeToken);
                            return data != null ? data : emptyValue;
                        } catch (IOException ex) {
                            logger.severe("Failed to load backup: " + ex.getMessage());
                        }
                    }

                    return emptyValue;
                }
            } finally {
                lock.readLock().unlock();
            }
        });
    }

    /**
     * Saves data to the JSON file asynchronously.
     * Creates a backup before saving.
     *
     * @param data The data to save
     * @return CompletableFuture that completes when save is done
     */
    @NotNull
    public CompletableFuture<Void> save(@NotNull T data) {
        return CompletableFuture.runAsync(() -> {
            lock.writeLock().lock();
            try {
                // Create backup of existing file
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
                    gson.toJson(data, typeToken, writer);
                } catch (IOException e) {
                    logger.severe("Failed to save data to " + dataFile.getName() + ": " + e.getMessage());
                    throw new RuntimeException("Failed to save data", e);
                }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    /**
     * Gets the data file.
     */
    @NotNull
    public File getDataFile() {
        return dataFile;
    }

    /**
     * Gets the backup file.
     */
    @NotNull
    public File getBackupFile() {
        return backupFile;
    }

    /**
     * Deletes the data file and backup.
     */
    public void delete() {
        lock.writeLock().lock();
        try {
            if (dataFile.exists()) {
                dataFile.delete();
            }
            if (backupFile.exists()) {
                backupFile.delete();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}