package com.yourserver.battleroyale.loot;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LootTier enum.
 */
class LootTierTest {

    @Test
    void values_returnsAllTiers() {
        // Act
        LootTier[] tiers = LootTier.values();

        // Assert
        assertEquals(5, tiers.length);
        assertEquals(LootTier.COMMON, tiers[0]);
        assertEquals(LootTier.UNCOMMON, tiers[1]);
        assertEquals(LootTier.RARE, tiers[2]);
        assertEquals(LootTier.EPIC, tiers[3]);
        assertEquals(LootTier.LEGENDARY, tiers[4]);
    }

    @Test
    void getDisplayName_returnsCorrectNames() {
        // Assert
        assertEquals("Common", LootTier.COMMON.getDisplayName());
        assertEquals("Uncommon", LootTier.UNCOMMON.getDisplayName());
        assertEquals("Rare", LootTier.RARE.getDisplayName());
        assertEquals("Epic", LootTier.EPIC.getDisplayName());
        assertEquals("Legendary", LootTier.LEGENDARY.getDisplayName());
    }

    @Test
    void getColor_returnsCorrectColors() {
        // Assert
        assertEquals(NamedTextColor.GRAY, LootTier.COMMON.getColor());
        assertEquals(NamedTextColor.GREEN, LootTier.UNCOMMON.getColor());
        assertEquals(NamedTextColor.AQUA, LootTier.RARE.getColor());
        assertNotNull(LootTier.EPIC.getColor());
        assertEquals(NamedTextColor.GOLD, LootTier.LEGENDARY.getColor());
    }

    @Test
    void getWeight_returnsCorrectWeights() {
        // Assert
        assertEquals(50, LootTier.COMMON.getWeight());
        assertEquals(30, LootTier.UNCOMMON.getWeight());
        assertEquals(15, LootTier.RARE.getWeight());
        assertEquals(4, LootTier.EPIC.getWeight());
        assertEquals(1, LootTier.LEGENDARY.getWeight());
    }

    @Test
    void getWeight_totalEquals100() {
        // Act
        int total = 0;
        for (LootTier tier : LootTier.values()) {
            total += tier.getWeight();
        }

        // Assert
        assertEquals(100, total);
    }

    @Test
    void selectRandom_returnsValidTier() {
        // Act
        LootTier tier = LootTier.selectRandom();

        // Assert
        assertNotNull(tier);
    }

    @Test
    void selectRandom_distributionFollowsWeights() {
        // Act - generate many random selections
        Map<LootTier, Integer> counts = new EnumMap<>(LootTier.class);
        for (LootTier tier : LootTier.values()) {
            counts.put(tier, 0);
        }

        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            LootTier tier = LootTier.selectRandom();
            counts.put(tier, counts.get(tier) + 1);
        }

        // Assert - Common should appear most, Legendary least
        int commonCount = counts.get(LootTier.COMMON);
        int legendaryCount = counts.get(LootTier.LEGENDARY);

        assertTrue(commonCount > legendaryCount * 10,
                "Common should appear much more than Legendary");

        // Verify approximate distribution (within 20% margin)
        double commonRatio = (double) commonCount / iterations;
        assertTrue(commonRatio > 0.40 && commonRatio < 0.60,
                "Common should be ~50% of drops");
    }

    @Test
    void getLegacyColor_returnsCorrectColorCodes() {
        // Assert
        assertEquals("§7", LootTier.COMMON.getLegacyColor());
        assertEquals("§a", LootTier.UNCOMMON.getLegacyColor());
        assertEquals("§b", LootTier.RARE.getLegacyColor());
        assertEquals("§d", LootTier.EPIC.getLegacyColor());
        assertEquals("§6", LootTier.LEGENDARY.getLegacyColor());
    }

    @Test
    void ordinal_increasesWithRarity() {
        // Assert - verify order is correct
        assertTrue(LootTier.COMMON.ordinal() < LootTier.UNCOMMON.ordinal());
        assertTrue(LootTier.UNCOMMON.ordinal() < LootTier.RARE.ordinal());
        assertTrue(LootTier.RARE.ordinal() < LootTier.EPIC.ordinal());
        assertTrue(LootTier.EPIC.ordinal() < LootTier.LEGENDARY.ordinal());
    }
}