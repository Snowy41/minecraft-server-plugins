package com.yourserver.battleroyale.loot;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

/**
 * Represents loot rarity tiers with colors and weights.
 */
public enum LootTier {

    COMMON("Common", NamedTextColor.GRAY, 50),
    UNCOMMON("Uncommon", NamedTextColor.GREEN, 30),
    RARE("Rare", NamedTextColor.AQUA, 15),
    EPIC("Epic", TextColor.color(0xAA00FF), 4),
    LEGENDARY("Legendary", NamedTextColor.GOLD, 1);

    private final String displayName;
    private final TextColor color;
    private final int weight;

    LootTier(String displayName, TextColor color, int weight) {
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public TextColor getColor() {
        return color;
    }

    public int getWeight() {
        return weight;
    }

    /**
     * Selects a random tier based on weights.
     */
    @NotNull
    public static LootTier selectRandom() {
        int totalWeight = 0;
        for (LootTier tier : values()) {
            totalWeight += tier.weight;
        }

        int random = (int) (Math.random() * totalWeight);
        int currentWeight = 0;

        for (LootTier tier : values()) {
            currentWeight += tier.weight;
            if (random < currentWeight) {
                return tier;
            }
        }

        return COMMON;
    }

    /**
     * Gets the tier color as a legacy color code.
     */
    @NotNull
    public String getLegacyColor() {
        return switch (this) {
            case COMMON -> "§7";
            case UNCOMMON -> "§a";
            case RARE -> "§b";
            case EPIC -> "§d";
            case LEGENDARY -> "§6";
        };
    }
}