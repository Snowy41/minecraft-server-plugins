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

    public boolean canTakeDamage() {
        return this == PLAYING;
    }


    public boolean canDealDamage() {
        return this == PLAYING;
    }
    public boolean isPlayable() {
        return this == PLAYING;
    }
}