package com.yourserver.social.command;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.gui.GUIManager;
import com.yourserver.social.manager.ClanManager;
import com.yourserver.social.model.Clan;
import com.yourserver.social.util.SocialUtils;
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
 * Clan command handler.
 *
 * Usage:
 * /clan create <name> <tag> - Create a clan
 * /clan invite <player>      - Invite player
 * /clan accept <name>        - Accept clan invite
 * /clan deny <name>          - Deny clan invite
 * /clan leave                - Leave clan
 * /clan kick <player>        - Kick player
 * /clan promote <player>     - Promote member
 * /clan demote <player>      - Demote member
 * /clan chat <message>       - Send clan chat
 * /clan list                 - List clan members
 * /clan info                 - Show clan info
 * /clan disband              - Disband clan (owner only)
 */
public class ClanCommand implements CommandExecutor, TabCompleter {

    private final SocialPlugin plugin;
    private final ClanManager clanManager;
    private final GUIManager guiManager;

    public ClanCommand(@NotNull SocialPlugin plugin,
                       @NotNull ClanManager clanManager,
                       @NotNull GUIManager guiManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
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
            // Show clan info or open GUI
            handleInfo(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "deny", "decline" -> handleDeny(player, args);
            case "leave" -> handleLeave(player);
            case "kick" -> handleKick(player, args);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "chat", "c" -> handleChat(player, args);
            case "list", "members" -> handleList(player);
            case "info" -> handleInfo(player);
            case "disband" -> handleDisband(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /clan create <name> <tag>", NamedTextColor.RED));
            return;
        }

        String name = args[1];
        String tag = args[2];

        // Validate name and tag using SocialUtils
        if (!SocialUtils.isValidName(name, 3, 16)) {
            player.sendMessage(Component.text("Invalid clan name! Must be 3-16 characters, alphanumeric + spaces.", NamedTextColor.RED));
            return;
        }

        if (!SocialUtils.isValidTag(tag, 2, 6)) {
            player.sendMessage(Component.text("Invalid clan tag! Must be 2-6 characters, alphanumeric only.", NamedTextColor.RED));
            return;
        }

        clanManager.createClan(player, name, tag).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "clan-created", name, tag);
                    case ALREADY_IN_CLAN -> player.sendMessage(Component.text(
                            "You're already in a clan! Leave with /clan leave", NamedTextColor.RED));
                    case NAME_TAKEN -> sendMessage(player, "clan-name-taken");
                    case TAG_TAKEN -> sendMessage(player, "clan-tag-taken");
                    case INVALID_NAME -> player.sendMessage(Component.text(
                            "Invalid clan name!", NamedTextColor.RED));
                    case INVALID_TAG -> player.sendMessage(Component.text(
                            "Invalid clan tag!", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleInvite(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan invite <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            player.sendMessage(Component.text("Player not online!", NamedTextColor.RED));
            return;
        }

        if (target.equals(player)) {
            sendMessage(player, "cannot-add-self");
            return;
        }

        clanManager.invitePlayer(player, target.getUniqueId(), target.getName()).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "clan-invite-sent", target.getName());
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case NO_PERMISSION -> sendMessage(player, "clan-no-permission");
                    case CLAN_FULL -> sendMessage(player, "clan-full");
                    case TARGET_IN_CLAN -> player.sendMessage(Component.text(
                            target.getName() + " is already in a clan!", NamedTextColor.YELLOW));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleAccept(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan accept <clan name>", NamedTextColor.RED));
            return;
        }

        String clanName = SocialUtils.joinArgs(args, 1);

        // Find clan by name
        clanManager.getClanByName(clanName).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Clan not found!", NamedTextColor.RED));
                });
                return;
            }

            Clan clan = clanOpt.get();

            clanManager.acceptInvite(player, clan.getId()).thenAccept(result -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    switch (result) {
                        case SUCCESS -> {
                            sendMessage(player, "clan-joined", clan.getName());
                            showClanInfo(player, clan);
                        }
                        case CLAN_NOT_FOUND -> player.sendMessage(Component.text(
                                "That clan no longer exists!", NamedTextColor.RED));
                        case CLAN_FULL -> sendMessage(player, "clan-full");
                        default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                    }
                });
            });
        });
    }

    private void handleDeny(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan deny <clan name>", NamedTextColor.RED));
            return;
        }

        String clanName = SocialUtils.joinArgs(args, 1);

        clanManager.getClanByName(clanName).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Clan not found!", NamedTextColor.RED));
                });
                return;
            }

            Clan clan = clanOpt.get();
            clanManager.denyInvite(player.getUniqueId(), clan.getId()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Clan invite denied.", NamedTextColor.GRAY));
                });
            });
        });
    }

    private void handleLeave(@NotNull Player player) {
        clanManager.leaveClan(player).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "clan-left");
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case OWNER_CANNOT_LEAVE -> player.sendMessage(Component.text(
                            "Clan owners cannot leave! Use /clan disband or transfer ownership.", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleKick(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan kick <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        clanManager.kickPlayer(player, target.getUniqueId()).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "clan-kicked", target.getName());
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case NO_PERMISSION -> sendMessage(player, "clan-no-permission");
                    case PLAYER_NOT_IN_CLAN -> player.sendMessage(Component.text(
                            target.getName() + " is not in your clan!", NamedTextColor.RED));
                    case CANNOT_KICK_OWNER -> player.sendMessage(Component.text(
                            "Cannot kick the clan owner!", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handlePromote(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan promote <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        clanManager.promoteMember(player, target.getUniqueId(), Clan.ClanRank.ADMIN).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> player.sendMessage(Component.text(
                            "Promoted " + target.getName() + " to Admin!", NamedTextColor.GREEN));
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case NO_PERMISSION -> sendMessage(player, "clan-no-permission");
                    case PLAYER_NOT_IN_CLAN -> player.sendMessage(Component.text(
                            target.getName() + " is not in your clan!", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleDemote(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan demote <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore()) {
            sendMessage(player, "player-not-found");
            return;
        }

        clanManager.promoteMember(player, target.getUniqueId(), Clan.ClanRank.MEMBER).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> player.sendMessage(Component.text(
                            "Demoted " + target.getName() + " to Member!", NamedTextColor.YELLOW));
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case NO_PERMISSION -> sendMessage(player, "clan-no-permission");
                    case PLAYER_NOT_IN_CLAN -> player.sendMessage(Component.text(
                            target.getName() + " is not in your clan!", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void handleChat(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /clan chat <message>", NamedTextColor.RED));
            return;
        }

        String message = SocialUtils.joinArgs(args, 1);
        clanManager.sendClanChat(player, message);
    }

    private void handleList(@NotNull Player player) {
        clanManager.getPlayerClan(player.getUniqueId()).thenAccept(clanOpt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (clanOpt.isEmpty()) {
                    sendMessage(player, "clan-not-in-clan");
                    return;
                }

                showClanInfo(player, clanOpt.get());
            });
        });
    }

    private void handleInfo(@NotNull Player player) {
        clanManager.getPlayerClan(player.getUniqueId()).thenAccept(clanOpt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (clanOpt.isEmpty()) {
                    player.sendMessage(Component.text("You're not in a clan!", NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("Create one with: /clan create <name> <tag>", NamedTextColor.GRAY));
                    return;
                }

                showClanInfo(player, clanOpt.get());
            });
        });
    }

    private void handleDisband(@NotNull Player player) {
        clanManager.disbandClan(player).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                switch (result) {
                    case SUCCESS -> sendMessage(player, "clan-disbanded");
                    case NOT_IN_CLAN -> sendMessage(player, "clan-not-in-clan");
                    case NOT_OWNER -> player.sendMessage(Component.text(
                            "Only the clan owner can disband the clan!", NamedTextColor.RED));
                    default -> player.sendMessage(Component.text("An error occurred!", NamedTextColor.RED));
                }
            });
        });
    }

    private void showClanInfo(@NotNull Player player, @NotNull Clan clan) {
        player.sendMessage(SocialUtils.createHeader("Clan: " + clan.getName()));
        player.sendMessage(Component.text("Tag: [" + clan.getTag() + "]", NamedTextColor.YELLOW));
        player.sendMessage(Component.text(SocialUtils.pluralize(clan.size(), "member") + " / " + clan.getMaxMembers(), NamedTextColor.GRAY));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("Members:", NamedTextColor.GOLD));

        for (var entry : clan.getMembers().entrySet()) {
            UUID memberUuid = entry.getKey();
            Clan.ClanRank rank = entry.getValue();

            Player p = Bukkit.getPlayer(memberUuid);
            String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(memberUuid).getName();
            boolean online = p != null;

            String status = online ? "§a✓ Online" : "§7✗ Offline";
            NamedTextColor rankColor = SocialUtils.getRankColor(rank.name());

            player.sendMessage(Component.text("  • ", NamedTextColor.GRAY)
                    .append(Component.text(name, SocialUtils.getOnlineColor(online)))
                    .append(Component.text(" [" + rank.getDisplayName() + "] ", rankColor))
                    .append(Component.text(status, online ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
        }
    }

    private void sendHelp(@NotNull Player player) {
        player.sendMessage(SocialUtils.createHeader("Clan Commands"));
        player.sendMessage(Component.text("/clan create <name> <tag>", NamedTextColor.WHITE)
                .append(Component.text(" - Create a clan", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan invite <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Invite player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan accept <name>", NamedTextColor.WHITE)
                .append(Component.text(" - Accept invite", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan leave", NamedTextColor.WHITE)
                .append(Component.text(" - Leave clan", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan kick <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Kick player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan promote <player>", NamedTextColor.WHITE)
                .append(Component.text(" - Promote member", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan list", NamedTextColor.WHITE)
                .append(Component.text(" - List members", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan chat <message>", NamedTextColor.WHITE)
                .append(Component.text(" - Send clan chat", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/clan disband", NamedTextColor.WHITE)
                .append(Component.text(" - Disband clan", NamedTextColor.GRAY)));
    }

    private void sendMessage(@NotNull Player player, @NotNull String key, @NotNull String... replacements) {
        String message = plugin.getSocialConfig().getMessagesConfig().getMessage(key);

        if (replacements.length >= 1) message = message.replace("{name}", replacements[0]);
        if (replacements.length >= 2) message = message.replace("{tag}", replacements[1]);
        if (replacements.length >= 3) message = message.replace("{player}", replacements[2]);

        player.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() + message
        ));
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "deny", "leave",
                    "kick", "promote", "demote", "list", "chat", "disband", "info");
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("invite") || subCommand.equals("kick") ||
                    subCommand.equals("promote") || subCommand.equals("demote")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}