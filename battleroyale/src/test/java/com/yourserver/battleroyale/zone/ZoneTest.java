package com.yourserver.battleroyale.zone;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Zone system.
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
        Zone zone = new Zone(world, center, 1000, testPhase);

        assertNotNull(zone);
        assertEquals(1000, zone.getCurrentRadius());
        assertEquals(1000, zone.getTargetRadius());
        assertFalse(zone.isShrinking());
        assertEquals(world, zone.getWorld());
    }

    @Test
    void startShrink_withNewRadius_updatesShrinkState() {
        Zone zone = new Zone(world, center, 1000, testPhase);

        zone.startShrink(500, 30);

        assertTrue(zone.isShrinking());
        assertEquals(500, zone.getTargetRadius());
        assertTrue(zone.getRemainingSeconds() <= 30);
    }

    @Test
    void tick_whenShrinking_updatesRadius() {
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 1);

        for (int i = 0; i < 20; i++) {
            zone.tick();
            try {
                Thread.sleep(50);
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
        Zone zone = new Zone(world, center, 1000, testPhase);
        Location insideLocation = new Location(world, 100, 100, 100);

        boolean result = zone.isInZone(insideLocation);

        assertTrue(result);
    }

    @Test
    void isInZone_withLocationOutside_returnsFalse() {
        Zone zone = new Zone(world, center, 100, testPhase);
        Location outsideLocation = new Location(world, 500, 100, 500);

        boolean result = zone.isInZone(outsideLocation);

        assertFalse(result);
    }

    @Test
    void getDistanceToEdge_withLocationInside_returnsPositive() {
        Zone zone = new Zone(world, center, 1000, testPhase);
        Location insideLocation = new Location(world, 100, 100, 0);

        double distance = zone.getDistanceToEdge(insideLocation);

        assertTrue(distance > 0);
    }

    @Test
    void getDistanceToEdge_withLocationOutside_returnsNegative() {
        Zone zone = new Zone(world, center, 100, testPhase);
        Location outsideLocation = new Location(world, 200, 100, 0);

        double distance = zone.getDistanceToEdge(outsideLocation);

        assertTrue(distance < 0);
    }

    @Test
    void isShrinkComplete_afterShrinkFinished_returnsTrue() {
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 0); // Instant shrink

        zone.tick();
        boolean result = zone.isShrinkComplete();

        assertTrue(result);
    }

    @Test
    void getShrinkProgress_duringMiddleOfShrink_returnsPartialProgress() {
        Zone zone = new Zone(world, center, 1000, testPhase);
        zone.startShrink(500, 2); // 2 second shrink

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        zone.tick();
        double progress = zone.getShrinkProgress();

        assertTrue(progress > 0.0);
        assertTrue(progress < 1.0);
    }
}