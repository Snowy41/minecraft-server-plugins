package com.yourserver.lobby.listener;

import com.yourserver.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Enhanced lobby protection listener for 1.21.8
 *
 * - Death prevention (instant respawn at spawn)
 * - Mob spawning prevention (friendly + hostile)
 * - Comprehensive interaction blocking
 * - Only allows lobby items and NPC interactions
 */
public class LobbyProtectionListener implements Listener {

    private final LobbyConfig config;

    public LobbyProtectionListener(LobbyConfig config) {
        this.config = config;
    }

    /**
     * Checks if a player should bypass protection (OP players).
     */
    private boolean shouldBypass(Player player) {
        return config.getProtectionConfig().isOpBypass() && player.isOp();
    }

    /**
     * Checks if a location is in the spawn region.
     */
    private boolean isInSpawnRegion(Location location) {
        var region = config.getProtectionConfig().getSpawnRegion();
        if (region == null) {
            return true;
        }

        return region.contains(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Checks if an item is a lobby item (allowed for interaction).
     */
    private boolean isLobbyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();

        // FIXED: Also check for PLAYER_HEAD (friends menu)
        return type == Material.COMPASS ||
                type == Material.NETHER_STAR ||
                type == Material.PLAYER_HEAD;
    }

    // ===== EXISTING METHODS (UNCHANGED) =====

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (config.getProtectionConfig().isBlockBreak() && isInSpawnRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (config.getProtectionConfig().isBlockPlace() && isInSpawnRegion(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (config.getProtectionConfig().isItemDrop() && isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (config.getProtectionConfig().isItemPickup() && isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (shouldBypass(victim)) {
            return;
        }

        if (!isInSpawnRegion(victim.getLocation())) {
            return;
        }

        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // FIXED: Inverted the logic - cancel when PvP is DISABLED (true in config)
        if (attacker != null && config.getProtectionConfig().isPvp()) {
            event.setCancelled(true);
            return; // FIXED: Added return to prevent further processing
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (config.getProtectionConfig().isFallDamage() && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        if (config.getProtectionConfig().isFireDamage() &&
                (cause == EntityDamageEvent.DamageCause.FIRE ||
                        cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                        cause == EntityDamageEvent.DamageCause.LAVA)) {
            event.setCancelled(true);
            return;
        }

        if (config.getProtectionConfig().isDrowningDamage() && cause == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            return;
        }

        if (config.getProtectionConfig().isVoidDamage() && cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            return;
        }

        if (config.getProtectionConfig().isPlayerDamage()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (config.getProtectionConfig().isHunger() && isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (config.getProtectionConfig().isWeatherClear() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTimeSkip(org.bukkit.event.world.TimeSkipEvent event) {
        if (!config.getProtectionConfig().isAlwaysDay()) {
            return;
        }

        if (event.getSkipReason() == org.bukkit.event.world.TimeSkipEvent.SkipReason.COMMAND) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (config.getProtectionConfig().isVoidTeleport()) {
            if (player.getLocation().getY() < config.getProtectionConfig().getVoidYLevel()) {
                player.getServer().getScheduler().runTask(
                        player.getServer().getPluginManager().getPlugin("LobbyPlugin"),
                        () -> {
                            Location spawn = player.getWorld().getSpawnLocation();
                            player.teleport(spawn);
                            player.sendMessage(config.getMessagesConfig().getVoidTeleport());
                        }
                );
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (entity.hasMetadata("NPC") || entity.hasMetadata("CustomNPC")) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        Entity entity = event.getRightClicked();

        if (entity.hasMetadata("NPC") || entity.hasMetadata("CustomNPC")) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player attacker = null;

        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || shouldBypass(attacker)) {
            return;
        }

        if (!isInSpawnRegion(attacker.getLocation())) {
            return;
        }

        Entity victim = event.getEntity();

        if (victim.hasMetadata("NPC")) {
            return;
        }

        if (!(victim instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ===== NEW METHODS FOR 1.21.8 =====

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!config.getProtectionConfig().isPreventDeath()) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);

        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("LobbyPlugin"),
                () -> {
                    player.spigot().respawn();
                    Location spawn = player.getWorld().getSpawnLocation();
                    player.teleport(spawn);
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                },
                1L
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!config.getProtectionConfig().isPreventMobSpawning()) {
            return;
        }

        if (!isInSpawnRegion(event.getLocation())) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER ||
                reason == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                reason == CreatureSpawnEvent.SpawnReason.COMMAND ||
                reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        Entity entity = event.getEntity();

        boolean isFriendly = entity instanceof Animals ||
                entity instanceof WaterMob ||
                entity instanceof Ambient ||
                entity instanceof AbstractVillager;

        boolean isHostile = entity instanceof Monster ||
                entity instanceof Slime ||
                entity instanceof Ghast ||
                entity instanceof Phantom;

        if (isFriendly && config.getProtectionConfig().isPreventFriendlyMobs()) {
            event.setCancelled(true);
            return;
        }

        if (isHostile && config.getProtectionConfig().isPreventHostileMobs()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!config.getProtectionConfig().isPreventAllInteractions()) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        ItemStack item = event.getItem();

        // Allow lobby items
        if (isLobbyItem(item)) {
            return;
        }

        // FIXED: Cancel ALL other interactions
        if (event.hasBlock() || event.hasItem()) {
            event.setCancelled(true);
            // FIXED: Also set useItemInHand and useInteractedBlock to DENY
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(event.getEntity().getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalEnter(PlayerPortalEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        if (event.getInventory().getHolder() == null) {
            return;
        }

        if (event.getClickedInventory() != null &&
                event.getClickedInventory().getHolder() != null &&
                !(event.getClickedInventory().getHolder() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (!isInSpawnRegion(player.getLocation())) {
            return;
        }

        if (event.getInventory().getHolder() != null &&
                !(event.getInventory().getHolder() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (shouldBypass(player)) {
            return;
        }

        if (!config.getProtectionConfig().isPreventAllInteractions()) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }

        if (shouldBypass(player)) {
            return;
        }

        if (isInSpawnRegion(player.getLocation())) {
            event.setCancelled(true);
        }
    }
}