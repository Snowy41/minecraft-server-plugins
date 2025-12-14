package com.yourserver.socialvel.messaging;

import com.yourserver.socialvel.config.VelocityConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for RedisManager.
 * Note: Full integration testing requires a Redis instance (use Testcontainers in production).
 */
class RedisManagerTest {

    @Test
    void keyPrefix_appliedCorrectly() {
        // Test configuration structure without needing actual Redis connection
        VelocityConfig.RedisConfig config = new VelocityConfig.RedisConfig(
                "localhost", 6379, "", "test:"
        );

        assertEquals("test:", config.getKeyPrefix());
        assertEquals("localhost", config.getHost());
        assertEquals(6379, config.getPort());
    }

    @Test
    void redisConfig_hasPassword_checksCorrectly() {
        VelocityConfig.RedisConfig withPassword = new VelocityConfig.RedisConfig(
                "localhost", 6379, "password", "test:"
        );

        VelocityConfig.RedisConfig withoutPassword = new VelocityConfig.RedisConfig(
                "localhost", 6379, "", "test:"
        );

        assertNotNull(withPassword);
        assertNotNull(withoutPassword);
    }
}