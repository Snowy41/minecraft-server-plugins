package com.yourserver.lobby.gui.menu;

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
import org.bukkit.inventory.ItemStack;

/**
 * Game selector GUI for choosing which game to play.
 */
public class GameSelectorGUI extends GUI {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;
    private final CorePlugin corePlugin;

    public GameSelectorGUI(LobbyPlugin plugin, LobbyConfig config, CorePlugin corePlugin) {
        super(Component.text("Game Selector", NamedTextColor.GOLD, TextDecoration.BOLD), 3);
        this.plugin = plugin;
        this.config = config;
        this.corePlugin = corePlugin;

        setupItems();
    }

    private void setupItems() {
        // Battle Royale game (slot 11)
        ItemStack battleRoyale = new ItemBuilder(Material.DIAMOND_SWORD)
                .name(Component.text("Battle Royale", NamedTextColor.RED, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Fight to be the last one standing!", NamedTextColor.GRAY),
                        Component.text("100 players, custom items, shrinking zone", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Status: ", NamedTextColor.GRAY)
                                .append(Component.text("Coming Soon", NamedTextColor.YELLOW)),
                        Component.empty(),
                        Component.text("Click to join queue!", NamedTextColor.GREEN)
                )
                .build();

        setItem(11, battleRoyale, player -> {
            player.closeInventory();
            player.sendMessage(Component.text("Battle Royale is coming soon!", NamedTextColor.YELLOW));
            // TODO: Join battle royale queue via Redis
        });

        // Cosmetics (slot 13)
        ItemStack cosmetics = new ItemBuilder(Material.NETHER_STAR)
                .name(Component.text("Cosmetics", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Customize your appearance!", NamedTextColor.GRAY),
                        Component.text("Particle trails, hub items, and more", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to open!", NamedTextColor.GREEN)
                )
                .build();

        setItem(13, cosmetics, player -> {
            player.closeInventory();
            plugin.getGuiManager().openCosmetics(player);
        });

        // Stats (slot 15)
        ItemStack stats = new ItemBuilder(Material.BOOK)
                .name(Component.text("Statistics", NamedTextColor.AQUA, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("View your game statistics", NamedTextColor.GRAY),
                        Component.text("Wins, kills, K/D ratio, and more", NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Click to view!", NamedTextColor.GREEN)
                )
                .build();

        setItem(15, stats, player -> {
            player.closeInventory();
            plugin.getGuiManager().openStats(player);
        });

        // Close button (slot 22)
        ItemStack close = new ItemBuilder(Material.BARRIER)
                .name(Component.text("Close", NamedTextColor.RED))
                .build();

        setItem(22, close, Player::closeInventory);

        // Fill empty slots with glass pane
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        fillEmpty(filler);
    }
}