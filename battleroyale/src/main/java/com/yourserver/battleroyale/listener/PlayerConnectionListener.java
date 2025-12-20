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
 *
 * FIXED: Players are NO LONGER auto-joined to games on server join!
 * They must use /br join to join a game manually.
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

        // REMOVED AUTO-JOIN LOGIC
        // Players now spawn in lobby and must manually use /br join

        plugin.getLogger().info("Player " + player.getName() + " connected (not auto-joined to game)");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove from game if in one
        if (gameManager.isInGame(player)) {
            gameManager.leaveGame(player);
            plugin.getLogger().info("Player " + player.getName() + " left game on disconnect");
        }
    }
}