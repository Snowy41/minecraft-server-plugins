package com.yourserver.lobby.listener;

import com.yourserver.lobby.config.LobbyConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Handles lobby protection (prevent breaking blocks, damage, etc.)
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

        if (config.getProtectionConfig().isPlayerDamage()) {
            if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                    cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK ||
                    cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                event.setCancelled(true);
                return;
            }
        }

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
}