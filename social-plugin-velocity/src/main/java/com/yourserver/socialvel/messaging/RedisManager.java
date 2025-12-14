package com.yourserver.socialvel.messaging;

import com.yourserver.socialvel.SocialPluginVelocity;
import com.yourserver.socialvel.config.VelocityConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RedisManager {

    private final SocialPluginVelocity plugin;
    private final String keyPrefix;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisCommands<String, String> syncCommands;

    private final Map<String, Consumer<String>> subscriptions = new ConcurrentHashMap<>();

    public RedisManager(SocialPluginVelocity plugin, VelocityConfig.RedisConfig config) {
        this.plugin = plugin;
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
            plugin.getLogger().info("Redis connection test successful! Response: " + pong);
        } catch (Exception e) {
            plugin.getLogger().error("Failed to connect to Redis!", e);
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
    }

    public void publish(String channel, String message) {
        try {
            syncCommands.publish(prefixChannel(channel), message);
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to publish message to channel: " + channel, e);
        }
    }

    public void subscribe(String channel, Consumer<String> handler) {
        String prefixedChannel = prefixChannel(channel);
        subscriptions.put(prefixedChannel, handler);
        pubSubConnection.sync().subscribe(prefixedChannel);
        plugin.getLogger().info("Subscribed to Redis channel: " + prefixedChannel);
    }

    public void unsubscribe(String channel) {
        String prefixedChannel = prefixChannel(channel);
        subscriptions.remove(prefixedChannel);
        pubSubConnection.sync().unsubscribe(prefixedChannel);
    }

    public String get(String key) {
        try {
            return syncCommands.get(prefixKey(key));
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to get key: " + key, e);
            return null;
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        try {
            syncCommands.setex(prefixKey(key), ttlSeconds, value);
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to set key: " + key, e);
        }
    }

    public void delete(String key) {
        try {
            syncCommands.del(prefixKey(key));
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to delete key: " + key, e);
        }
    }

    public boolean exists(String key) {
        try {
            Long result = syncCommands.exists(prefixKey(key));
            return result != null && result > 0;
        } catch (Exception e) {
            plugin.getLogger().warn("Failed to check key existence: " + key, e);
            return false;
        }
    }

    private void handleMessage(String channel, String message) {
        Consumer<String> handler = subscriptions.get(channel);
        if (handler != null) {
            try {
                handler.accept(message);
            } catch (Exception e) {
                plugin.getLogger().error("Error handling message from channel: " + channel, e);
            }
        }
    }

    private String prefixKey(String key) {
        return keyPrefix + key;
    }

    private String prefixChannel(String channel) {
        return keyPrefix + channel;
    }

    public void shutdown() {
        if (pubSubConnection != null) {
            pubSubConnection.close();
        }

        if (connection != null) {
            connection.close();
        }

        if (redisClient != null) {
            redisClient.shutdown();
        }
    }
}