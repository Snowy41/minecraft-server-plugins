package com.yourserver.battleroyale.zone;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Zone system.
 * FIXED: Proper MockBukkit initialization - server/world are static,
 * but center/testPhase are recreated per-test.
 */
class ZoneTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location center;
    private ZonePhase testPhase;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("zone_world");
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        center = new Location(world, 0, 100, 0);

        // Create test phase
        testPhase = new ZonePhase.Builder()
                .id(1)
                .waitDuration(60)
                .shrinkDuration(30)
                .targetRadius(500)
                .damagePerTick(1.0)
                .tickInterval(20)
                .build();
    }

    @Test
    void constructor_withValidData_createsZone() {
        // Act
        Zone zone = new Zone(world, center, 1000, testPhase);

        // Assert
        assertNotNull(zone);
        assertEquals(1000, zone.getCurrentRadius());
        assertEquals(1000, zone.getTargetRadius());
        assertFalse(zone.isShrinking());
        assertEquals(world, zone.getWorld());
    }

    @Test
    void startShrink_withNewRadius_updatesShrinkState() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);

        // Act
        zone.startShrink(500, 30);

        // Assert
        assertTrue(zone.isShrinking());
        assertEquals(500, zone.getTargetRadius());
        // Note: getRemainingSeconds may vary slightly due to timing
        assertTrue(zone.getRemainingSeconds() <= 30);
    }

    @Test
    void tick_whenShrinking_updatesRadius() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 1); // 1 second shrink for testing

        // Act - simulate ticks over 1 second
        for (int i = 0; i < 20; i++) { // 20 ticks = 1 second
            zone.tick();
            try {
                Thread.sleep(50); // Simulate tick delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Assert
        assertTrue(zone.getCurrentRadius() < 1000);
        assertTrue(zone.getCurrentRadius() >= 500);
    }

    @Test
    void isInZone_withLocationInside_returnsTrue() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);
        Location insideLocation = new Location(world, 100, 100, 100);

        // Act
        boolean result = zone.isInZone(insideLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInZone_withLocationOutside_returnsFalse() {
        // Arrange
        Zone zone = new Zone(world, center, 100, testPhase);
        Location outsideLocation = new Location(world, 500, 100, 500);

        // Act
        boolean result = zone.isInZone(outsideLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void getDistanceToEdge_withLocationInside_returnsPositive() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);
        Location insideLocation = new Location(world, 100, 100, 0);

        // Act
        double distance = zone.getDistanceToEdge(insideLocation);

        // Assert
        assertTrue(distance > 0);
    }

    @Test
    void getDistanceToEdge_withLocationOutside_returnsNegative() {
        // Arrange
        Zone zone = new Zone(world, center, 100, testPhase);
        Location outsideLocation = new Location(world, 200, 100, 0);

        // Act
        double distance = zone.getDistanceToEdge(outsideLocation);

        // Assert
        assertTrue(distance < 0);
    }

    @Test
    void isShrinkComplete_afterShrinkFinished_returnsTrue() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 0); // Instant shrink

        // Act
        zone.tick();
        boolean result = zone.isShrinkComplete();

        // Assert
        assertTrue(result);
    }

    @Test
    void getShrinkProgress_duringMiddleOfShrink_returnsPartialProgress() {
        // Arrange
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 2); // 2 second shrink

        // Act - wait 1 second
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        zone.tick();
        double progress = zone.getShrinkProgress();

        // Assert
        assertTrue(progress > 0.0);
        assertTrue(progress < 1.0);
    }
}