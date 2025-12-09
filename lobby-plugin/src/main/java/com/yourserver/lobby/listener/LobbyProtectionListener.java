package com.yourserver.lobby.listener;

import com.yourserver.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Handles lobby protection (prevent breaking blocks, damage, PvP, etc.)
 * Cancels almost ALL interactions except plugin-specific items.
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
            return true; // If no region defined, protect everywhere
        }

        return region.contains(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

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

    /**
     * Prevent PvP (player attacking player).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if victim is a player
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (shouldBypass(victim)) {
            return;
        }

        if (!isInSpawnRegion(victim.getLocation())) {
            return;
        }

        // Check if attacker is a player (direct or projectile)
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Cancel PvP if enabled in config
        if (attacker != null && config.getProtectionConfig().isPvp()) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent general damage to players.
     */
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

        // Check specific damage types
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

        // Block all other damage if player damage is enabled
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

    /**
     * Keep time always day if configured.
     * Uses TimeSkipEvent (Paper-specific event when time naturally changes).
     * IMPORTANT: We only CANCEL the event here. The TimeManager handles setting the time.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTimeSkip(org.bukkit.event.world.TimeSkipEvent event) {
        if (!config.getProtectionConfig().isAlwaysDay()) {
            return;
        }

        // Cancel natural time progression (sleeping, commands, etc.)
        // The TimeManager task will keep the time at the configured value
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check for void fall and teleport to spawn
        if (config.getProtectionConfig().isVoidTeleport()) {
            if (player.getLocation().getY() < config.getProtectionConfig().getVoidYLevel()) {
                // Teleport handled by SpawnManager through scheduled task to avoid recursion
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

    /**
     * Cancel ALL interactions with entities (armor stands, item frames, etc.)
     * EXCEPT for custom NPCs (Citizens plugin) and our CustomNPCs.
     */
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

        // Allow interaction with Citizens NPCs (if Citizens is installed)
        if (entity.hasMetadata("NPC")) {
            return; // Allow NPC interactions
        }

        // Allow interaction with our custom NPCs
        if (entity.hasMetadata("CustomNPC")) {
            return; // Allow custom NPC interactions
        }

        // Cancel all other entity interactions
        event.setCancelled(true);
    }

    /**
     * Cancel interactions at specific entities (armor stands in spawn, etc.)
     */
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

        // Allow interaction with Citizens NPCs
        if (entity.hasMetadata("NPC")) {
            return;
        }

        // Allow interaction with our custom NPCs
        if (entity.hasMetadata("CustomNPC")) {
            return;
        }

        // Cancel all other entity interactions
        event.setCancelled(true);
    }

    /**
     * Prevent attacking entities (armor stands, item frames, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        // Get the attacker
        Entity damager = event.getDamager();
        Player attacker = null;

        if (damager instanceof Player) {
            attacker = (Player) damager;
        } else if (damager instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null) {
            return;
        }

        if (shouldBypass(attacker)) {
            return;
        }

        if (!isInSpawnRegion(attacker.getLocation())) {
            return;
        }

        Entity victim = event.getEntity();

        // Allow damaging Citizens NPCs (if you want combat NPCs later)
        if (victim.hasMetadata("NPC")) {
            return;
        }

        // If victim is not a player, cancel the damage (armor stands, item frames, etc.)
        if (!(victim instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent bucket usage (water/lava placement).
     */
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

    /**
     * Prevent bucket filling.
     */
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

    /**
     * Cancel bed interactions.
     */
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
}