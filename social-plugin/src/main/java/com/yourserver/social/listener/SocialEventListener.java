package com.yourserver.social.listener;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.manager.PartyManager;
import com.yourserver.social.messaging.SocialMessenger;
import com.yourserver.social.model.Party;
import com.yourserver.social.util.SocialUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles social-related events:
 * - Friend online/offline notifications
 * - Party chat prefix
 * - Status updates
 */
public class SocialEventListener implements Listener {

    private final SocialPlugin plugin;
    private final FriendManager friendManager;
    private final PartyManager partyManager;
    private final SocialMessenger messenger;

    public SocialEventListener(@NotNull SocialPlugin plugin,
                               @NotNull FriendManager friendManager,
                               @NotNull PartyManager partyManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.partyManager = partyManager;
        this.messenger = plugin.getMessenger();
    }

    /**
     * When a player joins:
     * - Update their status to ONLINE
     * - Notify their friends they're online
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Update player status in Redis
        String serverName = getServerName();
        messenger.updateStatus(player.getUniqueId(), serverName, SocialMessenger.PlayerStatus.ONLINE);

        // Notify friends this player is online (async to avoid blocking)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            friendManager.getFriends(player.getUniqueId()).thenAccept(friends -> {
                for (var friend : friends) {
                    Player friendPlayer = Bukkit.getPlayer(friend.getFriendUuid());

                    if (friendPlayer != null) {
                        // Friend is online on this server
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            friendPlayer.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                            plugin.getSocialConfig().getMessagesConfig().getMessage("friend-online")
                                                    .replace("{player}", player.getName())
                                                    .replace("{server}", serverName)
                            ));
                        });
                    }
                }
            });
        });

        // Log join for debugging
        plugin.getLogger().info("Player " + player.getName() + " joined - status updated to ONLINE");
    }

    /**
     * When a player quits:
     * - Update their status to OFFLINE
     * - Notify their friends they're offline
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Update player status in Redis
        String serverName = getServerName();
        messenger.updateStatus(player.getUniqueId(), serverName, SocialMessenger.PlayerStatus.OFFLINE);

        // Notify friends this player is offline (async)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            friendManager.getFriends(player.getUniqueId()).thenAccept(friends -> {
                for (var friend : friends) {
                    Player friendPlayer = Bukkit.getPlayer(friend.getFriendUuid());

                    if (friendPlayer != null) {
                        // Friend is online on this server
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            friendPlayer.sendMessage(plugin.getMiniMessage().deserialize(
                                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                            plugin.getSocialConfig().getMessagesConfig().getMessage("friend-offline")
                                                    .replace("{player}", player.getName())
                            ));
                        });
                    }
                }
            });
        });

        // Log quit for debugging
        plugin.getLogger().info("Player " + player.getName() + " quit - status updated to OFFLINE");
    }

    /**
     * Party chat shortcut: Messages starting with "!" go to party chat
     * Example: "!Hello party!" -> sends to party chat
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check if message starts with party chat prefix
        String chatPrefix = plugin.getSocialConfig().getMessagesConfig().getMessage("party-chat-prefix");
        if (chatPrefix == null || chatPrefix.isEmpty()) {
            chatPrefix = "!"; // Default
        }

        if (message.startsWith(chatPrefix)) {
            event.setCancelled(true);

            // Remove prefix
            String actualMessage = message.substring(chatPrefix.length()).trim();

            if (actualMessage.isEmpty()) {
                player.sendMessage(Component.text("Message cannot be empty!", NamedTextColor.RED));
                return;
            }

            // Check if player is in a party
            Party party = partyManager.getPlayerParty(player.getUniqueId());

            if (party == null) {
                player.sendMessage(Component.text("You're not in a party!", NamedTextColor.RED));
                return;
            }

            // Send party chat
            partyManager.sendPartyChat(player, actualMessage);
        }
    }

    /**
     * Gets the current server name from config or uses default.
     */
    @NotNull
    private String getServerName() {
        // Try to get from system property (set by startup script)
        String serverName = System.getProperty("server.name");

        if (serverName == null || serverName.isEmpty()) {
            // Fallback to Bukkit server name
            serverName = Bukkit.getServer().getName();
        }

        if (serverName == null || serverName.isEmpty()) {
            // Ultimate fallback
            serverName = "server";
        }

        return serverName;
    }

    /**
     * Formats a time ago string using SocialUtils.
     * Example: "5 minutes ago", "2 hours ago"
     */
    @NotNull
    private String formatTimeAgo(@NotNull java.time.Instant instant) {
        return SocialUtils.formatTimeAgo(instant);
    }
}