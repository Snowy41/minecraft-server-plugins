package com.yourserver.battleroyale.listener;

import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Minimal protection - ONLY for pre-game lobby.
 *
 * During actual gameplay (ACTIVE/DEATHMATCH):
 * - Players CAN break/place blocks (for strategy!)
 * - Players CAN take hunger damage
 * - Players CAN PvP
 * - It's full survival battle royale!
 *
 * Only protects WAITING/STARTING phases (pre-game lobby above map).
 */
public class MinimalProtectionListener implements Listener {

    private final GameManager gameManager;

    public MinimalProtectionListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    /**
     * Prevent block breaking ONLY in pre-game lobby (WAITING/STARTING).
     * During game: ALLOW everything!
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);

        if (game == null) {
            return;
        }

        // ONLY prevent in lobby (WAITING/STARTING)
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Wait for game to start!", NamedTextColor.YELLOW));
        }

        // During ACTIVE/DEATHMATCH: Allow! (survival gameplay)
    }

    /**
     * Prevent block placing ONLY in pre-game lobby.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);

        if (game == null) {
            return;
        }

        // ONLY prevent in lobby
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Wait for game to start!", NamedTextColor.YELLOW));
        }
    }

    /**
     * Prevent PvP ONLY in pre-game lobby.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        Game game = gameManager.getPlayerGame(victim);
        if (game == null) {
            return;
        }

        // ONLY prevent in lobby
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
            attacker.sendActionBar(Component.text("Wait for game to start!", NamedTextColor.YELLOW));
        }
    }

    /**
     * Disable hunger ONLY in pre-game lobby.
     * During game: normal hunger (survival!)
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            return;
        }

        GameState state = game.getState();

        // Disable hunger ONLY in lobby
        if (state == GameState.WAITING || state == GameState.STARTING) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }

        // During ACTIVE: Allow hunger! (survival gameplay)
        // During DEATHMATCH: Also disable for fairness
        if (state == GameState.DEATHMATCH) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    /**
     * Prevent item dropping in lobby only.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Game game = gameManager.getPlayerGame(player);

        if (game == null) {
            return;
        }

        // Prevent item dropping ONLY in lobby
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Wait for game to start!", NamedTextColor.YELLOW));
        }
    }
}