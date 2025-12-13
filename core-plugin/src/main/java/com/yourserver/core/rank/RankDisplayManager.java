package com.yourserver.core.rank;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages rank display with custom Unicode icons.
 * Integrates with LuckPerms for rank detection.
 */
public class RankDisplayManager {

    private final Logger logger;
    private LuckPerms luckPerms;
    private final Map<String, RankData> rankMappings;

    public RankDisplayManager(Logger logger) {
        this.logger = logger;

        // Initialize LuckPerms API
        try {
            RegisteredServiceProvider<LuckPerms> provider =
                    Bukkit.getServicesManager().getRegistration(LuckPerms.class);

            if (provider != null) {
                this.luckPerms = provider.getProvider();
                logger.info("LuckPerms API hooked successfully");
            } else {
                logger.warning("LuckPerms not found! Rank icons will not work.");
                this.luckPerms = null;
            }
        } catch (Exception e) {
            logger.warning("Failed to hook LuckPerms: " + e.getMessage());
            this.luckPerms = null;
        }

        // Initialize rank mappings
        this.rankMappings = new HashMap<>();
        initializeRankMappings();

        logger.info("Rank Display Manager initialized with " + rankMappings.size() + " ranks");
    }

    /**
     * Initialize all rank mappings with Unicode characters and colors.
     */
    private void initializeRankMappings() {
        // Format: rank name, unicode char, color code, priority, display name
        rankMappings.put("owner", new RankData("\uE007", "§c", 16, "OWNER"));
        rankMappings.put("admin", new RankData("\uE00E", "§c", 15, "ADMIN"));
        rankMappings.put("developer", new RankData("\uE000", "§b", 14, "DEVELOPER"));
        rankMappings.put("mod", new RankData("\uE006", "§9", 13, "MOD"));
        rankMappings.put("builder", new RankData("\uE00F", "§2", 12, "BUILDER"));
        rankMappings.put("helper", new RankData("\uE002", "§a", 11, "HELPER"));
        rankMappings.put("youtube", new RankData("\uE00D", "§c", 10, "YOUTUBE"));
        rankMappings.put("twitch", new RankData("\uE009", "§5", 9, "TWITCH"));
        rankMappings.put("ultravip", new RankData("\uE00A", "§d", 8, "ULTRAVIP"));
        rankMappings.put("megavip", new RankData("\uE005", "§6", 7, "MEGAVIP"));
        rankMappings.put("vipplus", new RankData("\uE00C", "§b", 6, "VIP+"));
        rankMappings.put("vip", new RankData("\uE00B", "§6", 5, "VIP"));
        rankMappings.put("legend", new RankData("\uE004", "§d", 4, "LEGEND"));
        rankMappings.put("hero", new RankData("\uE003", "§c", 3, "HERO"));
        rankMappings.put("elite", new RankData("\uE001", "§b", 2, "ELITE"));
        rankMappings.put("player", new RankData("\uE008", "§7", 1, "PLAYER"));
    }

    /**
     * Get the highest priority rank for a player.
     */
    @Nullable
    public RankData getPlayerRank(@NotNull Player player) {
        if (luckPerms == null) {
            return null;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return null;
            }

            RankData highestRank = null;
            int highestPriority = -1;

            // Get all inheritance nodes using NodeType.INHERITANCE
            for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
                String groupName = node.getGroupName().toLowerCase();
                RankData rankData = rankMappings.get(groupName);

                if (rankData != null && rankData.priority > highestPriority) {
                    highestRank = rankData;
                    highestPriority = rankData.priority;
                }
            }

            return highestRank;
        } catch (Exception e) {
            logger.warning("Error getting rank for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get formatted rank display: [ICON] RankName
     */
    @NotNull
    public String getRankDisplay(@NotNull Player player) {
        RankData rank = getPlayerRank(player);
        if (rank == null) {
            return "§7" + player.getName();
        }
        return rank.unicodeChar + " " + rank.colorCode + rank.displayName;
    }

    /**
     * Get formatted player name: [ICON] PlayerName
     */
    @NotNull
    public String getFormattedPlayerName(@NotNull Player player) {
        RankData rank = getPlayerRank(player);
        if (rank == null) {
            return "§7" + player.getName();
        }
        return rank.unicodeChar + " " + rank.colorCode + player.getName();
    }

    /**
     * Get just the rank icon.
     */
    @NotNull
    public String getRankIcon(@NotNull Player player) {
        RankData rank = getPlayerRank(player);
        return rank != null ? rank.unicodeChar : "";
    }

    /**
     * Get rank color code.
     */
    @NotNull
    public String getRankColor(@NotNull Player player) {
        RankData rank = getPlayerRank(player);
        return rank != null ? rank.colorCode : "§7";
    }

    /**
     * Check if player has a specific rank.
     */
    public boolean hasRank(@NotNull Player player, @NotNull String rankName) {
        if (luckPerms == null) {
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) {
                return false;
            }

            return user.getNodes(NodeType.INHERITANCE).stream()
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase(rankName));
        } catch (Exception e) {
            logger.warning("Error checking rank for " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Rank data storage class.
     */
    public static class RankData {
        private final String unicodeChar;
        private final String colorCode;
        private final int priority;
        private final String displayName;

        public RankData(String unicodeChar, String colorCode, int priority, String displayName) {
            this.unicodeChar = unicodeChar;
            this.colorCode = colorCode;
            this.priority = priority;
            this.displayName = displayName;
        }

        public String getUnicodeChar() { return unicodeChar; }
        public String getColorCode() { return colorCode; }
        public int getPriority() { return priority; }
        public String getDisplayName() { return displayName; }
    }
}