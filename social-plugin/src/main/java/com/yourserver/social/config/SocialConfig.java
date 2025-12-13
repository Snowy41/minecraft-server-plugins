package com.yourserver.social.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;

/**
 * Configuration loader for social plugin settings.
 */
public class SocialConfig {

    private final FriendsConfig friendsConfig;
    private final MessagesConfig messagesConfig;

    private SocialConfig(FriendsConfig friendsConfig, MessagesConfig messagesConfig) {
        this.friendsConfig = friendsConfig;
        this.messagesConfig = messagesConfig;
    }

    public static SocialConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");

        try {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .file(configFile)
                    .build();

            CommentedConfigurationNode root = loader.load();

            FriendsConfig friends = loadFriends(root.node("friends"));
            MessagesConfig messages = loadMessages(root.node("messages"));

            return new SocialConfig(friends, messages);

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

    private static MessagesConfig loadMessages(CommentedConfigurationNode node) {
        return new MessagesConfig(
                node.node("prefix").getString("<gradient:#4169E1:#1E90FF>[Social]</gradient> "),
                node.node("friend-request-sent").getString("<green>Friend request sent to <white>{player}"),
                node.node("friend-request-received").getString("<yellow>{player} <green>wants to be your friend!"),
                node.node("friend-added").getString("<green>You are now friends with <white>{player}"),
                node.node("friend-removed").getString("<red>Removed <white>{player} <red>from friends"),
                node.node("friend-already-added").getString("<red>{player} is already your friend!"),
                node.node("friend-request-expired").getString("<red>Friend request expired!"),
                node.node("max-friends-reached").getString("<red>You have reached the maximum number of friends! <gray>({max})"),
                node.node("player-not-found").getString("<red>Player not found!"),
                node.node("cannot-add-self").getString("<red>You cannot add yourself!"),
                node.node("no-pending-requests").getString("<red>No pending requests!")
        );
    }

    public FriendsConfig getFriendsConfig() {
        return friendsConfig;
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

    public static class MessagesConfig {
        private final String prefix;
        private final String friendRequestSent;
        private final String friendRequestReceived;
        private final String friendAdded;
        private final String friendRemoved;
        private final String friendAlreadyAdded;
        private final String friendRequestExpired;
        private final String maxFriendsReached;
        private final String playerNotFound;
        private final String cannotAddSelf;
        private final String noPendingRequests;

        public MessagesConfig(String prefix, String friendRequestSent, String friendRequestReceived,
                              String friendAdded, String friendRemoved, String friendAlreadyAdded,
                              String friendRequestExpired, String maxFriendsReached, String playerNotFound,
                              String cannotAddSelf, String noPendingRequests) {
            this.prefix = prefix;
            this.friendRequestSent = friendRequestSent;
            this.friendRequestReceived = friendRequestReceived;
            this.friendAdded = friendAdded;
            this.friendRemoved = friendRemoved;
            this.friendAlreadyAdded = friendAlreadyAdded;
            this.friendRequestExpired = friendRequestExpired;
            this.maxFriendsReached = maxFriendsReached;
            this.playerNotFound = playerNotFound;
            this.cannotAddSelf = cannotAddSelf;
            this.noPendingRequests = noPendingRequests;
        }

        public String getPrefix() { return prefix; }
        public String getFriendRequestSent() { return friendRequestSent; }
        public String getFriendRequestReceived() { return friendRequestReceived; }
        public String getFriendAdded() { return friendAdded; }
        public String getFriendRemoved() { return friendRemoved; }
        public String getFriendAlreadyAdded() { return friendAlreadyAdded; }
        public String getFriendRequestExpired() { return friendRequestExpired; }
        public String getMaxFriendsReached() { return maxFriendsReached; }
        public String getPlayerNotFound() { return playerNotFound; }
        public String getCannotAddSelf() { return cannotAddSelf; }
        public String getNoPendingRequests() { return noPendingRequests; }

        public String getMessage(String key) {
            return switch (key) {
                case "friend-request-sent" -> friendRequestSent;
                case "friend-request-received" -> friendRequestReceived;
                case "friend-added" -> friendAdded;
                case "friend-removed" -> friendRemoved;
                case "friend-already-added" -> friendAlreadyAdded;
                case "friend-request-expired" -> friendRequestExpired;
                case "max-friends-reached" -> maxFriendsReached;
                case "player-not-found" -> playerNotFound;
                case "cannot-add-self" -> cannotAddSelf;
                case "no-pending-requests" -> noPendingRequests;
                default -> "";
            };
        }
    }
}