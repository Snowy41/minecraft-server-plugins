package com.yourserver.lobby.util;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ItemBuilder.
 */
class ItemBuilderTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void build_withMaterial_createsItemStack() {
        ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD).build();

        assertNotNull(item);
        assertEquals(Material.DIAMOND_SWORD, item.getType());
        assertEquals(1, item.getAmount());
    }

    @Test
    void name_setsDisplayName() {
        Component name = Component.text("Test Item", NamedTextColor.GOLD);
        ItemStack item = new ItemBuilder(Material.STONE)
                .name(name)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertEquals(name, meta.displayName());
    }

    @Test
    void lore_setsLoreLines() {
        Component line1 = Component.text("Line 1");
        Component line2 = Component.text("Line 2");

        ItemStack item = new ItemBuilder(Material.STONE)
                .lore(line1, line2)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.lore());
        assertEquals(2, meta.lore().size());
        assertEquals(line1, meta.lore().get(0));
        assertEquals(line2, meta.lore().get(1));
    }

    @Test
    void amount_setsItemAmount() {
        ItemStack item = new ItemBuilder(Material.STONE)
                .amount(64)
                .build();

        assertEquals(64, item.getAmount());
    }

    @Test
    void enchant_addsEnchantment() {
        ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
                .enchant(Enchantment.SHARPNESS, 5)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.hasEnchant(Enchantment.SHARPNESS));
        assertEquals(5, meta.getEnchantLevel(Enchantment.SHARPNESS));
    }

    @Test
    void unbreakable_makesItemUnbreakable() {
        ItemStack item = new ItemBuilder(Material.DIAMOND_PICKAXE)
                .unbreakable()
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.isUnbreakable());
    }

    @Test
    void flags_addsItemFlags() {
        ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    void glow_addsEnchantmentAndHidesIt() {
        ItemStack item = new ItemBuilder(Material.STONE)
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.hasEnchant(Enchantment.UNBREAKING));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    void customModelData_setsCustomModelData() {
        ItemStack item = new ItemBuilder(Material.STONE)
                .customModelData(12345)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertTrue(meta.hasCustomModelData());
        assertEquals(12345, meta.getCustomModelData());
    }

    @Test
    void simple_createsSimpleItem() {
        Component name = Component.text("Simple Item");
        ItemStack item = ItemBuilder.simple(Material.STONE, name);

        assertNotNull(item);
        assertEquals(Material.STONE, item.getType());
        assertEquals(name, item.getItemMeta().displayName());
    }

    @Test
    void chainedCalls_workCorrectly() {
        Component name = Component.text("Chained Item", NamedTextColor.GOLD);
        Component lore1 = Component.text("First line", NamedTextColor.GRAY);
        Component lore2 = Component.text("Second line", NamedTextColor.GRAY);

        ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
                .name(name)
                .lore(lore1, lore2)
                .amount(1)
                .enchant(Enchantment.SHARPNESS, 5)
                .unbreakable()
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .glow()
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertEquals(name, meta.displayName());
        assertEquals(2, meta.lore().size());
        assertTrue(meta.hasEnchant(Enchantment.SHARPNESS));
        assertTrue(meta.hasEnchant(Enchantment.UNBREAKING));
        assertTrue(meta.isUnbreakable());
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    void addLore_appendsToExistingLore() {
        Component lore1 = Component.text("Line 1");
        Component lore2 = Component.text("Line 2");
        Component lore3 = Component.text("Line 3");

        ItemStack item = new ItemBuilder(Material.STONE)
                .lore(lore1, lore2)
                .addLore(lore3)
                .build();

        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        assertNotNull(meta.lore());
        assertEquals(3, meta.lore().size());
        assertEquals(lore3, meta.lore().get(2));
    }
}