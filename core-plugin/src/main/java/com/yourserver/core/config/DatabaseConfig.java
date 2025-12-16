package com.yourserver.core.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;

/**
 * Configuration loader - REDIS ONLY VERSION
 * MySQL configuration removed, only Redis remains.
 */
public class DatabaseConfig {

    private final RedisConfig redisConfig;

    private DatabaseConfig(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    /**
     * Loads Redis-only configuration from redis.yml
     */
    public static DatabaseConfig loadRedisOnly(File dataFolder) {
        File configFile = new File(dataFolder, "redis.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();
            RedisConfig redis = loadRedis(root.node("redis"));

            return new DatabaseConfig(redis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load redis.yml", e);
        }
    }

    private static RedisConfig loadRedis(CommentedConfigurationNode node) {
        return new RedisConfig(
                node.node("host").getString("localhost"),
                node.node("port").getInt(6379),
                node.node("password").getString(""),
                node.node("key-prefix").getString("mcserver:")
        );
    }

    public RedisConfig getRedisConfig() {
        return redisConfig;
    }

    // REMOVED: MySQLConfig class - no longer needed

    /**
     * Redis connection configuration.
     */
    public static class RedisConfig {
        private final String host;
        private final int port;
        private final String password;
        private final String keyPrefix;

        public RedisConfig(String host, int port, String password, String keyPrefix) {
            this.host = host;
            this.port = port;
            this.password = password;
            this.keyPrefix = keyPrefix;
        }

        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getPassword() { return password; }
        public String getKeyPrefix() { return keyPrefix; }

        public boolean hasPassword() {
            return password != null && !password.isEmpty();
        }
    }
}