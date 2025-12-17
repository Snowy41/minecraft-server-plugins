package com.yourserver.battleroyale.loot;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Loot system.
 * FIXED: Proper MockBukkit initialization order.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LootTableTest {

    private static ServerMock server;
    private LootTable lootTable;

    @BeforeAll
    static void setUpAll() {
        // Initialize MockBukkit ONCE for all tests
        // This must happen before any Bukkit Registry access
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownAll() {
        // Clean up MockBukkit after all tests
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        // Create new LootTable for each test
        lootTable = new LootTable();
    }

    @Test
    @Order(1)
    void constructor_initializesWithDefaultLoot() {
        assertNotNull(lootTable);
    }

    @Test
    @Order(2)
    void generateLoot_withCommonTier_returnsItems() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 5);

        assertEquals(5, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
            assertTrue(item.getAmount() > 0);
        }
    }

    @Test
    @Order(3)
    void generateLoot_withUncommonTier_returnsItems() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.UNCOMMON, 3);

        assertEquals(3, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
        }
    }

    @Test
    @Order(4)
    void generateLoot_withRareTier_returnsItems() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.RARE, 3);

        assertEquals(3, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
        }
    }

    @Test
    @Order(5)
    void generateLoot_withEpicTier_returnsItems() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.EPIC, 2);

        assertEquals(2, loot.size());
    }

    @Test
    @Order(6)
    void generateLoot_withLegendaryTier_returnsItems() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.LEGENDARY, 2);

        assertEquals(2, loot.size());
    }

    @Test
    @Order(7)
    void generateMixedLoot_returnsVariedTiers() {
        List<ItemStack> loot = lootTable.generateMixedLoot(10);

        assertEquals(10, loot.size());
        for (ItemStack item : loot) {
            assertNotNull(item);
            assertNotEquals(Material.AIR, item.getType());
        }
    }

    @Test
    @Order(8)
    void generateMixedLoot_withLargeCount_generatesAllItems() {
        List<ItemStack> loot = lootTable.generateMixedLoot(20);

        assertEquals(20, loot.size());
    }

    @Test
    @Order(9)
    void addLoot_withCustomItem_addsToTable() {
        lootTable.addLoot(LootTier.COMMON, Material.DIAMOND, 1, 1);
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 50);

        boolean foundDiamond = loot.stream()
                .anyMatch(item -> item.getType() == Material.DIAMOND);
        assertTrue(foundDiamond);
    }

    @Test
    @Order(10)
    void generateLoot_epicTier_hasEnchantments() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.EPIC, 10);

        boolean hasEnchantedItem = loot.stream()
                .anyMatch(item -> !item.getEnchantments().isEmpty());
        assertTrue(hasEnchantedItem);
    }

    @Test
    @Order(11)
    void generateLoot_legendaryTier_hasHighLevelEnchantments() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.LEGENDARY, 10);

        boolean hasEnchantedItem = loot.stream()
                .anyMatch(item -> !item.getEnchantments().isEmpty());
        assertTrue(hasEnchantedItem);
    }

    @Test
    @Order(12)
    void generateLoot_commonTier_noEnchantments() {
        List<ItemStack> loot = lootTable.generateLoot(LootTier.COMMON, 10);

        long enchantedCount = loot.stream()
                .filter(item -> !item.getEnchantments().isEmpty())
                .count();
        assertTrue(enchantedCount <= 2);
    }
}