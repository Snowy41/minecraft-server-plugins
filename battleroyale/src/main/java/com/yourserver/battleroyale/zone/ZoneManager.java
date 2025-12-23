package com.yourserver.battleroyale.zone;

import com.yourserver.battleroyale.BattleRoyalePlugin;
import com.yourserver.battleroyale.game.Game;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Manages zone shrinking, damage, and visual indicators for a game.
 */
public class ZoneManager {

    private final BattleRoyalePlugin plugin;
    private final Game game;

    private Zone currentZone;
    private final List<ZonePhase> phases;
    private int currentPhaseIndex;

    private BukkitTask tickTask;
    private BukkitTask damageTask;
    private BukkitTask particleTask;

    private final Map<UUID, BossBar> playerBossBars;
    private long phaseStartTime;
    private boolean active;

    public ZoneManager(@NotNull BattleRoyalePlugin plugin, @NotNull Game game) {
        this.plugin = Objects.requireNonNull(plugin);
        this.game = Objects.requireNonNull(game);
        this.phases = createDefaultPhases();
        this.currentPhaseIndex = 0;
        this.playerBossBars = new HashMap<>();
        this.active = false;
    }

    /**
     * Starts the zone system with the first phase.
     */
    public void start(@NotNull Location center, double initialRadius) {
        if (active) {
            return;
        }

        ZonePhase firstPhase = phases.get(0);
        this.currentZone = new Zone(center.getWorld(), center, initialRadius, firstPhase);
        this.phaseStartTime = System.currentTimeMillis();
        this.active = true;

        startZoneTick();

        startDamageTick();

        startParticleEffects();

        plugin.getLogger().info("Zone system started for game " + game.getId());
    }

    /**
     * Stops the zone system and cleans up.
     */
    public void stop() {
        active = false;

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        if (damageTask != null) {
            damageTask.cancel();
            damageTask = null;
        }

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        for (BossBar bossBar : playerBossBars.values()) {
            for (Player player : game.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
        }
        playerBossBars.clear();

        plugin.getLogger().info("Zone system stopped for game " + game.getId());
    }

    /**
     * Advances to the next zone phase.
     */
    public void nextPhase() {
        if (currentPhaseIndex >= phases.size() - 1) {
            plugin.getLogger().warning("No more zone phases available!");
            return;
        }

        currentPhaseIndex++;
        ZonePhase nextPhase = phases.get(currentPhaseIndex);

        currentZone.startShrink(nextPhase.getTargetRadius(), nextPhase.getShrinkDuration());
        phaseStartTime = System.currentTimeMillis();

        notifyPhaseChange(nextPhase);

        plugin.getLogger().info("Zone advanced to phase " + (currentPhaseIndex + 1) +
                " for game " + game.getId());
    }

    /**
     * Starts the zone tick task that updates shrink progress.
     */
    private void startZoneTick() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || currentZone == null) {
                return;
            }

            currentZone.tick();

            updateBossBars();

            checkPhaseAdvancement();

        }, 1L, 1L); // Every tick
    }

    /**
     * Starts the damage tick that damages players outside the zone.
     */
    private void startDamageTick() {
        ZonePhase currentPhase = phases.get(currentPhaseIndex);
        int interval = currentPhase.getTickInterval();

        damageTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || currentZone == null) {
                return;
            }

            ZonePhase phase = phases.get(currentPhaseIndex);
            double damage = phase.getDamagePerTick();

            for (Player player : game.getOnlinePlayers()) {
                if (!game.isPlayerAlive(player.getUniqueId())) {
                    continue;
                }

                if (!currentZone.isInZone(player.getLocation())) {
                    player.damage(damage);

                    player.sendActionBar(Component.text("☠ Outside Zone! -" +
                            String.format("%.1f", damage) + " HP", NamedTextColor.RED));
                }
            }

        }, interval, interval);
    }

    /**
     * Starts particle effects to visualize the zone border.
     */
    private void startParticleEffects() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active || currentZone == null) {
                return;
            }

            showZoneBorder();

        }, 0L, 20L);
    }

    /**
     * Shows particle effects at the zone border.
     */
    private void showZoneBorder() {
        Location center = currentZone.getCenter();
        double radius = currentZone.getCurrentRadius();
        int points = Math.min(100, (int) (radius * 2));

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);

            Location particleLoc = new Location(center.getWorld(), x, center.getY() + 50, z);

            center.getWorld().spawnParticle(
                    Particle.DUST,
                    particleLoc,
                    1,
                    0, 0, 0,
                    0,
                    new Particle.DustOptions(org.bukkit.Color.RED, 1.5f)
            );
        }
    }

    /**
     * Updates boss bars for all players showing zone status.
     */
    private void updateBossBars() {
        ZonePhase currentPhase = phases.get(currentPhaseIndex);

        for (Player player : game.getOnlinePlayers()) {
            BossBar bossBar = playerBossBars.computeIfAbsent(player.getUniqueId(), uuid -> {
                BossBar bar = BossBar.bossBar(
                        Component.text("Zone"),
                        0.0f,
                        BossBar.Color.RED,
                        BossBar.Overlay.PROGRESS
                );
                player.showBossBar(bar);
                return bar;
            });

            if (currentZone.isShrinking()) {
                long remaining = currentZone.getRemainingSeconds();
                float progress = (float) currentZone.getShrinkProgress();

                bossBar.name(Component.text("⚠ Zone Shrinking - " + remaining + "s",
                        NamedTextColor.RED));
                bossBar.progress(1.0f - progress);
                bossBar.color(BossBar.Color.RED);
            } else {
                long elapsed = (System.currentTimeMillis() - phaseStartTime) / 1000;
                long remaining = currentPhase.getWaitDuration() - elapsed;

                if (remaining > 0) {
                    bossBar.name(Component.text("Next Zone in " + remaining + "s",
                            NamedTextColor.YELLOW));
                    bossBar.progress((float) remaining / currentPhase.getWaitDuration());
                    bossBar.color(BossBar.Color.YELLOW);
                }
            }
        }
    }

    /**
     * Checks if it's time to advance to the next phase.
     */
    private void checkPhaseAdvancement() {
        ZonePhase currentPhase = phases.get(currentPhaseIndex);

        if (currentZone.isShrinking()) {
            return;
        }

        long elapsed = (System.currentTimeMillis() - phaseStartTime) / 1000;

        if (elapsed >= currentPhase.getWaitDuration()) {
            nextPhase();
        }
    }

    /**
     * Notifies all players about a phase change.
     */
    private void notifyPhaseChange(@NotNull ZonePhase phase) {
        Component message = Component.text()
                .append(Component.text("⚠ ", NamedTextColor.YELLOW))
                .append(Component.text("Zone Phase " + (currentPhaseIndex + 1), NamedTextColor.GOLD))
                .append(Component.text(" - Shrinking to ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.0f", phase.getTargetRadius()) + " blocks",
                        NamedTextColor.WHITE))
                .build();

        for (Player player : game.getOnlinePlayers()) {
            player.sendMessage(message);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }

    /**
     * Creates default zone phases.
     */
    @NotNull
    private List<ZonePhase> createDefaultPhases() {
        List<ZonePhase> phases = new ArrayList<>();

        phases.add(new ZonePhase.Builder()
                .id(1)
                .waitDuration(120)  // 2 min wait
                .shrinkDuration(60) // 1 min shrink
                .targetRadius(750)
                .damagePerTick(1.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(2)
                .waitDuration(120)
                .shrinkDuration(60)
                .targetRadius(500)
                .damagePerTick(2.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(3)
                .waitDuration(90)
                .shrinkDuration(45)
                .targetRadius(300)
                .damagePerTick(3.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(4)
                .waitDuration(90)
                .shrinkDuration(45)
                .targetRadius(150)
                .damagePerTick(4.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(5)
                .waitDuration(60)
                .shrinkDuration(30)
                .targetRadius(75)
                .damagePerTick(5.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(6)
                .waitDuration(60)
                .shrinkDuration(30)
                .targetRadius(40)
                .damagePerTick(7.0)
                .tickInterval(20)
                .build());

        phases.add(new ZonePhase.Builder()
                .id(7)
                .waitDuration(45)
                .shrinkDuration(30)
                .targetRadius(20)
                .damagePerTick(10.0)
                .tickInterval(10)  // Damage twice as often
                .build());

        return phases;
    }

    // ===== GETTERS =====

    public Zone getCurrentZone() {
        return currentZone;
    }

    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    public boolean isActive() {
        return active;
    }

    public boolean shouldTriggerDeathmatch() {
        return currentPhaseIndex >= phases.size() - 1 &&
                currentZone != null &&
                currentZone.isShrinkComplete();
    }
}