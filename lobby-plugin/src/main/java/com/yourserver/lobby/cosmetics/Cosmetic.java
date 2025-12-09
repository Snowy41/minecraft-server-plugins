package com.yourserver.lobby.cosmetics;

import org.bukkit.Particle;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a cosmetic item (trail, effect, etc.)
 */
public class Cosmetic {

    private final String id;
    private final CosmeticType type;
    private final String name;
    private final String permission;
    private final Particle particle; // For trails

    public Cosmetic(
            @NotNull String id,
            @NotNull CosmeticType type,
            @NotNull String name,
            @NotNull String permission,
            Particle particle
    ) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.permission = permission;
        this.particle = particle;
    }

    public String getId() {
        return id;
    }

    public CosmeticType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public Particle getParticle() {
        return particle;
    }
}