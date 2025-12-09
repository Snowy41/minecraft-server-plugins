package com.yourserver.api.messaging;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Interface for Redis pub/sub messaging system.
 * Enables cross-server communication in a multi-instance environment.
 */
public interface RedisMessenger {

    /**
     * Publishes a message to a Redis channel.
     *
     * @param channel The channel to publish to
     * @param message The message to send
     */
    void publish(@NotNull String channel, @NotNull String message);

    /**
     * Subscribes to a Redis channel.
     *
     * @param channel The channel to subscribe to
     * @param handler The handler to process incoming messages
     */
    void subscribe(@NotNull String channel, @NotNull Consumer<String> handler);

    /**
     * Unsubscribes from a Redis channel.
     *
     * @param channel The channel to unsubscribe from
     */
    void unsubscribe(@NotNull String channel);

    /**
     * Gets a value from Redis cache.
     *
     * @param key The cache key
     * @return The cached value, or null if not found
     */
    String get(@NotNull String key);

    /**
     * Sets a value in Redis cache with expiration.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param ttlSeconds Time-to-live in seconds
     */
    void set(@NotNull String key, @NotNull String value, long ttlSeconds);

    /**
     * Deletes a key from Redis cache.
     *
     * @param key The cache key to delete
     */
    void delete(@NotNull String key);

    /**
     * Checks if a key exists in Redis cache.
     *
     * @param key The cache key to check
     * @return true if the key exists, false otherwise
     */
    boolean exists(@NotNull String key);
}