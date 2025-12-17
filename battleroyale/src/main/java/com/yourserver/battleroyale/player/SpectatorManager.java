package com.yourserver.battleroyale.player;

import com.yourserver.battleroyale.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages spectator functionality for eliminated players.
 *
 * Features:
 * - Converts dead players to spectators
 * - Allows spectating remaining players
 * - Prevents interference with game
 * - Provides UI for spectator controls
 */
public class SpectatorManager {

    private final Game game;
    private final Map<UUID, SpectatorData> spectators;

    public SpectatorManager(@NotNull Game game) {
        this.game = game;
        this.spectators = new HashMap<>();
    }

    /**
     * Converts a player to spectator mode after death.
     *
     * @param player The player to make spectator
     */
    public void makeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();

        // Already a spectator?
        if (spectators.containsKey(uuid)) {
            return;
        }

        // Set spectator mode
        player.setGameMode(GameMode.SPECTATOR);

        // Clear effects
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );

        // Add night vision for better viewing
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));

        // Set flight
        player.setAllowFlight(true);
        player.setFlying(true);

        // Find a good spectate target
        Player target = findSpectateTarget();
        if (target != null) {
            player.teleport(target.getLocation());
            player.setSpectatorTarget(target);
        }

        // Create spectator data
        SpectatorData data = new SpectatorData(uuid, player.getName());
        spectators.put(uuid, data);

        // Send spectator UI
        sendSpectatorUI(player);
    }

    /**
     * Finds a suitable target for spectating.
     *
     * @return A living player, or null if none available
     */
    private Player findSpectateTarget() {
        return game.getAlivePlayers().stream()
                .map(uuid -> game.getOnlinePlayers().stream()
                        .filter(p -> p.getUniqueId().equals(uuid))
                        .findFirst()
                        .orElse(null))
                .filter(p -> p != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Sends spectator UI/instructions to player.
     */
    private void sendSpectatorUI(@NotNull Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.text("  👻 ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text("SPECTATOR MODE", NamedTextColor.GRAY, TextDecoration.BOLD)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  You can now spectate the game", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Controls:", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("    Left Click", NamedTextColor.GRAY)
                .append(Component.text(" - Spectate player", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("    Number Keys", NamedTextColor.GRAY)
                .append(Component.text(" - Teleport shortcuts", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Remaining: ", NamedTextColor.GRAY)
                .append(Component.text(game.getAliveCount() + " players", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        player.sendMessage(Component.empty());
    }

    /**
     * Removes a player from spectator mode.
     *
     * @param player The player to remove
     */
    public void removeSpectator(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        spectators.remove(uuid);

        // Clear spectator effects
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.setSpectatorTarget(null);

        // Reset to survival (will be changed by game state)
        player.setGameMode(GameMode.SURVIVAL);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /**
     * Checks if a player is a spectator.
     */
    public boolean isSpectator(@NotNull UUID uuid) {
        return spectators.containsKey(uuid);
    }

    /**
     * Gets the number of spectators.
     */
    public int getSpectatorCount() {
        return spectators.size();
    }

    /**
     * Teleports a spectator to a specific player.
     */
    public void spectatePlayer(@NotNull Player spectator, @NotNull Player target) {
        if (!isSpectator(spectator.getUniqueId())) {
            return;
        }

        if (!game.isPlayerAlive(target.getUniqueId())) {
            spectator.sendActionBar(Component.text("That player is eliminated!", NamedTextColor.RED));
            return;
        }

        spectator.teleport(target.getLocation());
        spectator.setSpectatorTarget(target);

        spectator.sendActionBar(Component.text("Now spectating: ", NamedTextColor.GRAY)
                .append(Component.text(target.getName(), NamedTextColor.YELLOW)));
    }

    /**
     * Teleports all spectators to the deathmatch arena.
     */
    public void teleportSpectatorsToArena(@NotNull Location arenaCenter) {
        for (UUID uuid : spectators.keySet()) {
            Player spectator = org.bukkit.Bukkit.getPlayer(uuid);
            if (spectator != null && spectator.isOnline()) {
                Location spectateLocation = arenaCenter.clone().add(0, 50, 0);
                spectator.teleport(spectateLocation);

                spectator.sendMessage(Component.text("Deathmatch starting! ", NamedTextColor.YELLOW)
                        .append(Component.text("Watch from above", NamedTextColor.GRAY)));
            }
        }
    }

    /**
     * Clears all spectators.
     */
    public void clearAll() {
        // Remove spectator effects from all
        for (UUID uuid : spectators.keySet()) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                removeSpectator(player);
            }
        }

        spectators.clear();
    }

    // ===== SPECTATOR DATA =====

    private static class SpectatorData {
        private final UUID uuid;
        private final String name;
        private final long deathTime;

        SpectatorData(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
            this.deathTime = System.currentTimeMillis();
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
        public long getDeathTime() { return deathTime; }
    }
}