package com.yourserver.lobby.cosmetics.trail;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a particle trail following a player.
 */
public class ParticleTrail {

    private final Player player;
    private final Particle particle;
    private final int spawnRate;
    private int tickCounter;

    public ParticleTrail(@NotNull Player player, @NotNull Particle particle, int spawnRate) {
        this.player = player;
        this.particle = particle;
        this.spawnRate = spawnRate;
        this.tickCounter = 0;
    }

    /**
     * Spawns particles at the player's location.
     */
    public void spawn() {
        tickCounter++;

        if (tickCounter < spawnRate) {
            return;
        }

        tickCounter = 0;

        if (!player.isOnline()) {
            return;
        }

        Location location = player.getLocation().clone();
        location.add(0, 0.5, 0); // Spawn at player's center

        // Spawn particles
        player.getWorld().spawnParticle(
                particle,
                location,
                1,      // count
                0.2,    // offset X
                0.2,    // offset Y
                0.2,    // offset Z
                0.01    // extra (speed)
        );
    }

    public Player getPlayer() {
        return player;
    }

    public Particle getParticle() {
        return particle;
    }
}