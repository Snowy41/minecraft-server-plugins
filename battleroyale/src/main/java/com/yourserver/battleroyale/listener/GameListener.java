package com.yourserver.battleroyale.listener;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.combat.CombatManager;
import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
import com.yourserver.battleroyale.player.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

/**
 * Handles game-related events (combat, death, movement).
 * Integrated with CombatManager for kill/assist tracking.
 */
public class GameListener implements Listener {

    private final BattleRoyalePlugin plugin;
    private final GameManager gameManager;
    private final CombatManager combatManager;

    public GameListener(BattleRoyalePlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.combatManager = new CombatManager();
    }

    /**
     * Prevent damage in pre-game lobby.
     * Track damage during active game.
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
            return;
        }

        // Track damage if attacker is player
        if (event.getDamager() instanceof Player attacker) {
            double damage = event.getFinalDamage();
            combatManager.recordDamage(victim.getUniqueId(), attacker.getUniqueId(), damage);

            // Update player damage stats
            GamePlayer victimPlayer = game.getPlayer(victim.getUniqueId());
            GamePlayer attackerPlayer = game.getPlayer(attacker.getUniqueId());

            if (victimPlayer != null) {
                victimPlayer.addDamageTaken(damage);
            }
            if (attackerPlayer != null) {
                attackerPlayer.addDamageDealt(damage);
            }
        }
    }

    /**
     * Handle player death in game.
     * Tracks killer, assists, and updates statistics.
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

        // Get killer and assisters
        UUID killerUuid = combatManager.getKiller(player.getUniqueId());
        UUID[] assisters = combatManager.getAssisters(player.getUniqueId(), killerUuid);

        // Update kill stats
        if (killerUuid != null) {
            GamePlayer killerPlayer = game.getPlayer(killerUuid);
            if (killerPlayer != null) {
                killerPlayer.addKill();

                // Notify killer
                Player killer = plugin.getServer().getPlayer(killerUuid);
                if (killer != null) {
                    killer.sendMessage(Component.text("☠ ", NamedTextColor.RED)
                            .append(Component.text("You killed ", NamedTextColor.GRAY))
                            .append(Component.text(player.getName(), NamedTextColor.WHITE))
                            .append(Component.text("!", NamedTextColor.GRAY)));
                }
            }
        }

        // Update assist stats
        for (UUID assisterUuid : assisters) {
            GamePlayer assisterPlayer = game.getPlayer(assisterUuid);
            if (assisterPlayer != null) {
                assisterPlayer.addAssist();

                // Notify assister
                Player assister = plugin.getServer().getPlayer(assisterUuid);
                if (assister != null) {
                    assister.sendMessage(Component.text("+ ", NamedTextColor.YELLOW)
                            .append(Component.text("Assist on ", NamedTextColor.GRAY))
                            .append(Component.text(player.getName(), NamedTextColor.WHITE)));
                }
            }
        }

        // Custom death message
        Component deathMessage;
        if (killerUuid != null) {
            Player killer = plugin.getServer().getPlayer(killerUuid);
            String killerName = killer != null ? killer.getName() : "Unknown";

            deathMessage = Component.text(player.getName(), NamedTextColor.RED)
                    .append(Component.text(" was eliminated by ", NamedTextColor.GRAY))
                    .append(Component.text(killerName, NamedTextColor.YELLOW))
                    .append(Component.text(" ☠", NamedTextColor.RED));
        } else {
            deathMessage = Component.text(player.getName(), NamedTextColor.RED)
                    .append(Component.text(" died", NamedTextColor.GRAY))
                    .append(Component.text(" ☠", NamedTextColor.RED));
        }

        // Broadcast to game
        for (Player p : game.getOnlinePlayers()) {
            p.sendMessage(deathMessage);
        }

        event.deathMessage(null); // Suppress default message

        // Mark player as eliminated
        game.eliminatePlayer(player.getUniqueId());

        // Clear combat data
        combatManager.clearPlayer(player.getUniqueId());

        // TODO: Convert to spectator
        // TODO: Show death screen with stats
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

    /**
     * Gets the combat manager instance.
     */
    public CombatManager getCombatManager() {
        return combatManager;
    }
}