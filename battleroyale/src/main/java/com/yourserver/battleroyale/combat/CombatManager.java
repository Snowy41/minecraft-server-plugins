package com.yourserver.battleroyale.combat;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages combat tracking for kills, assists, and damage.
 *
 * Features:
 * - Tracks last damage dealer for kill attribution
 * - Tracks assists (players who damaged within 10 seconds)
 * - Tracks total damage dealt and taken
 * - Combat log for preventing logout during combat
 */
public class CombatManager {

    // Combat timeout (10 seconds)
    private static final long COMBAT_TIMEOUT = 10000L;

    // Track last damage dealer (victim -> attacker)
    private final Map<UUID, DamageRecord> lastDamage;

    // Track assists (victim -> list of assisters)
    private final Map<UUID, Map<UUID, Long>> assists;

    // Track total damage (player -> damage)
    private final Map<UUID, Double> damageDealt;
    private final Map<UUID, Double> damageTaken;

    public CombatManager() {
        this.lastDamage = new ConcurrentHashMap<>();
        this.assists = new ConcurrentHashMap<>();
        this.damageDealt = new ConcurrentHashMap<>();
        this.damageTaken = new ConcurrentHashMap<>();
    }

    /**
     * Records damage between players.
     *
     * @param victim The player who took damage
     * @param attacker The player who dealt damage
     * @param damage Amount of damage
     */
    public void recordDamage(@NotNull UUID victim, @NotNull UUID attacker, double damage) {
        long now = System.currentTimeMillis();

        // Update last damage dealer
        lastDamage.put(victim, new DamageRecord(attacker, now, damage));

        // Track for assists
        assists.computeIfAbsent(victim, k -> new ConcurrentHashMap<>())
                .put(attacker, now);

        // Update damage statistics
        damageDealt.merge(attacker, damage, Double::sum);
        damageTaken.merge(victim, damage, Double::sum);
    }

    /**
     * Gets the killer of a player (last damage dealer within timeout).
     *
     * @param victim The player who died
     * @return The killer's UUID, or null if no valid killer
     */
    @Nullable
    public UUID getKiller(@NotNull UUID victim) {
        DamageRecord record = lastDamage.remove(victim);

        if (record == null) {
            return null;
        }

        long timeSince = System.currentTimeMillis() - record.timestamp;

        if (timeSince <= COMBAT_TIMEOUT) {
            return record.attacker;
        }

        return null;
    }

    /**
     * Gets all players who assisted in killing a player.
     * Assist = damaged within 10 seconds, but didn't get final blow.
     *
     * @param victim The player who died
     * @param killer The player who got the kill
     * @return Array of assister UUIDs
     */
    @NotNull
    public UUID[] getAssisters(@NotNull UUID victim, @Nullable UUID killer) {
        Map<UUID, Long> attackers = assists.remove(victim);

        if (attackers == null || attackers.isEmpty()) {
            return new UUID[0];
        }

        long now = System.currentTimeMillis();

        return attackers.entrySet().stream()
                .filter(entry -> {
                    // Not the killer
                    if (killer != null && entry.getKey().equals(killer)) {
                        return false;
                    }

                    // Damaged within timeout
                    long timeSince = now - entry.getValue();
                    return timeSince <= COMBAT_TIMEOUT;
                })
                .map(Map.Entry::getKey)
                .toArray(UUID[]::new);
    }

    /**
     * Checks if a player is in combat.
     *
     * @param player The player to check
     * @return true if in combat, false otherwise
     */
    public boolean isInCombat(@NotNull UUID player) {
        // Check if player recently attacked someone
        boolean recentlyAttacked = assists.values().stream()
                .anyMatch(attackers -> {
                    Long lastAttack = attackers.get(player);
                    if (lastAttack == null) {
                        return false;
                    }

                    long timeSince = System.currentTimeMillis() - lastAttack;
                    return timeSince <= COMBAT_TIMEOUT;
                });

        // Check if player was recently attacked
        DamageRecord record = lastDamage.get(player);
        if (record != null) {
            long timeSince = System.currentTimeMillis() - record.timestamp;
            if (timeSince <= COMBAT_TIMEOUT) {
                return true;
            }
        }

        return recentlyAttacked;
    }

    /**
     * Gets total damage dealt by a player.
     */
    public double getDamageDealt(@NotNull UUID player) {
        return damageDealt.getOrDefault(player, 0.0);
    }

    /**
     * Gets total damage taken by a player.
     */
    public double getDamageTaken(@NotNull UUID player) {
        return damageTaken.getOrDefault(player, 0.0);
    }

    /**
     * Clears combat data for a player.
     */
    public void clearPlayer(@NotNull UUID player) {
        lastDamage.remove(player);
        assists.remove(player);
        damageDealt.remove(player);
        damageTaken.remove(player);

        // Remove from other players' assist lists
        assists.values().forEach(map -> map.remove(player));
    }

    /**
     * Clears all combat data.
     */
    public void clearAll() {
        lastDamage.clear();
        assists.clear();
        damageDealt.clear();
        damageTaken.clear();
    }

    // ===== DAMAGE RECORD =====

    private static class DamageRecord {
        final UUID attacker;
        final long timestamp;
        final double damage;

        DamageRecord(UUID attacker, long timestamp, double damage) {
            this.attacker = attacker;
            this.timestamp = timestamp;
            this.damage = damage;
        }
    }
}