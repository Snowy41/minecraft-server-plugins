package com.yourserver.battleroyale.player;

/**
 * Represents a player's state in a battle royale game.
 */
public enum PlayerState {

    /**
     * Player is in pre-game lobby, waiting for game to start.
     */
    WAITING,

    /**
     * Player is alive and actively playing.
     */
    PLAYING,

    /**
     * Player is dead and spectating.
     */
    SPECTATING,

    /**
     * Player has disconnected from the game.
     */
    DISCONNECTED;

    /**
     * Checks if the player can take damage.
     */
    public boolean canTakeDamage() {
        return this == PLAYING;
    }

    /**
     * Checks if the player can deal damage.
     */
    public boolean canDealDamage() {
        return this == PLAYING;
    }

    /**
     * Checks if the player is in a playable state.
     */
    public boolean isPlayable() {
        return this == PLAYING;
    }
}