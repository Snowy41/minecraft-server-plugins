package com.yourserver.core.redis;

import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.core.config.DatabaseConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis manager implementing pub/sub messaging and caching.
 * Uses Lettuce client for high-performance async operations.
 */
public class RedisManager implements RedisMessenger {

    private final Logger logger;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisCommands<String, String> syncCommands;
    private String keyPrefix;

    // Store active subscriptions
    private final Map<String, Consumer<String>> subscriptions = new ConcurrentHashMap<>();

    public RedisManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Initializes Redis connection.
     *
     * @param config Redis configuration
     */
    public void initialize(DatabaseConfig.RedisConfig config) {
        logger.info("Initializing Redis connection...");

        this.keyPrefix = config.getKeyPrefix();

        // Build Redis URI
        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(config.getHost())
                .withPort(config.getPort());

        if (config.hasPassword()) {
            uriBuilder.withPassword(config.getPassword().toCharArray());
        }

        RedisURI redisUri = uriBuilder.build();

        // Create client
        this.redisClient = RedisClient.create(redisUri);
        this.connection = redisClient.connect();
        this.syncCommands = connection.sync();

        // Test connection
        try {
            String pong = syncCommands.ping();
            logger.info("Redis connection test successful! Response: " + pong);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to connect to Redis!", e);
            throw new RuntimeException("Redis connection failed", e);
        }

        // Initialize pub/sub connection
        this.pubSubConnection = redisClient.connectPubSub();

        // Add listener for all subscribed channels
        pubSubConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                handleMessage(channel, message);
            }
        });

        logger.info("Redis initialized successfully");
    }

    @Override
    public void publish(@NotNull String channel, @NotNull String message) {
        try {
            syncCommands.publish(prefixChannel(channel), message);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to publish message to channel: " + channel, e);
        }
    }

    @Override
    public void subscribe(@NotNull String channel, @NotNull Consumer<String> handler) {
        String prefixedChannel = prefixChannel(channel);
        subscriptions.put(prefixedChannel, handler);
        pubSubConnection.sync().subscribe(prefixedChannel);
        logger.info("Subscribed to Redis channel: " + prefixedChannel);
    }

    @Override
    public void unsubscribe(@NotNull String channel) {
        String prefixedChannel = prefixChannel(channel);
        subscriptions.remove(prefixedChannel);
        pubSubConnection.sync().unsubscribe(prefixedChannel);
        logger.info("Unsubscribed from Redis channel: " + prefixedChannel);
    }

    @Override
    public String get(@NotNull String key) {
        try {
            return syncCommands.get(prefixKey(key));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get key: " + key, e);
            return null;
        }
    }

    @Override
    public void set(@NotNull String key, @NotNull String value, long ttlSeconds) {
        try {
            syncCommands.setex(prefixKey(key), ttlSeconds, value);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to set key: " + key, e);
        }
    }

    @Override
    public void delete(@NotNull String key) {
        try {
            syncCommands.del(prefixKey(key));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to delete key: " + key, e);
        }
    }

    @Override
    public boolean exists(@NotNull String key) {
        try {
            Long result = syncCommands.exists(prefixKey(key));
            return result != null && result > 0;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to check key existence: " + key, e);
            return false;
        }
    }

    /**
     * Handles incoming pub/sub messages.
     *
     * @param channel The channel that received the message
     * @param message The message content
     */
    private void handleMessage(String channel, String message) {
        Consumer<String> handler = subscriptions.get(channel);
        if (handler != null) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error handling message from channel: " + channel, e);
            }
        }
    }

    /**
     * Prefixes a key with the configured key prefix.
     *
     * @param key The key to prefix
     * @return Prefixed key
     */
    private String prefixKey(String key) {
        return keyPrefix + key;
    }

    /**
     * Prefixes a channel name with the configured key prefix.
     *
     * @param channel The channel to prefix
     * @return Prefixed channel
     */
    private String prefixChannel(String channel) {
        return keyPrefix + channel;
    }

    /**
     * Shuts down Redis connections.
     */
    public void shutdown() {
        if (pubSubConnection != null) {
            pubSubConnection.close();
            logger.info("Redis pub/sub connection closed");
        }

        if (connection != null) {
            connection.close();
            logger.info("Redis connection closed");
        }

        if (redisClient != null) {
            redisClient.shutdown();
            logger.info("Redis client shut down");
        }
    }

    /**
     * Checks if Redis is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connection != null && connection.isOpen();
    }
}