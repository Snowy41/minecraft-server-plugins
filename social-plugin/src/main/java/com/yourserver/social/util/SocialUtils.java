package com.yourserver.social.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility methods for the social plugin.
 * Provides common functionality used across multiple components.
 */
public class SocialUtils {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    // ===== TIME FORMATTING =====

    /**
     * Formats an instant to a readable date.
     * Example: "Jan 15, 2025"
     */
    @NotNull
    public static String formatDate(@NotNull Instant instant) {
        LocalDateTime date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return date.format(DATE_FORMATTER);
    }

    /**
     * Formats an instant to a readable time.
     * Example: "14:30"
     */
    @NotNull
    public static String formatTime(@NotNull Instant instant) {
        LocalDateTime time = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return time.format(TIME_FORMATTER);
    }

    /**
     * Formats a duration to a readable string.
     * Example: "5 days ago", "2 hours ago", "just now"
     */
    @NotNull
    public static String formatTimeAgo(@NotNull Instant instant) {
        Duration duration = Duration.between(instant, Instant.now());

        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return "just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        } else if (seconds < 604800) {
            long days = seconds / 86400;
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        } else if (seconds < 2592000) {
            long weeks = seconds / 604800;
            return weeks + " week" + (weeks != 1 ? "s" : "") + " ago";
        } else if (seconds < 31536000) {
            long months = seconds / 2592000;
            return months + " month" + (months != 1 ? "s" : "") + " ago";
        } else {
            long years = seconds / 31536000;
            return years + " year" + (years != 1 ? "s" : "") + " ago";
        }
    }

    // ===== ITEM CREATION =====

    /**
     * Creates a simple item with name and lore.
     */
    @NotNull
    public static ItemStack createItem(@NotNull Material material,
                                       @NotNull Component name,
                                       @NotNull List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(l -> l.decoration(TextDecoration.ITALIC, false))
                    .toList());
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates a filler item for GUIs (gray stained glass pane).
     */
    @NotNull
    public static ItemStack createFillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
        }

        return filler;
    }

    // ===== TEXT FORMATTING =====

    /**
     * Truncates a string to a maximum length.
     */
    @NotNull
    public static String truncate(@NotNull String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Pluralizes a word based on count.
     * Example: pluralize(1, "member") -> "1 member"
     *          pluralize(2, "member") -> "2 members"
     */
    @NotNull
    public static String pluralize(int count, @NotNull String singular) {
        return count + " " + singular + (count != 1 ? "s" : "");
    }

    // ===== VALIDATION =====

    /**
     * Checks if a string is a valid clan/party name.
     * - Only alphanumeric and spaces
     * - No leading/trailing spaces
     * - Length between min and max
     */
    public static boolean isValidName(@NotNull String name, int minLength, int maxLength) {
        if (name.length() < minLength || name.length() > maxLength) {
            return false;
        }

        if (name.startsWith(" ") || name.endsWith(" ")) {
            return false;
        }

        return name.matches("^[a-zA-Z0-9 ]+$");
    }

    /**
     * Checks if a string is a valid clan tag.
     * - Only alphanumeric
     * - No spaces
     * - Length between min and max
     */
    public static boolean isValidTag(@NotNull String tag, int minLength, int maxLength) {
        if (tag.length() < minLength || tag.length() > maxLength) {
            return false;
        }

        return tag.matches("^[a-zA-Z0-9]+$");
    }

    // ===== COLOR HELPERS =====

    /**
     * Gets a color based on online status.
     */
    @NotNull
    public static NamedTextColor getOnlineColor(boolean online) {
        return online ? NamedTextColor.GREEN : NamedTextColor.GRAY;
    }

    /**
     * Gets a color based on rank.
     */
    @NotNull
    public static NamedTextColor getRankColor(@NotNull String rank) {
        return switch (rank.toUpperCase()) {
            case "OWNER" -> NamedTextColor.GOLD;
            case "ADMIN" -> NamedTextColor.RED;
            case "MEMBER" -> NamedTextColor.GRAY;
            default -> NamedTextColor.WHITE;
        };
    }

    // ===== PAGINATION =====

    /**
     * Calculates the number of pages needed for pagination.
     */
    public static int calculatePages(int totalItems, int itemsPerPage) {
        return (int) Math.ceil((double) totalItems / itemsPerPage);
    }

    /**
     * Gets items for a specific page.
     */
    @NotNull
    public static <T> List<T> getPage(@NotNull List<T> items, int page, int itemsPerPage) {
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, items.size());

        if (start >= items.size()) {
            return List.of();
        }

        return items.subList(start, end);
    }

    // ===== COMPONENT BUILDERS =====

    /**
     * Creates a header component.
     */
    @NotNull
    public static Component createHeader(@NotNull String text) {
        return Component.text("=== " + text + " ===", NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    /**
     * Creates a clickable component.
     */
    @NotNull
    public static Component createClickable(@NotNull String text, @NotNull String command,
                                            @NotNull NamedTextColor color) {
        return Component.text("[" + text + "]", color, TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        Component.text("Click to " + text.toLowerCase(), NamedTextColor.GRAY)
                ));
    }

    /**
     * Creates a separator line.
     */
    @NotNull
    public static Component createSeparator() {
        return Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
                NamedTextColor.DARK_GRAY);
    }

    // ===== PERMISSION HELPERS =====

    /**
     * Checks if a player has a social permission.
     */
    public static boolean hasPermission(@NotNull org.bukkit.entity.Player player,
                                        @NotNull String permission) {
        return player.hasPermission("social." + permission);
    }

    /**
     * Checks if a player has admin permission.
     */
    public static boolean isAdmin(@NotNull org.bukkit.entity.Player player) {
        return player.hasPermission("social.admin");
    }

    // ===== ARRAY/LIST HELPERS =====

    /**
     * Joins arguments from an array starting at an index.
     * Example: joinArgs(["party", "chat", "hello", "world"], 2) -> "hello world"
     */
    @NotNull
    public static String joinArgs(@NotNull String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, startIndex, args.length));
    }

    // ===== UUID HELPERS =====

    /**
     * Safely parses a UUID string.
     * Returns null if invalid.
     */
    public static java.util.UUID parseUUID(@NotNull String uuidString) {
        try {
            return java.util.UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== PRIVATE CONSTRUCTOR =====

    private SocialUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
}