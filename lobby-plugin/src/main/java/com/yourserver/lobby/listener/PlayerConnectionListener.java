package com.yourserver.lobby.listener;

import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.cosmetics.CosmeticsManager;
import com.yourserver.lobby.scoreboard.ScoreboardManager;
import com.yourserver.lobby.spawn.SpawnManager;
import com.yourserver.lobby.tablist.TabListManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join/quit events for lobby functionality.
 * FIXED: Removed duplicate giveJoinItems() method - now uses plugin's method
 */
public class PlayerConnectionListener implements Listener {

    private final LobbyPlugin plugin;
    private final SpawnManager spawnManager;
    private final ScoreboardManager scoreboardManager;
    private final TabListManager tabListManager;
    private final CosmeticsManager cosmeticsManager;

    public PlayerConnectionListener(
            LobbyPlugin plugin,
            SpawnManager spawnManager,
            ScoreboardManager scoreboardManager,
            TabListManager tabListManager,
            CosmeticsManager cosmeticsManager
    ) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
        this.scoreboardManager = scoreboardManager;
        this.tabListManager = tabListManager;
        this.cosmeticsManager = cosmeticsManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Teleport to spawn
        if (spawnManager.isSpawnSet()) {
            spawnManager.teleportToSpawn(player);
        }

        // Set gamemode to adventure
        player.setGameMode(GameMode.ADVENTURE);

        // Reset player state
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0);

        // Give join items (only if player has items enabled)
        // Uses the plugin's centralized method
        if (plugin.getItemToggleManager().hasItemsEnabled(player)) {
            plugin.giveJoinItems(player);
        }

        // Set up scoreboard
        if (plugin.getLobbyConfig().getScoreboardConfig().isEnabled()) {
            scoreboardManager.createScoreboard(player);
        }

        // Set up tab list
        if (plugin.getLobbyConfig().getTabListConfig().isEnabled()) {
            tabListManager.updatePlayer(player);

            // SET INITIAL TAB LIST NAME WITH RANK ICON
            var corePlugin = plugin.getCorePlugin();
            if (corePlugin != null) {
                var rankManager = corePlugin.getRankDisplayManager();
                if (rankManager != null) {
                    String formattedName = rankManager.getFormattedPlayerName(player);
                    player.setPlayerListName(formattedName);
                }
            }
        }

        // Custom join message (optional)
        event.joinMessage(null); // Remove default join message
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove scoreboard
        scoreboardManager.removeScoreboard(player);

        // Remove cosmetics
        cosmeticsManager.removeTrail(player);

        // Clean up item toggle state
        plugin.getItemToggleManager().cleanup(player);

        // Custom quit message (optional)
        event.quitMessage(null); // Remove default quit message
    }
}