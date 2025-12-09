package com.yourserver.lobby.gui.menu;

import com.yourserver.api.model.PlayerStats;
import com.yourserver.core.CorePlugin;
import com.yourserver.lobby.LobbyPlugin;
import com.yourserver.lobby.config.LobbyConfig;
import com.yourserver.lobby.gui.GUI;
import com.yourserver.lobby.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * Statistics GUI showing player stats.
 */
public class StatsGUI extends GUI {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final CorePlugin corePlugin;
    private final Player targetPlayer;

    public StatsGUI(LobbyPlugin plugin, LobbyConfig config, CorePlugin corePlugin, Player targetPlayer) {
        super(Component.text("Statistics - " + targetPlayer.getName(), NamedTextColor.AQUA, TextDecoration.BOLD), 3);
        this.plugin = plugin;
        this.config = config;
        this.corePlugin = corePlugin;
        this.targetPlayer = targetPlayer;

        setupItems();
    }

    private void setupItems() {
        // Load stats asynchronously and update GUI
        corePlugin.getPlayerDataManager().loadPlayerStats(targetPlayer.getUniqueId())
                .thenAccept(this::displayStats);

        // Loading indicator
        ItemStack loading = new ItemBuilder(Material.HOPPER)
                .name(Component.text("Loading...", NamedTextColor.YELLOW))
                .build();
        setItem(13, loading);

        // Fill with glass pane
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        fillEmpty(filler);
    }

    private void displayStats(PlayerStats stats) {
        // Games played (slot 11)
        ItemStack gamesPlayed = new ItemBuilder(Material.GRASS_BLOCK)
                .name(Component.text("Games Played", NamedTextColor.GREEN, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Total: " + stats.getGamesPlayed(), NamedTextColor.WHITE),
                        Component.text("Wins: " + stats.getGamesWon(), NamedTextColor.WHITE),
                        Component.text("Win Rate: " + String.format("%.1f%%", stats.getWinRate()), NamedTextColor.GOLD)
                )
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        setItem(11, gamesPlayed);

        // Combat stats (slot 13)
        ItemStack combat = new ItemBuilder(Material.DIAMOND_SWORD)
                .name(Component.text("Combat Stats", NamedTextColor.RED, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Kills: " + stats.getKills(), NamedTextColor.WHITE),
                        Component.text("Deaths: " + stats.getDeaths(), NamedTextColor.WHITE),
                        Component.text("K/D Ratio: " + String.format("%.2f", stats.getKDRatio()), NamedTextColor.GOLD)
                )
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        setItem(13, combat);

        // Damage stats (slot 15)
        ItemStack damage = new ItemBuilder(Material.BOW)
                .name(Component.text("Damage Stats", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Dealt: " + String.format("%.0f", stats.getDamageDealt()), NamedTextColor.WHITE),
                        Component.text("Taken: " + String.format("%.0f", stats.getDamageTaken()), NamedTextColor.WHITE)
                )
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        setItem(15, damage);

        // Close button (slot 22)
        ItemStack close = new ItemBuilder(Material.BARRIER)
                .name(Component.text("Close", NamedTextColor.RED))
                .build();
        setItem(22, close, Player::closeInventory);
    }
}