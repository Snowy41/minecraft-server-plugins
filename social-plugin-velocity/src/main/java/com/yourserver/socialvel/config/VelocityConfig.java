package com.yourserver.socialvel.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VelocityConfig {

    private final RedisConfig redisConfig;

    private VelocityConfig(RedisConfig redisConfig) {
        this.redisConfig = redisConfig;
    }

    public static VelocityConfig load(Path dataDirectory) {
        try {
            Files.createDirectories(dataDirectory);
            Path configFile = dataDirectory.resolve("config.yml");

            if (!Files.exists(configFile)) {
                createDefaultConfig(configFile);
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();
            RedisConfig redis = loadRedis(root.node("redis"));

            return new VelocityConfig(redis);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private static void createDefaultConfig(Path configFile) throws IOException {
        String defaultConfig = """
                # Redis Configuration (must match Paper servers)
                redis:
                  host: "redis-13511.crce218.eu-central-1-1.ec2.cloud.redislabs.com"
                  port: 13511
                  password: "TUdVa4lhKdpQDIiI5ynKrb3ylzllqmmp"
                  key-prefix: "mcserver:"
                """;

        Files.writeString(configFile, defaultConfig);
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