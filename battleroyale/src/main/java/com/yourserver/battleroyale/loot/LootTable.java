package com.yourserver.battleroyale.loot;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages loot tables for different tiers.
 * Generates random items based on tier and configuration.
 */
public class LootTable {

    private final Map<LootTier, List<LootItem>> lootByTier;
    private final Random random;

    public LootTable() {
        this.lootByTier = new EnumMap<>(LootTier.class);
        this.random = new Random();

        initializeDefaultLoot();
    }

    /**
     * Initializes default loot for all tiers.
     */
    private void initializeDefaultLoot() {
        // COMMON LOOT
        addLoot(LootTier.COMMON, Material.WOODEN_SWORD, 1, 1);
        addLoot(LootTier.COMMON, Material.WOODEN_AXE, 1, 1);
        addLoot(LootTier.COMMON, Material.LEATHER_HELMET, 1, 1);
        addLoot(LootTier.COMMON, Material.LEATHER_CHESTPLATE, 1, 1);
        addLoot(LootTier.COMMON, Material.LEATHER_LEGGINGS, 1, 1);
        addLoot(LootTier.COMMON, Material.LEATHER_BOOTS, 1, 1);
        addLoot(LootTier.COMMON, Material.BREAD, 1, 4);
        addLoot(LootTier.COMMON, Material.ARROW, 8, 16);
        addLoot(LootTier.COMMON, Material.BOW, 1, 1);

        // UNCOMMON LOOT
        addLoot(LootTier.UNCOMMON, Material.STONE_SWORD, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.STONE_AXE, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.CHAINMAIL_HELMET, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.CHAINMAIL_CHESTPLATE, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.CHAINMAIL_LEGGINGS, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.CHAINMAIL_BOOTS, 1, 1);
        addLoot(LootTier.UNCOMMON, Material.COOKED_BEEF, 2, 6);
        addLoot(LootTier.UNCOMMON, Material.ARROW, 16, 32);
        addLoot(LootTier.UNCOMMON, Material.CROSSBOW, 1, 1);

        // RARE LOOT
        addLoot(LootTier.RARE, Material.IRON_SWORD, 1, 1);
        addLoot(LootTier.RARE, Material.IRON_AXE, 1, 1);
        addLoot(LootTier.RARE, Material.IRON_HELMET, 1, 1);
        addLoot(LootTier.RARE, Material.IRON_CHESTPLATE, 1, 1);
        addLoot(LootTier.RARE, Material.IRON_LEGGINGS, 1, 1);
        addLoot(LootTier.RARE, Material.IRON_BOOTS, 1, 1);
        addLoot(LootTier.RARE, Material.GOLDEN_APPLE, 1, 2);
        addLoot(LootTier.RARE, Material.SHIELD, 1, 1);
        addLoot(LootTier.RARE, Material.ENDER_PEARL, 1, 3);

        // EPIC LOOT
        addLoot(LootTier.EPIC, Material.DIAMOND_SWORD, 1, 1);
        addLoot(LootTier.EPIC, Material.DIAMOND_AXE, 1, 1);
        addLoot(LootTier.EPIC, Material.DIAMOND_HELMET, 1, 1);
        addLoot(LootTier.EPIC, Material.DIAMOND_CHESTPLATE, 1, 1);
        addLoot(LootTier.EPIC, Material.DIAMOND_LEGGINGS, 1, 1);
        addLoot(LootTier.EPIC, Material.DIAMOND_BOOTS, 1, 1);
        addLoot(LootTier.EPIC, Material.ENCHANTED_GOLDEN_APPLE, 1, 1);
        addLoot(LootTier.EPIC, Material.TOTEM_OF_UNDYING, 1, 1);

        // LEGENDARY LOOT
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_SWORD, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_AXE, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_HELMET, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_CHESTPLATE, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_LEGGINGS, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.NETHERITE_BOOTS, 1, 1);
        addLoot(LootTier.LEGENDARY, Material.ENCHANTED_GOLDEN_APPLE, 2, 3);
        addLoot(LootTier.LEGENDARY, Material.TOTEM_OF_UNDYING, 1, 2);
    }

    /**
     * Adds a loot item to a tier.
     */
    public void addLoot(@NotNull LootTier tier, @NotNull Material material,
                        int minAmount, int maxAmount) {
        lootByTier.computeIfAbsent(tier, k -> new ArrayList<>())
                .add(new LootItem(material, minAmount, maxAmount));
    }

    /**
     * Generates random loot for a specific tier.
     *
     * @param tier The loot tier
     * @param itemCount Number of items to generate
     * @return List of randomly generated items
     */
    @NotNull
    public List<ItemStack> generateLoot(@NotNull LootTier tier, int itemCount) {
        List<ItemStack> loot = new ArrayList<>();
        List<LootItem> available = lootByTier.get(tier);

        if (available == null || available.isEmpty()) {
            return loot;
        }

        for (int i = 0; i < itemCount; i++) {
            LootItem item = available.get(random.nextInt(available.size()));
            ItemStack stack = item.create();

            // Apply tier-specific enchantments
            applyEnchantments(stack, tier);

            loot.add(stack);
        }

        return loot;
    }

    /**
     * Generates random loot for a chest (mixed tiers).
     *
     * @param itemCount Number of items to generate
     * @return List of items with random tiers
     */
    @NotNull
    public List<ItemStack> generateMixedLoot(int itemCount) {
        List<ItemStack> loot = new ArrayList<>();

        for (int i = 0; i < itemCount; i++) {
            LootTier tier = LootTier.selectRandom();
            List<ItemStack> tierLoot = generateLoot(tier, 1);
            if (!tierLoot.isEmpty()) {
                loot.add(tierLoot.get(0));
            }
        }

        return loot;
    }

    /**
     * Applies tier-appropriate enchantments to an item.
     */
    private void applyEnchantments(@NotNull ItemStack item, @NotNull LootTier tier) {
        // Only enchant weapons and armor
        Material type = item.getType();
        if (!isEnchantable(type)) {
            return;
        }

        int enchantLevel = switch (tier) {
            case COMMON -> 0;
            case UNCOMMON -> random.nextInt(2); // 0-1
            case RARE -> 1 + random.nextInt(2); // 1-2
            case EPIC -> 2 + random.nextInt(2); // 2-3
            case LEGENDARY -> 3 + random.nextInt(2); // 3-4
        };

        if (enchantLevel == 0) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Apply appropriate enchantments
        if (isSword(type)) {
            meta.addEnchant(Enchantment.SHARPNESS, enchantLevel, true);
            if (tier.ordinal() >= LootTier.RARE.ordinal()) {
                meta.addEnchant(Enchantment.KNOCKBACK, 1, true);
            }
        } else if (isArmor(type)) {
            meta.addEnchant(Enchantment.PROTECTION, enchantLevel, true);
            if (tier.ordinal() >= LootTier.EPIC.ordinal()) {
                meta.addEnchant(Enchantment.UNBREAKING, 2, true);
            }
        } else if (isBow(type)) {
            meta.addEnchant(Enchantment.POWER, enchantLevel, true);
            if (tier.ordinal() >= LootTier.RARE.ordinal()) {
                meta.addEnchant(Enchantment.INFINITY, 1, true);
            }
        }

        item.setItemMeta(meta);
    }

    private boolean isEnchantable(Material type) {
        return isSword(type) || isArmor(type) || isBow(type);
    }

    private boolean isSword(Material type) {
        return type.name().endsWith("_SWORD");
    }

    private boolean isArmor(Material type) {
        String name = type.name();
        return name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS");
    }

    private boolean isBow(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    // ===== LOOT ITEM CLASS =====

    private static class LootItem {
        private final Material material;
        private final int minAmount;
        private final int maxAmount;

        LootItem(Material material, int minAmount, int maxAmount) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        ItemStack create() {
            Random random = new Random();
            int amount = minAmount + random.nextInt(maxAmount - minAmount + 1);
            return new ItemStack(material, amount);
        }
    }
}