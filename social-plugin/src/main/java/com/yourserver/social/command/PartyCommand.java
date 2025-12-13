package com.yourserver.social.command;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.gui.GUIManager;
import com.yourserver.social.manager.PartyManager;
import com.yourserver.social.model.Party;
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
 * Party command handler.
 *
 * Usage:
 * /party create       - Create a party
 * /party invite <player> - Invite player
 * /party accept       - Accept invite
 * /party deny         - Deny invite
 * /party leave        - Leave party
 * /party kick <player> - Kick player
 * /party list         - List members
 * /party chat <message> - Send party chat
 * /party disband      - Disband party (leader only)
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final SocialPlugin plugin;
    private final PartyManager partyManager;
    private final GUIManager guiManager;

    public PartyCommand(@NotNull SocialPlugin plugin,
                        @NotNull PartyManager partyManager,
                        @NotNull GUIManager guiManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
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
            // Show party info or open GUI
            handleList(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny", "decline" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "list", "members" -> handleList(player);
            case "chat", "c" -> handleChat(player, args);
            case "disband" -> handleDisband(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(@NotNull Player player) {
        PartyManager.PartyResult result = partyManager.createParty(player);

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<green>Party created! Invite friends with <white>/party invite <player>"
            ));
            case ALREADY_IN_PARTY -> player.sendMessage(Component.text(
                    "You're already in a party! Leave with /party leave", NamedTextColor.RED));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void handleInvite(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /party invite <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            player.sendMessage(Component.text("Player not online!", NamedTextColor.RED));
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text("You cannot invite yourself!", NamedTextColor.RED));
            return;
        }

        PartyManager.PartyResult result = partyManager.invitePlayer(player, target.getUniqueId());

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<green>Invited <white>" + target.getName() + " <green>to the party"
            ));
            case NOT_IN_PARTY -> player.sendMessage(Component.text(
                    "You're not in a party! Create one with /party create", NamedTextColor.RED));
            case NOT_LEADER -> player.sendMessage(Component.text(
                    "Only the party leader can invite players!", NamedTextColor.RED));
            case PARTY_FULL -> player.sendMessage(Component.text(
                    "Party is full!", NamedTextColor.RED));
            case TARGET_IN_PARTY -> player.sendMessage(Component.text(
                    target.getName() + " is already in a party!", NamedTextColor.YELLOW));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void handleAccept(@NotNull Player player) {
        PartyManager.PartyResult result = partyManager.acceptInvite(player);

        switch (result) {
            case SUCCESS -> {
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                "<green>You joined the party!"
                ));

                // Show party members
                Party party = partyManager.getPlayerParty(player.getUniqueId());
                if (party != null) {
                    showPartyInfo(player, party);
                }
            }
            case NO_PENDING_INVITE -> player.sendMessage(Component.text(
                    "No pending party invites!", NamedTextColor.YELLOW));
            case PARTY_NOT_FOUND -> player.sendMessage(Component.text(
                    "That party no longer exists!", NamedTextColor.RED));
            case PARTY_FULL -> player.sendMessage(Component.text(
                    "Party is full!", NamedTextColor.RED));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void handleDeny(@NotNull Player player) {
        partyManager.denyInvite(player.getUniqueId());
        player.sendMessage(Component.text("Party invite denied.", NamedTextColor.GRAY));
    }

    private void handleLeave(@NotNull Player player) {
        PartyManager.PartyResult result = partyManager.leaveParty(player);

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<yellow>You left the party"
            ));
            case NOT_IN_PARTY -> player.sendMessage(Component.text(
                    "You're not in a party!", NamedTextColor.RED));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void handleKick(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /party kick <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

        if (!offlineTarget.hasPlayedBefore()) {
            player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
            return;
        }

        PartyManager.PartyResult result = partyManager.kickPlayer(player, offlineTarget.getUniqueId());

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<yellow>Kicked <white>" + offlineTarget.getName() + " <yellow>from the party"
            ));
            case NOT_IN_PARTY -> player.sendMessage(Component.text(
                    "You're not in a party!", NamedTextColor.RED));
            case NOT_LEADER -> player.sendMessage(Component.text(
                    "Only the party leader can kick players!", NamedTextColor.RED));
            case PLAYER_NOT_IN_PARTY -> player.sendMessage(Component.text(
                    offlineTarget.getName() + " is not in your party!", NamedTextColor.RED));
            case CANNOT_KICK_LEADER -> player.sendMessage(Component.text(
                    "Cannot kick the party leader!", NamedTextColor.RED));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void handleList(@NotNull Player player) {
        Party party = partyManager.getPlayerParty(player.getUniqueId());

        if (party == null) {
            player.sendMessage(Component.text("You're not in a party!", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Create one with: /party create", NamedTextColor.GRAY));
            return;
        }

        showPartyInfo(player, party);
    }

    private void handleChat(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /party chat <message>", NamedTextColor.RED));
            return;
        }

        Party party = partyManager.getPlayerParty(player.getUniqueId());

        if (party == null) {
            player.sendMessage(Component.text("You're not in a party!", NamedTextColor.RED));
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        partyManager.sendPartyChat(player, message);
    }

    private void handleDisband(@NotNull Player player) {
        PartyManager.PartyResult result = partyManager.disbandParty(player);

        switch (result) {
            case SUCCESS -> player.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<red>Party disbanded!"
            ));
            case NOT_IN_PARTY -> player.sendMessage(Component.text(
                    "You're not in a party!", NamedTextColor.RED));
            case NOT_LEADER -> player.sendMessage(Component.text(
                    "Only the party leader can disband the party!", NamedTextColor.RED));
            default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
        }
    }

    private void showPartyInfo(@NotNull Player player, @NotNull Party party) {
        player.sendMessage(Component.text("=== Party Members (" + party.size() + "/" +
                party.getMaxMembers() + ") ===", NamedTextColor.GOLD));

        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            String name = p != null ? p.getName() : "Unknown";
            boolean online = p != null;
            boolean isLeader = party.isLeader(member);

            String status = online ? "§a✓ Online" : "§7✗ Offline";
            String role = isLeader ? " §e[Leader]" : "";

            player.sendMessage(Component.text("  • " + name + role + " " + status));
        }
    }

    private void sendHelp(@NotNull Player player) {
        player.sendMessage(Component.text("=== Party Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/party create", NamedTextColor.WHITE)
                .append(Component.text(" - Create a party", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party invite <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Invite player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party accept", NamedTextColor.WHITE)
                .append(Component.text(" - Accept invite", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party leave", NamedTextColor.WHITE)
                .append(Component.text(" - Leave party", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party kick <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Kick player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party list", NamedTextColor.WHITE)
                .append(Component.text(" - List members", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party chat <message>", NamedTextColor.WHITE)
                .append(Component.text(" - Send party chat", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/party disband", NamedTextColor.WHITE)
                .append(Component.text(" - Disband party", NamedTextColor.GRAY)));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "deny", "leave",
                    "kick", "list", "chat", "disband");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("invite") || subCommand.equals("kick")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}