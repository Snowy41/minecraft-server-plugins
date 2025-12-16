package com.yourserver.battleroyale.zone;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Zone system.
 */
class ZoneTest {

    @Mock
    private World mockWorld;

    private Location center;
    private ZonePhase testPhase;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create test center location
        center = new Location(mockWorld, 0, 100, 0);

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
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);

        // Assert
        assertNotNull(zone);
        assertEquals(1000, zone.getCurrentRadius());
        assertEquals(1000, zone.getTargetRadius());
        assertFalse(zone.isShrinking());
        assertEquals(mockWorld, zone.getWorld());
    }

    @Test
    void startShrink_withNewRadius_updatesShrinkState() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);

        // Act
        zone.startShrink(500, 30);

        // Assert
        assertTrue(zone.isShrinking());
        assertEquals(500, zone.getTargetRadius());
        assertEquals(30, zone.getRemainingSeconds());
    }

    @Test
    void tick_whenShrinking_updatesRadius() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);
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
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);
        Location insideLocation = new Location(mockWorld, 100, 100, 100);
        when(mockWorld.equals(mockWorld)).thenReturn(true);

        // Act
        boolean result = zone.isInZone(insideLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInZone_withLocationOutside_returnsFalse() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 100, testPhase);
        Location outsideLocation = new Location(mockWorld, 500, 100, 500);
        when(mockWorld.equals(mockWorld)).thenReturn(true);

        // Act
        boolean result = zone.isInZone(outsideLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void getDistanceToEdge_withLocationInside_returnsPositive() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);
        Location insideLocation = new Location(mockWorld, 100, 100, 0);
        when(mockWorld.equals(mockWorld)).thenReturn(true);

        // Act
        double distance = zone.getDistanceToEdge(insideLocation);

        // Assert
        assertTrue(distance > 0);
    }

    @Test
    void getDistanceToEdge_withLocationOutside_returnsNegative() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 100, testPhase);
        Location outsideLocation = new Location(mockWorld, 200, 100, 0);
        when(mockWorld.equals(mockWorld)).thenReturn(true);

        // Act
        double distance = zone.getDistanceToEdge(outsideLocation);

        // Assert
        assertTrue(distance < 0);
    }

    @Test
    void isShrinkComplete_afterShrinkFinished_returnsTrue() {
        // Arrange
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);
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
        Zone zone = new Zone(mockWorld, center, 1000, testPhase);
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