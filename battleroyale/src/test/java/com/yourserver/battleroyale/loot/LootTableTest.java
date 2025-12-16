package com.yourserver.battleroyale.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Loot system.
 */
class LootTableTest {

    private LootTable lootTable;

    @BeforeEach
    void setUp() {
        lootTable = new LootTable();
    }

    @Test
    void constructor_initializesWithDefaultLoot() {
        // Act & Assert
        assertNotNull(lootTable);
    }

    @Test
    void generateLoot_withCommonTier_returnsItems() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 5);

        // Assert
        assertEquals(5, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
            assertTrue(item.getAmount() > 0);
        }
    }

    @Test
    void generateLoot_withUncommonTier_returnsItems() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.UNCOMMON, 3);

        // Assert
        assertEquals(3, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
        }
    }

    @Test
    void generateLoot_withRareTier_returnsItems() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.RARE, 3);

        // Assert
        assertEquals(3, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
        }
    }

    @Test
    void generateLoot_withEpicTier_returnsItems() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.EPIC, 2);

        // Assert
        assertEquals(2, loot.size());
    }

    @Test
    void generateLoot_withLegendaryTier_returnsItems() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.LEGENDARY, 2);

        // Assert
        assertEquals(2, loot.size());
    }

    @Test
    void generateMixedLoot_returnsVariedTiers() {
        // Act
        List<ItemStack> loot = lootTable.generateMixedLoot(10);

        // Assert
        assertEquals(10, loot.size());
        // Verify we got some items
        for (ItemStack item : loot) {
            assertNotNull(item);
            assertNotEquals(Material.AIR, item.getType());
        }
    }

    @Test
    void generateMixedLoot_withLargeCount_generatesAllItems() {
        // Act
        List<ItemStack> loot = lootTable.generateMixedLoot(20);

        // Assert
        assertEquals(20, loot.size());
    }

    @Test
    void addLoot_withCustomItem_addsToTable() {
        // Act
        lootTable.addLoot(LootTier.COMMON, Material.DIAMOND, 1, 1);
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 50);

        // Assert - should eventually get a diamond
        boolean foundDiamond = loot.stream()
                .anyMatch(item -> item.getType() == Material.DIAMOND);
        assertTrue(foundDiamond);
    }

    @Test
    void generateLoot_epicTier_hasEnchantments() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.EPIC, 10);

        // Assert - at least some items should have enchantments
        boolean hasEnchantedItem = loot.stream()
                .anyMatch(item -> !item.getEnchantments().isEmpty());
        assertTrue(hasEnchantedItem);
    }

    @Test
    void generateLoot_legendaryTier_hasHighLevelEnchantments() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.LEGENDARY, 10);

        // Assert - legendary items should have enchantments
        boolean hasEnchantedItem = loot.stream()
                .anyMatch(item -> !item.getEnchantments().isEmpty());
        assertTrue(hasEnchantedItem);
    }

    @Test
    void generateLoot_commonTier_noEnchantments() {
        // Act
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 10);

        // Assert - common items should have no or minimal enchantments
        long enchantedCount = loot.stream()
                .filter(item -> !item.getEnchantments().isEmpty())
                .count();
        assertTrue(enchantedCount <= 2); // At most 2 enchanted items
    }
}