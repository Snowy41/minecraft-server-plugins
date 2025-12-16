package com.yourserver.battleroyale.zone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ZonePhase configuration.
 */
class ZonePhaseTest {

    @Test
    void constructor_withValidData_createsPhase() {
        // Act
        ZonePhase phase = new ZonePhase(1, 120, 60, 500, 2.0, 20);

        // Assert
        assertEquals(1, phase.getId());
        assertEquals(120, phase.getWaitDuration());
        assertEquals(60, phase.getShrinkDuration());
        assertEquals(500, phase.getTargetRadius());
        assertEquals(2.0, phase.getDamagePerTick());
        assertEquals(20, phase.getTickInterval());
    }

    @Test
    void constructor_withNegativeValues_convertsToPositive() {
        // Act
        ZonePhase phase = new ZonePhase(1, -120, -60, -500, -2.0, -20);

        // Assert
        assertEquals(0, phase.getWaitDuration());
        assertEquals(1, phase.getShrinkDuration()); // Minimum 1
        assertEquals(0, phase.getTargetRadius());
        assertEquals(0.0, phase.getDamagePerTick());
        assertEquals(1, phase.getTickInterval()); // Minimum 1
    }

    @Test
    void getTotalDuration_returnsWaitPlusShrink() {
        // Arrange
        ZonePhase phase = new ZonePhase(1, 120, 60, 500, 2.0, 20);

        // Act
        int total = phase.getTotalDuration();

        // Assert
        assertEquals(180, total);
    }

    @Test
    void isFinalPhase_withSmallRadius_returnsTrue() {
        // Arrange
        ZonePhase phase = new ZonePhase(7, 60, 30, 15, 10.0, 10);

        // Act
        boolean isFinal = phase.isFinalPhase();

        // Assert
        assertTrue(isFinal);
    }

    @Test
    void isFinalPhase_withLargeRadius_returnsFalse() {
        // Arrange
        ZonePhase phase = new ZonePhase(1, 120, 60, 500, 2.0, 20);

        // Act
        boolean isFinal = phase.isFinalPhase();

        // Assert
        assertFalse(isFinal);
    }

    @Test
    void createDefault_createsPhaseWithDefaultValues() {
        // Act
        ZonePhase phase = ZonePhase.createDefault(1);

        // Assert
        assertEquals(1, phase.getId());
        assertEquals(180, phase.getWaitDuration());
        assertEquals(60, phase.getShrinkDuration());
        assertEquals(500, phase.getTargetRadius());
        assertEquals(1.0, phase.getDamagePerTick());
        assertEquals(20, phase.getTickInterval());
    }

    @Test
    void builder_withAllProperties_buildsCorrectPhase() {
        // Act
        ZonePhase phase = new ZonePhase.Builder()
                .id(3)
                .waitDuration(90)
                .shrinkDuration(45)
                .targetRadius(300)
                .damagePerTick(3.0)
                .tickInterval(20)
                .build();

        // Assert
        assertEquals(3, phase.getId());
        assertEquals(90, phase.getWaitDuration());
        assertEquals(45, phase.getShrinkDuration());
        assertEquals(300, phase.getTargetRadius());
        assertEquals(3.0, phase.getDamagePerTick());
        assertEquals(20, phase.getTickInterval());
    }

    @Test
    void builder_withPartialProperties_usesDefaults() {
        // Act
        ZonePhase phase = new ZonePhase.Builder()
                .id(5)
                .targetRadius(100)
                .build();

        // Assert
        assertEquals(5, phase.getId());
        assertEquals(100, phase.getTargetRadius());
        assertEquals(180, phase.getWaitDuration()); // Default
        assertEquals(60, phase.getShrinkDuration()); // Default
    }

    @Test
    void toString_returnsFormattedString() {
        // Arrange
        ZonePhase phase = new ZonePhase(1, 120, 60, 500, 2.0, 20);

        // Act
        String result = phase.toString();

        // Assert
        assertTrue(result.contains("id=1"));
        assertTrue(result.contains("wait=120s"));
        assertTrue(result.contains("shrink=60s"));
        assertTrue(result.contains("radius=500"));
        assertTrue(result.contains("damage=2.0"));
    }
}