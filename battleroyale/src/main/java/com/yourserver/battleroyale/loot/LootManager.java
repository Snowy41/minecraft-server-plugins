package com.yourserver.battleroyale.loot;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.arena.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages loot chest spawning and contents for a battle royale game.
 */
public class LootManager {

    private final BattleRoyalePlugin plugin;
    private final LootTable lootTable;
    private final Random random;

    private final List<Location> activeChests;

    public LootManager(@NotNull BattleRoyalePlugin plugin) {
        this.plugin = plugin;
        this.lootTable = new LootTable();
        this.random = new Random();
        this.activeChests = new ArrayList<>();
    }

    /**
     * Spawns loot chests across the arena based on spawn rate.
     *
     * @param arena The arena to spawn loot in
     */
    public void spawnLoot(@NotNull Arena arena) {
        double spawnRate = arena.getConfig().getLootChestSpawnRate();
        List<Location> chestLocations = arena.getLootChestLocations(spawnRate);

        plugin.getLogger().info("Spawning " + chestLocations.size() + " loot chests...");

        for (Location location : chestLocations) {
            spawnChest(location);
        }

        plugin.getLogger().info("Loot chests spawned: " + activeChests.size());
    }

    /**
     * Spawns a single loot chest at a location.
     */
    private void spawnChest(@NotNull Location location) {
        Block block = location.getBlock();
        block.setType(Material.CHEST);

        if (block.getState() instanceof Chest chest) {
            fillChest(chest);
            activeChests.add(location);
        }
    }

    /**
     * Fills a chest with random loot.
     */
    private void fillChest(@NotNull Chest chest) {
        Inventory inv = chest.getInventory();
        inv.clear();

        int itemCount = 3 + random.nextInt(6);
        List<ItemStack> loot = lootTable.generateMixedLoot(itemCount);

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots);

        for (int i = 0; i < loot.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), loot.get(i));
        }

        chest.update();
    }

    /**
     * Removes all spawned chests.
     */
    public void clearLoot() {
        for (Location location : activeChests) {
            Block block = location.getBlock();
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }

        activeChests.clear();
        plugin.getLogger().info("All loot chests cleared");
    }

    /**
     * Refills all chests (for mid-game loot refreshes).
     */
    public void refillChests() {
        int refilled = 0;

        for (Location location : activeChests) {
            Block block = location.getBlock();
            if (block.getState() instanceof Chest chest) {
                fillChest(chest);
                refilled++;
            }
        }

        plugin.getLogger().info("Refilled " + refilled + " loot chests");
    }

    /**
     * Gets the total number of active chests.
     */
    public int getActiveChestCount() {
        return activeChests.size();
    }

    /**
     * Gets the loot table for custom configurations.
     */
    @NotNull
    public LootTable getLootTable() {
        return lootTable;
    }
}