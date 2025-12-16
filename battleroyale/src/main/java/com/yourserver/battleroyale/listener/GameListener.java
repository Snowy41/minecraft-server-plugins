package com.yourserver.battleroyale.listener;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles game-related events (combat, death, movement).
 */
public class GameListener implements Listener {

    private final BattleRoyalePlugin plugin;
    private final GameManager gameManager;

    public GameListener(BattleRoyalePlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    /**
     * Prevent damage in pre-game lobby.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Game game = gameManager.getPlayerGame(victim);
        if (game == null) {
            return;
        }

        // Prevent damage in WAITING and STARTING states
        if (game.getState() == GameState.WAITING || game.getState() == GameState.STARTING) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle player death in game.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            return;
        }

        // Don't drop items in battle royale
        event.getDrops().clear();
        event.setKeepInventory(false);

        // Mark player as eliminated
        game.eliminatePlayer(player.getUniqueId());

        // TODO: Track killer for statistics
        // TODO: Convert to spectator
        // TODO: Show death message
    }

    /**
     * Prevent players from leaving pre-game lobby early.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Game game = gameManager.getPlayerGame(player);
        if (game == null) {
            return;
        }

        // Only restrict movement during WAITING and STARTING
        if (game.getState() != GameState.WAITING && game.getState() != GameState.STARTING) {
            return;
        }

        // TODO: Check if player is trying to leave pre-game lobby bounds
        // If so, teleport them back
    }
}