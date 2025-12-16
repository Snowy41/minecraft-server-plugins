package com.yourserver.battleroyale.game;

/**
 * Represents the current state of a battle royale game.
 *
 * State transitions:
 * WAITING → STARTING → ACTIVE → DEATHMATCH → ENDING
 */
public enum GameState {

    /**
     * Waiting for players in pre-game lobby.
     * Players can see the map below and strategize.
     */
    WAITING,

    /**
     * Countdown before game starts (3, 2, 1, GO!).
     * Players still in lobby, cannot leave.
     */
    STARTING,

    /**
     * Game in progress.
     * Players fighting, zone shrinking, normal gameplay.
     */
    ACTIVE,

    /**
     * Deathmatch phase.
     * Remaining players teleported to small arena.
     * No hiding, forced combat.
     * Triggered by: time limit (1 hour) OR very small zone.
     */
    DEATHMATCH,

    /**
     * Game ending.
     * Winner announced, stats saved, cleanup in progress.
     */
    ENDING;

    /**
     * Checks if the game is in progress (active gameplay).
     */
    public boolean isInProgress() {
        return this == ACTIVE || this == DEATHMATCH;
    }

    /**
     * Checks if players can join the game.
     */
    public boolean canJoin() {
        return this == WAITING;
    }

    /**
     * Checks if the game has started.
     */
    public boolean hasStarted() {
        return this != WAITING && this != STARTING;
    }
}