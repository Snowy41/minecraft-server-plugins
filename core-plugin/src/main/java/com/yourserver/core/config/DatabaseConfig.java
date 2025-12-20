package com.yourserver.core.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;

/**
 * Database configuration loader supporting both MySQL and Redis.
 * Provides type-safe access to database settings.
 */
public class DatabaseConfig {

    private final MySQLConfig mysqlConfig;
    private final RedisConfig redisConfig;

    private DatabaseConfig(MySQLConfig mysqlConfig, RedisConfig redisConfig) {
        this.mysqlConfig = mysqlConfig;
        this.redisConfig = redisConfig;
    }

    /**
     * Loads complete database configuration from database.yml
     */
    public static DatabaseConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "database.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            MySQLConfig mysql = loadMySQL(root.node("mysql"));
            RedisConfig redis = loadRedis(root.node("redis"));

            return new DatabaseConfig(mysql, redis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load database.yml", e);
        }
    }

    /**
     * Loads Redis-only configuration (backwards compatibility)
     */
    public static DatabaseConfig loadRedisOnly(File dataFolder) {
        File configFile = new File(dataFolder, "redis.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();
            RedisConfig redis = loadRedis(root.node("redis"));

            // Return config with null MySQL (Redis-only mode)
            return new DatabaseConfig(null, redis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load redis.yml", e);
        }
    }

    private static MySQLConfig loadMySQL(CommentedConfigurationNode node) {
        String host = node.node("host").getString("localhost");
        int port = node.node("port").getInt(3306);
        String database = node.node("database").getString("minecraft_server");
        String username = node.node("username").getString("minecraft");
        String password = node.node("password").getString("");

        // Pool settings
        CommentedConfigurationNode pool = node.node("pool");
        int maxPoolSize = pool.node("maximum-pool-size").getInt(10);
        int minIdle = pool.node("minimum-idle").getInt(2);
        long connectionTimeout = pool.node("connection-timeout").getLong(30000);
        long idleTimeout = pool.node("idle-timeout").getLong(600000);
        long maxLifetime = pool.node("max-lifetime").getLong(1800000);

        return new MySQLConfig(
                host, port, database, username, password,
                maxPoolSize, minIdle, connectionTimeout, idleTimeout, maxLifetime
        );
    }

    private static RedisConfig loadRedis(CommentedConfigurationNode node) {
        return new RedisConfig(
                node.node("host").getString("localhost"),
                node.node("port").getInt(6379),
                node.node("password").getString(""),
                node.node("key-prefix").getString("mcserver:")
        );
    }

    public MySQLConfig getMySQLConfig() {
        return mysqlConfig;
    }

    public RedisConfig getRedisConfig() {
        return redisConfig;
    }

    public boolean hasMySQLConfig() {
        return mysqlConfig != null;
    }

    /**
     * MySQL connection configuration.
     */
    public static class MySQLConfig {
        private final String host;
        private final int port;
        private final String database;
        private final String username;
        private final String password;
        private final int maximumPoolSize;
        private final int minimumIdle;
        private final long connectionTimeout;
        private final long idleTimeout;
        private final long maxLifetime;

        public MySQLConfig(String host, int port, String database, String username, String password,
                           int maximumPoolSize, int minimumIdle, long connectionTimeout,
                           long idleTimeout, long maxLifetime) {
            this.host = host;
            this.port = port;
            this.database = database;
            this.username = username;
            this.password = password;
            this.maximumPoolSize = maximumPoolSize;
            this.minimumIdle = minimumIdle;
            this.connectionTimeout = connectionTimeout;
            this.idleTimeout = idleTimeout;
            this.maxLifetime = maxLifetime;
        }

        // Getters
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getDatabase() { return database; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public int getMinimumIdle() { return minimumIdle; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public long getIdleTimeout() { return idleTimeout; }
        public long getMaxLifetime() { return maxLifetime; }
    }

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