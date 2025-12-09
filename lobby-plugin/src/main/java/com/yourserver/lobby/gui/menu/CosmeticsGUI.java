package com.yourserver.lobby.gui.menu;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cosmetics GUI for selecting particle trails and other cosmetics.
 */
public class CosmeticsGUI extends GUI {

    private final LobbyPlugin plugin;
    private final LobbyConfig config;

    public CosmeticsGUI(LobbyPlugin plugin, LobbyConfig config) {
        super(Component.text("Cosmetics", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD), 4);
        this.plugin = plugin;
        this.config = config;

        setupItems();
    }

    private void setupItems() {
        // Title (slot 4)
        ItemStack title = new ItemBuilder(Material.NETHER_STAR)
                .name(Component.text("Particle Trails", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .lore(
                        Component.empty(),
                        Component.text("Choose a particle trail!", NamedTextColor.GRAY)
                )
                .flags(ItemFlag.HIDE_ATTRIBUTES)
                .build();
        setItem(4, title);

        // Add all available trails
        Map<String, LobbyConfig.TrailConfig> trails = config.getCosmeticsConfig().getTrails();
        int slot = 10;

        for (Map.Entry<String, LobbyConfig.TrailConfig> entry : trails.entrySet()) {
            String trailId = entry.getKey();
            LobbyConfig.TrailConfig trail = entry.getValue();

            // Get material based on trail type
            Material material = getTrailMaterial(trailId);

            // Create lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Particle: " + trail.getParticle().name(), NamedTextColor.GRAY));
            lore.add(Component.empty());

            // Check if player has permission
            boolean hasPermission = true; // Will be checked in click handler
            boolean isVip = trail.isVip();

            if (isVip) {
                lore.add(Component.text("⭐ VIP Cosmetic", NamedTextColor.GOLD, TextDecoration.BOLD));
                lore.add(Component.empty());
            }

            lore.add(Component.text("Click to equip!", NamedTextColor.GREEN));

            ItemStack item = new ItemBuilder(material)
                    .name(plugin.getMiniMessage().deserialize(trail.getName()))
                    .lore(lore)
                    .flags(ItemFlag.HIDE_ATTRIBUTES)
                    .build();

            setItem(slot, item, player -> {
                // Check permission
                if (!player.hasPermission(trail.getPermission())) {
                    player.closeInventory();
                    player.sendMessage(Component.text(
                            config.getMessagesConfig().getCosmeticLocked(),
                            NamedTextColor.RED
                    ));
                    return;
                }

                // Equip trail
                plugin.getCosmeticsManager().setTrail(player, trailId);
                player.closeInventory();

                String message = config.getMessagesConfig().getCosmeticEquipped()
                        .replace("{cosmetic}", trail.getName());
                player.sendMessage(plugin.getMiniMessage().deserialize(message));
            });

            slot++;
            if (slot == 17) slot = 19; // Skip to next row
            if (slot == 26) slot = 28;
        }

        // Remove trail button (slot 31)
        ItemStack removeTrail = new ItemBuilder(Material.BARRIER)
                .name(Component.text("Remove Trail", NamedTextColor.RED))
                .lore(
                        Component.empty(),
                        Component.text("Click to remove your trail", NamedTextColor.GRAY)
                )
                .build();

        setItem(31, removeTrail, player -> {
            plugin.getCosmeticsManager().removeTrail(player);
            player.closeInventory();
            player.sendMessage(Component.text(
                    config.getMessagesConfig().getCosmeticRemoved(),
                    NamedTextColor.GRAY
            ));
        });

        // Fill empty slots
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
        fillEmpty(filler);
    }

    private Material getTrailMaterial(String trailId) {
        return switch (trailId.toLowerCase()) {
            case "flame" -> Material.BLAZE_POWDER;
            case "heart" -> Material.PINK_DYE;
            case "enchant" -> Material.EXPERIENCE_BOTTLE;
            case "portal" -> Material.ENDER_PEARL;
            case "note" -> Material.NOTE_BLOCK;
            case "redstone" -> Material.REDSTONE;
            default -> Material.NETHER_STAR;
        };
    }
}