package com.yourserver.npc.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Represents equipment worn/held by an NPC.
 */
public class NPCEquipment {

    private ItemStack mainHand;
    private ItemStack offHand;
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;

    public NPCEquipment() {
        // Default: no equipment
    }

    // Builder pattern for easy creation
    public static class Builder {
        private final NPCEquipment equipment = new NPCEquipment();

        public Builder mainHand(@Nullable ItemStack item) {
            equipment.mainHand = item;
            return this;
        }

        public Builder offHand(@Nullable ItemStack item) {
            equipment.offHand = item;
            return this;
        }

        public Builder helmet(@Nullable ItemStack item) {
            equipment.helmet = item;
            return this;
        }

        public Builder chestplate(@Nullable ItemStack item) {
            equipment.chestplate = item;
            return this;
        }

        public Builder leggings(@Nullable ItemStack item) {
            equipment.leggings = item;
            return this;
        }

        public Builder boots(@Nullable ItemStack item) {
            equipment.boots = item;
            return this;
        }

        public NPCEquipment build() {
            return equipment;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    @Nullable public ItemStack getMainHand() { return mainHand; }
    @Nullable public ItemStack getOffHand() { return offHand; }
    @Nullable public ItemStack getHelmet() { return helmet; }
    @Nullable public ItemStack getChestplate() { return chestplate; }
    @Nullable public ItemStack getLeggings() { return leggings; }
    @Nullable public ItemStack getBoots() { return boots; }

    // Setters
    public void setMainHand(@Nullable ItemStack item) { this.mainHand = item; }
    public void setOffHand(@Nullable ItemStack item) { this.offHand = item; }
    public void setHelmet(@Nullable ItemStack item) { this.helmet = item; }
    public void setChestplate(@Nullable ItemStack item) { this.chestplate = item; }
    public void setLeggings(@Nullable ItemStack item) { this.leggings = item; }
    public void setBoots(@Nullable ItemStack item) { this.boots = item; }

    /**
     * Checks if any equipment is set.
     */
    public boolean hasEquipment() {
        return mainHand != null || offHand != null || helmet != null ||
                chestplate != null || leggings != null || boots != null;
    }

    /**
     * Clears all equipment.
     */
    public void clear() {
        mainHand = null;
        offHand = null;
        helmet = null;
        chestplate = null;
        leggings = null;
        boots = null;
    }

    /**
     * Creates a copy of this equipment.
     */
    public NPCEquipment copy() {
        return NPCEquipment.builder()
                .mainHand(mainHand != null ? mainHand.clone() : null)
                .offHand(offHand != null ? offHand.clone() : null)
                .helmet(helmet != null ? helmet.clone() : null)
                .chestplate(chestplate != null ? chestplate.clone() : null)
                .leggings(leggings != null ? leggings.clone() : null)
                .boots(boots != null ? boots.clone() : null)
                .build();
    }
}