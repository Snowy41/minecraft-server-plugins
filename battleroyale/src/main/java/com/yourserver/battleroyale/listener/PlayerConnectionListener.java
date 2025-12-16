package com.yourserver.battleroyale.listener;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player connection events.
 */
public class PlayerConnectionListener implements Listener {

    private final BattleRoyalePlugin plugin;
    private final GameManager gameManager;

    public PlayerConnectionListener(BattleRoyalePlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Find or create a joinable game
        Game game = gameManager.findJoinableGame();

        if (game == null) {
            // Create new game if none available
            game = gameManager.createGame();
            plugin.getLogger().info("Created new game for player " + player.getName());
        }

        // Add player to game
        if (gameManager.joinGame(player, game)) {
            plugin.getLogger().info("Player " + player.getName() + " joined game " + game.getId());
        } else {
            plugin.getLogger().warning("Failed to add player " + player.getName() + " to game!");
            // TODO: Kick player or send to lobby
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove from game if in one
        if (gameManager.isInGame(player)) {
            gameManager.leaveGame(player);
            plugin.getLogger().info("Player " + player.getName() + " left game");
        }
    }
}