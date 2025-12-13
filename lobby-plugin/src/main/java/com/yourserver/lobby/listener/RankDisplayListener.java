package com.yourserver.lobby.listener;

import com.yourserver.core.CorePlugin;
import com.yourserver.core.rank.RankDisplayManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Handles rank display in nametags and tab list.
 * Updates when players join and when ranks change.
 */
public class RankDisplayListener implements Listener {

    private final CorePlugin corePlugin;
    private final MiniMessage miniMessage;

    public RankDisplayListener(CorePlugin corePlugin) {
        this.corePlugin = corePlugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Updates nametag and tab list when player joins.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update the joining player's display
        Bukkit.getScheduler().runTaskLater(
                corePlugin,
                () -> {
                    updatePlayerDisplay(player);

                    // Update nametags for ALL players so everyone can see each other
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (!online.equals(player)) {
                            // Refresh existing player's nametag
                            updatePlayerDisplay(online);
                        }
                    }
                },
                10L // Slightly longer delay to ensure LuckPerms is fully loaded
        );
    }

    /**
     * Updates a player's tab list name and nametag.
     */
    public void updatePlayerDisplay(Player player) {
        RankDisplayManager rankManager = corePlugin.getRankDisplayManager();
        if (rankManager == null) return;

        // Get formatted name with rank icon
        String formattedName = rankManager.getFormattedPlayerName(player);
        Component nameComponent = miniMessage.deserialize(formattedName);

        // Set tab list name
        player.playerListName(nameComponent);

        // Set nametag (using scoreboard teams)
        updateNametag(player, formattedName);
    }

    /**
     * Updates a player's nametag using scoreboard teams.
     * This shows above their head in-game.
     *
     * IMPORTANT: We use the MAIN scoreboard for nametags so they're visible to everyone,
     * even though players have their own custom lobby scoreboard.
     */
    private void updateNametag(Player player, String formattedName) {
        // ALWAYS use the main scoreboard for nametags
        // This ensures nametags are visible to all players
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Team name based on player name (so all players see the same team)
        String teamName = "rank_" + player.getName();

        // Limit team name to 16 characters (Minecraft limit)
        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        // Get or create team on MAIN scoreboard
        Team team = mainScoreboard.getTeam(teamName);
        if (team == null) {
            team = mainScoreboard.registerNewTeam(teamName);
        }

        // Add player to team
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }

        // Set prefix (the rank icon and color)
        // Extract everything except the player name
        String prefixString = formattedName.replace(player.getName(), "").trim();
        Component prefix = miniMessage.deserialize(prefixString + " ");
        team.prefix(prefix);

        // Optional: Set suffix (nothing for now)
        team.suffix(Component.empty());
    }

    /**
     * Call this method when a player's rank changes.
     * You can hook this into LuckPerms events if needed.
     */
    public void onRankChange(Player player) {
        updatePlayerDisplay(player);
    }

    /**
     * Clean up teams when player leaves.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove player from their nametag team on main scoreboard
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "rank_" + player.getName();

        if (teamName.length() > 16) {
            teamName = teamName.substring(0, 16);
        }

        Team team = mainScoreboard.getTeam(teamName);
        if (team != null) {
            team.removeEntry(player.getName());

            // If team is empty, unregister it
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }
}