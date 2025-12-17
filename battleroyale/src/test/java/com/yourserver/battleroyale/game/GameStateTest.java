package com.yourserver.battleroyale.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameState enum.
 * Tests state properties and transitions.
 */
class GameStateTest {

    @Test
    void values_returnsAllStates() {
        // Act
        GameState[] states = GameState.values();

        // Assert
        assertEquals(5, states.length);
        assertEquals(GameState.WAITING, states[0]);
        assertEquals(GameState.STARTING, states[1]);
        assertEquals(GameState.ACTIVE, states[2]);
        assertEquals(GameState.DEATHMATCH, states[3]);
        assertEquals(GameState.ENDING, states[4]);
    }

    @Test
    void isInProgress_waiting_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.WAITING.isInProgress());
    }

    @Test
    void isInProgress_starting_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.STARTING.isInProgress());
    }

    @Test
    void isInProgress_active_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.ACTIVE.isInProgress());
    }

    @Test
    void isInProgress_deathmatch_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.DEATHMATCH.isInProgress());
    }

    @Test
    void isInProgress_ending_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.ENDING.isInProgress());
    }

    @Test
    void canJoin_waiting_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.WAITING.canJoin());
    }

    @Test
    void canJoin_starting_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.STARTING.canJoin());
    }

    @Test
    void canJoin_active_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.ACTIVE.canJoin());
    }

    @Test
    void canJoin_deathmatch_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.DEATHMATCH.canJoin());
    }

    @Test
    void canJoin_ending_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.ENDING.canJoin());
    }

    @Test
    void hasStarted_waiting_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.WAITING.hasStarted());
    }

    @Test
    void hasStarted_starting_returnsFalse() {
        // Act & Assert
        assertFalse(GameState.STARTING.hasStarted());
    }

    @Test
    void hasStarted_active_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.ACTIVE.hasStarted());
    }

    @Test
    void hasStarted_deathmatch_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.DEATHMATCH.hasStarted());
    }

    @Test
    void hasStarted_ending_returnsTrue() {
        // Act & Assert
        assertTrue(GameState.ENDING.hasStarted());
    }

    @Test
    void ordinal_increasesInExpectedOrder() {
        // Assert - verify state transition order
        assertTrue(GameState.WAITING.ordinal() < GameState.STARTING.ordinal());
        assertTrue(GameState.STARTING.ordinal() < GameState.ACTIVE.ordinal());
        assertTrue(GameState.ACTIVE.ordinal() < GameState.DEATHMATCH.ordinal());
        assertTrue(GameState.DEATHMATCH.ordinal() < GameState.ENDING.ordinal());
    }

    @Test
    void valueOf_withValidName_returnsState() {
        // Act
        GameState state = GameState.valueOf("ACTIVE");

        // Assert
        assertEquals(GameState.ACTIVE, state);
    }

    @Test
    void valueOf_withInvalidName_throwsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                GameState.valueOf("INVALID")
        );
    }

    @Test
    void stateTransitionLogic_isConsistent() {
        // Test that only WAITING state allows joins
        int statesThatAllowJoin = 0;
        for (GameState state : GameState.values()) {
            if (state.canJoin()) {
                statesThatAllowJoin++;
            }
        }
        assertEquals(1, statesThatAllowJoin, "Only WAITING should allow joins");

        // Test that only ACTIVE and DEATHMATCH are in progress
        int statesInProgress = 0;
        for (GameState state : GameState.values()) {
            if (state.isInProgress()) {
                statesInProgress++;
            }
        }
        assertEquals(2, statesInProgress, "Only ACTIVE and DEATHMATCH should be in progress");
    }
}