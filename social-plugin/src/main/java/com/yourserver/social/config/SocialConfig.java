package com.yourserver.social.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration loader for social plugin settings.
 */
public class SocialConfig {

    private final String storageType;
    private final FriendsConfig friendsConfig;
    private final PartiesConfig partiesConfig;
    private final ClansConfig clansConfig;
    private final MessagesConfig messagesConfig;

    private SocialConfig(String storageType, FriendsConfig friendsConfig, PartiesConfig partiesConfig,
                         ClansConfig clansConfig, MessagesConfig messagesConfig) {
        this.storageType = storageType;
        this.friendsConfig = friendsConfig;
        this.partiesConfig = partiesConfig;
        this.clansConfig = clansConfig;
        this.messagesConfig = messagesConfig;
    }

    public static SocialConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            String storage = root.node("storage").getString("json");
            FriendsConfig friends = loadFriends(root.node("friends"));
            PartiesConfig parties = loadParties(root.node("parties"));
            ClansConfig clans = loadClans(root.node("clans"));
            MessagesConfig messages = loadMessages(root.node("messages"));

            return new SocialConfig(storage, friends, parties, clans, messages);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private static FriendsConfig loadFriends(CommentedConfigurationNode node) {
        return new FriendsConfig(
                node.node("enabled").getBoolean(true),
                node.node("max-friends").getInt(50),
                node.node("requests", "expire-after").getInt(300)
        );
    }

    private static PartiesConfig loadParties(CommentedConfigurationNode node) {
        return new PartiesConfig(
                node.node("enabled").getBoolean(true),
                node.node("max-members").getInt(8),
                node.node("leader-only-invite").getBoolean(true),
                node.node("invites", "expire-after").getInt(60)
        );
    }

    private static ClansConfig loadClans(CommentedConfigurationNode node) {
        return new ClansConfig(
                node.node("enabled").getBoolean(true),
                node.node("max-members").getInt(50),
                node.node("creation", "min-name-length").getInt(3),
                node.node("creation", "max-name-length").getInt(16),
                node.node("creation", "min-tag-length").getInt(2),
                node.node("creation", "max-tag-length").getInt(6)
        );
    }

    private static MessagesConfig loadMessages(CommentedConfigurationNode node) {
        Map<String, String> allMessages = new HashMap<>();

        // Load all message keys
        allMessages.put("prefix", node.node("prefix").getString("<gradient:#4169E1:#1E90FF>[Social]</gradient> "));
        allMessages.put("friend-request-sent", node.node("friend-request-sent").getString("<green>Friend request sent to <white>{player}"));
        allMessages.put("friend-request-received", node.node("friend-request-received").getString("<yellow>{player} <green>wants to be your friend!"));
        allMessages.put("friend-added", node.node("friend-added").getString("<green>You are now friends with <white>{player}"));
        allMessages.put("friend-removed", node.node("friend-removed").getString("<red>Removed <white>{player} <red>from friends"));
        allMessages.put("friend-online", node.node("friend-online").getString("<green>✓ <white>{player} <gray>is now online"));
        allMessages.put("friend-offline", node.node("friend-offline").getString("<red>✗ <white>{player} <gray>is now offline"));
        allMessages.put("friend-already-added", node.node("friend-already-added").getString("<red>{player} is already your friend!"));
        allMessages.put("friend-request-expired", node.node("friend-request-expired").getString("<red>Friend request expired!"));
        allMessages.put("max-friends-reached", node.node("max-friends-reached").getString("<red>Maximum friends reached!"));

        // Party messages
        allMessages.put("party-chat-prefix", node.node("party-chat-prefix").getString("!"));

        // Clan messages
        allMessages.put("clan-created", node.node("clan-created").getString("<green>Clan created!"));
        allMessages.put("clan-invite-sent", node.node("clan-invite-sent").getString("<green>Invite sent!"));
        allMessages.put("clan-joined", node.node("clan-joined").getString("<green>You joined the clan!"));
        allMessages.put("clan-left", node.node("clan-left").getString("<yellow>You left the clan"));
        allMessages.put("clan-kicked", node.node("clan-kicked").getString("<red>Player kicked"));
        allMessages.put("clan-disbanded", node.node("clan-disbanded").getString("<red>Clan disbanded!"));
        allMessages.put("clan-name-taken", node.node("clan-name-taken").getString("<red>Name taken!"));
        allMessages.put("clan-tag-taken", node.node("clan-tag-taken").getString("<red>Tag taken!"));
        allMessages.put("clan-full", node.node("clan-full").getString("<red>Clan is full!"));
        allMessages.put("clan-not-in-clan", node.node("clan-not-in-clan").getString("<red>You're not in a clan!"));
        allMessages.put("clan-no-permission", node.node("clan-no-permission").getString("<red>No permission!"));

        // Error messages
        allMessages.put("player-not-found", node.node("player-not-found").getString("<red>Player not found!"));
        allMessages.put("cannot-add-self", node.node("cannot-add-self").getString("<red>Cannot add yourself!"));
        allMessages.put("no-pending-requests", node.node("no-pending-requests").getString("<red>No pending requests!"));

        return new MessagesConfig(allMessages);
    }

    public String getStorageType() {
        return storageType;
    }

    public FriendsConfig getFriendsConfig() {
        return friendsConfig;
    }

    public PartiesConfig getPartiesConfig() {
        return partiesConfig;
    }

    public ClansConfig getClansConfig() {
        return clansConfig;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public static class FriendsConfig {
        private final boolean enabled;
        private final int maxFriends;
        private final int requestExpireSeconds;

        public FriendsConfig(boolean enabled, int maxFriends, int requestExpireSeconds) {
            this.enabled = enabled;
            this.maxFriends = maxFriends;
            this.requestExpireSeconds = requestExpireSeconds;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxFriends() { return maxFriends; }
        public int getRequestExpireSeconds() { return requestExpireSeconds; }
    }

    public static class PartiesConfig {
        private final boolean enabled;
        private final int maxMembers;
        private final boolean leaderOnlyInvite;
        private final int inviteExpireSeconds;

        public PartiesConfig(boolean enabled, int maxMembers, boolean leaderOnlyInvite, int inviteExpireSeconds) {
            this.enabled = enabled;
            this.maxMembers = maxMembers;
            this.leaderOnlyInvite = leaderOnlyInvite;
            this.inviteExpireSeconds = inviteExpireSeconds;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxMembers() { return maxMembers; }
        public boolean isLeaderOnlyInvite() { return leaderOnlyInvite; }
        public int getInviteExpireSeconds() { return inviteExpireSeconds; }
    }

    public static class ClansConfig {
        private final boolean enabled;
        private final int maxMembers;
        private final int minNameLength;
        private final int maxNameLength;
        private final int minTagLength;
        private final int maxTagLength;

        public ClansConfig(boolean enabled, int maxMembers, int minNameLength, int maxNameLength,
                           int minTagLength, int maxTagLength) {
            this.enabled = enabled;
            this.maxMembers = maxMembers;
            this.minNameLength = minNameLength;
            this.maxNameLength = maxNameLength;
            this.minTagLength = minTagLength;
            this.maxTagLength = maxTagLength;
        }

        public boolean isEnabled() { return enabled; }
        public int getMaxMembers() { return maxMembers; }
        public int getMinNameLength() { return minNameLength; }
        public int getMaxNameLength() { return maxNameLength; }
        public int getMinTagLength() { return minTagLength; }
        public int getMaxTagLength() { return maxTagLength; }
    }

    public static class MessagesConfig {
        private final Map<String, String> messages;

        public MessagesConfig(Map<String, String> messages) {
            this.messages = new HashMap<>(messages);
        }

        public String getPrefix() {
            return messages.getOrDefault("prefix", "[Social] ");
        }

        public String getMessage(String key) {
            return messages.getOrDefault(key, "");
        }

        // Keep legacy getters for compatibility
        public String getFriendRequestSent() { return getMessage("friend-request-sent"); }
        public String getFriendRequestReceived() { return getMessage("friend-request-received"); }
        public String getFriendAdded() { return getMessage("friend-added"); }
        public String getFriendRemoved() { return getMessage("friend-removed"); }
        public String getFriendAlreadyAdded() { return getMessage("friend-already-added"); }
        public String getFriendRequestExpired() { return getMessage("friend-request-expired"); }
        public String getMaxFriendsReached() { return getMessage("max-friends-reached"); }
        public String getPlayerNotFound() { return getMessage("player-not-found"); }
        public String getCannotAddSelf() { return getMessage("cannot-add-self"); }
        public String getNoPendingRequests() { return getMessage("no-pending-requests"); }
    }
}