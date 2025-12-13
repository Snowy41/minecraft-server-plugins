package com.yourserver.social.command;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.gui.GUIManager;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.model.Friend;
import com.yourserver.social.model.FriendRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Friend command handler.
 *
 * Usage:
 * /friend add <player>     - Send friend request
 * /friend remove <player>  - Remove friend
 * /friend accept <player>  - Accept friend request
 * /friend deny <player>    - Deny friend request
 * /friend list             - List all friends (or open GUI)
 * /friend requests         - List pending requests
 */
public class FriendCommand implements CommandExecutor, TabCompleter {

    private final SocialPlugin plugin;
    private final FriendManager friendManager;
    private final GUIManager guiManager;

    public FriendCommand(@NotNull SocialPlugin plugin,
                         @NotNull FriendManager friendManager,
                         @NotNull GUIManager guiManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // Open friends GUI
            guiManager.openFriendsGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add" -> handleAdd(player, args);
            case "remove", "delete" -> handleRemove(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny", "decline" -> handleDeny(player, args);
            case "list" -> handleList(player);
            case "requests" -> handleRequests(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleAdd(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /friend add <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];

        // Get target player (online or offline)
        Player target = Bukkit.getPlayer(targetName);
        OfflinePlayer offlineTarget = target != null ? target : Bukkit.getOfflinePlayer(targetName);

        if (!offlineTarget.hasPlayedBefore() && target == null) {
            sendMessage(player, "player-not-found");
            return;
        }

        UUID targetUuid = offlineTarget.getUniqueId();
        String actualName = offlineTarget.getName();

        // Send friend request
        friendManager.sendFriendRequest(player, targetUuid, actualName).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "friend-request-sent", actualName);
                    case ALREADY_FRIENDS -> sendMessage(player, "friend-already-added", actualName);
                    case REQUEST_EXISTS -> player.sendMessage(Component.text(
                            "You already sent a friend request to " + actualName, NamedTextColor.YELLOW));
                    case MAX_FRIENDS -> sendMessage(player, "max-friends-reached",
                            String.valueOf(plugin.getSocialConfig().getFriendsConfig().getMaxFriends()));
                    case CANNOT_ADD_SELF -> sendMessage(player, "cannot-add-self");
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleRemove(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /friend remove <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

        if (!offlineTarget.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        UUID targetUuid = offlineTarget.getUniqueId();

        friendManager.removeFriend(player, targetUuid).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sendMessage(player, "friend-removed", offlineTarget.getName());
                } else {
                    player.sendMessage(Component.text(
                            offlineTarget.getName() + " is not your friend!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleAccept(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /friend accept <player>", NamedTextColor.RED));
            return;
        }

        String senderName = args[1];
        OfflinePlayer offlineSender = Bukkit.getOfflinePlayer(senderName);

        if (!offlineSender.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        UUID senderUuid = offlineSender.getUniqueId();

        friendManager.acceptFriendRequest(player, senderUuid).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "friend-added", offlineSender.getName());
                    case REQUEST_NOT_FOUND -> sendMessage(player, "no-pending-requests");
                    case REQUEST_EXPIRED -> sendMessage(player, "friend-request-expired");
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleDeny(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /friend deny <player>", NamedTextColor.RED));
            return;
        }

        String senderName = args[1];
        OfflinePlayer offlineSender = Bukkit.getOfflinePlayer(senderName);

        if (!offlineSender.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        UUID senderUuid = offlineSender.getUniqueId();

        friendManager.denyFriendRequest(player.getUniqueId(), senderUuid).thenRun(() -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(Component.text("Friend request denied.", NamedTextColor.GRAY));
            });
        });
    }

    private void handleList(@NotNull Player player) {
        friendManager.getFriends(player.getUniqueId()).thenAccept(friends -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (friends.isEmpty()) {
                    player.sendMessage(Component.text("You have no friends yet!", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("Add friends with: /friend add <player>", NamedTextColor.GRAY));
                    return;
                }

                player.sendMessage(Component.text("=== Your Friends (" + friends.size() + ") ===", NamedTextColor.GOLD));
                for (Friend friend : friends) {
                    boolean online = Bukkit.getPlayer(friend.getFriendUuid()) != null;
                    String status = online ? "§a✓ Online" : "§7✗ Offline";
                    player.sendMessage(Component.text("  • " + friend.getFriendName() + " " + status));
                }
            });
        });
    }

    private void handleRequests(@NotNull Player player) {
        friendManager.getPendingRequests(player.getUniqueId()).thenAccept(requests -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (requests.isEmpty()) {
                    player.sendMessage(Component.text("No pending friend requests!", NamedTextColor.YELLOW));
                    return;
                }

                player.sendMessage(Component.text("=== Friend Requests (" + requests.size() + ") ===", NamedTextColor.GOLD));
                for (FriendRequest request : requests) {
                    player.sendMessage(plugin.getMiniMessage().deserialize(
                            "  <yellow>" + request.getFromName() +
                                    " <click:run_command:/friend accept " + request.getFromName() + ">[Accept]</click>" +
                                    " <click:run_command:/friend deny " + request.getFromName() + ">[Deny]</click>"
                    ));
                }
            });
        });
    }

    private void sendHelp(@NotNull Player player) {
        player.sendMessage(Component.text("=== Friend Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/friend add <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Send friend request", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/friend remove <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Remove friend", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/friend accept <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Accept request", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/friend deny <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Deny request", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/friend list", NamedTextColor.WHITE)
                .append(Component.text(" - List all friends", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/friend requests", NamedTextColor.WHITE)
                .append(Component.text(" - List pending requests", NamedTextColor.GRAY)));
    }

    private void sendMessage(@NotNull Player player, @NotNull String key, @NotNull String... replacements) {
        String message = plugin.getSocialConfig().getMessagesConfig().getMessage(key);

        for (int i = 0; i < replacements.length; i++) {
            message = message.replace("{player}", replacements[i])
                    .replace("{max}", replacements[i]);
        }

        player.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() + message
        ));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("add", "remove", "accept", "deny", "list", "requests");
        }

        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}