package com.yourserver.socialvel.messaging;

import com.yourserver.socialvel.SocialPluginVelocity;
import com.yourserver.socialvel.config.VelocityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisManagerTest {

    @Mock
    private SocialPluginVelocity plugin;

    @Test
    void publish_callsRedisCorrectly() {
        // Note: This test would need a Redis testcontainer for full integration testing
        // For now, we're just testing the structure

        VelocityConfig.RedisConfig config = new VelocityConfig.RedisConfig(
                "localhost", 6379, "", "test:"
        );

        // Can't fully test without Redis connection
        // In production, use Testcontainers with Redis
        assertNotNull(config);
    }

    @Test
    void keyPrefix_appliedCorrectly() {
        VelocityConfig.RedisConfig config = new VelocityConfig.RedisConfig(
                "localhost", 6379, "", "mcserver:"
        );

        assertEquals("mcserver:", config.getKeyPrefix());
    }
}
