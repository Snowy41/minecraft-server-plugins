package com.yourserver.lobby.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent builder for creating ItemStacks with Adventure API components.
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;

    public ItemBuilder(@NotNull Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }

    public ItemBuilder(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
    }

    /**
     * Sets the item's display name.
     *
     * @param name The display name component
     * @return This builder
     */
    public ItemBuilder name(@NotNull Component name) {
        if (itemMeta != null) {
            itemMeta.displayName(name);
        }
        return this;
    }

    /**
     * Sets the item's lore.
     *
     * @param lore The lore lines
     * @return This builder
     */
    public ItemBuilder lore(@NotNull Component... lore) {
        return lore(Arrays.asList(lore));
    }

    /**
     * Sets the item's lore.
     *
     * @param lore The lore lines
     * @return This builder
     */
    public ItemBuilder lore(@NotNull List<Component> lore) {
        if (itemMeta != null) {
            itemMeta.lore(new ArrayList<>(lore));
        }
        return this;
    }

    /**
     * Adds lore lines to the item.
     *
     * @param lore The lore lines to add
     * @return This builder
     */
    public ItemBuilder addLore(@NotNull Component... lore) {
        if (itemMeta != null) {
            List<Component> currentLore = itemMeta.lore();
            if (currentLore == null) {
                currentLore = new ArrayList<>();
            }
            currentLore.addAll(Arrays.asList(lore));
            itemMeta.lore(currentLore);
        }
        return this;
    }

    /**
     * Sets the item amount.
     *
     * @param amount The amount
     * @return This builder
     */
    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    /**
     * Adds an enchantment.
     *
     * @param enchantment The enchantment
     * @param level The level
     * @return This builder
     */
    public ItemBuilder enchant(@NotNull Enchantment enchantment, int level) {
        if (itemMeta != null) {
            itemMeta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Makes the item unbreakable.
     *
     * @return This builder
     */
    public ItemBuilder unbreakable() {
        if (itemMeta != null) {
            itemMeta.setUnbreakable(true);
        }
        return this;
    }

    /**
     * Adds item flags.
     *
     * @param flags The flags to add
     * @return This builder
     */
    public ItemBuilder flags(@NotNull ItemFlag... flags) {
        if (itemMeta != null) {
            itemMeta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Makes the item glow (adds enchantment and hides it).
     *
     * @return This builder
     */
    public ItemBuilder glow() {
        if (itemMeta != null) {
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Sets the custom model data.
     *
     * @param data The custom model data
     * @return This builder
     */
    public ItemBuilder customModelData(int data) {
        if (itemMeta != null) {
            itemMeta.setCustomModelData(data);
        }
        return this;
    }

    /**
     * Builds the ItemStack.
     *
     * @return The finished ItemStack
     */
    @NotNull
    public ItemStack build() {
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    /**
     * Creates a simple item with just a material and name.
     *
     * @param material The material
     * @param name The display name
     * @return The ItemStack
     */
    @NotNull
    public static ItemStack simple(@NotNull Material material, @NotNull Component name) {
        return new ItemBuilder(material).name(name).build();
    }
}