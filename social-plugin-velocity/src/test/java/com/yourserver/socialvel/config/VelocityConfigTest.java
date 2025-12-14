package com.yourserver.socialvel.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VelocityConfigTest {

    @Test
    void load_createsDefaultConfig_whenMissing(@TempDir Path tempDir) {
        VelocityConfig config = VelocityConfig.load(tempDir);

        assertNotNull(config);
        assertNotNull(config.getRedisConfig());
    }

    @Test
    void load_parsesRedisConfig(@TempDir Path tempDir) throws Exception {
        // Create config file
        String configContent = """
            redis:
              host: "test-redis.com"
              port: 6380
              password: "testpass"
              key-prefix: "test:"
            """;

        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("config.yml"), configContent);

        VelocityConfig config = VelocityConfig.load(tempDir);

        assertEquals("test-redis.com", config.getRedisConfig().getHost());
        assertEquals(6380, config.getRedisConfig().getPort());
        assertEquals("testpass", config.getRedisConfig().getPassword());
        assertEquals("test:", config.getRedisConfig().getKeyPrefix());
    }

    @Test
    void redisConfig_hasPassword_checksCorrectly() {
        VelocityConfig.RedisConfig config1 =
                new VelocityConfig.RedisConfig("localhost", 6379, "password", "prefix:");
        VelocityConfig.RedisConfig config2 =
                new VelocityConfig.RedisConfig("localhost", 6379, "", "prefix:");
        VelocityConfig.RedisConfig config3 =
                new VelocityConfig.RedisConfig("localhost", 6379, null, "prefix:");

        assertTrue(config1.hasPassword());
        assertFalse(config2.hasPassword());
        assertFalse(config3.hasPassword());
    }
}
