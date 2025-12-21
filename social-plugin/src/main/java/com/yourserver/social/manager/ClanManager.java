package com.yourserver.social.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yourserver.social.SocialPlugin;
import com.yourserver.social.database.MySQLClanRepository;
import com.yourserver.social.messaging.SocialMessenger;
import com.yourserver.social.model.Clan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages clan system with MySQL storage and cross-server messaging.
 * CloudNet 4.0.0 + MySQL Edition
 */
public class ClanManager {

    private final SocialPlugin plugin;
    private final MySQLClanRepository repository;
    private final SocialMessenger messenger;

    // Cache clans (10 minutes)
    private final Cache<String, Clan> clanCache;
    private final Cache<UUID, String> playerClanCache;

    public ClanManager(@NotNull SocialPlugin plugin,
                       @NotNull MySQLClanRepository repository,
                       @NotNull SocialMessenger messenger) {
        this.plugin = plugin;
        this.repository = repository;
        this.messenger = messenger;

        // Initialize caches
        this.clanCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();

        this.playerClanCache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();

        // Register message handlers
        setupMessageHandlers();

        // Schedule expired invite cleanup (every 10 minutes)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            repository.deleteExpiredInvites().thenAccept(count -> {
                if (count > 0) {
                    plugin.getLogger().info("Cleaned up " + count + " expired clan invites");
                }
            });
        }, 12000L, 12000L); // 10 minutes
    }

    /**
     * Sets up cross-server message handlers.
     */
    private void setupMessageHandlers() {
        messenger.onMessage(SocialMessenger.MessageType.CLAN, msg -> {
            String action = msg.data.get("action");

            switch (action) {
                case "invite" -> handleInviteMessage(msg);
                case "join" -> handleJoinMessage(msg);
                case "leave" -> handleLeaveMessage(msg);
                case "chat" -> handleChatMessage(msg);
            }
        });
    }

    // ===== CLAN CREATION =====

    /**
     * Creates a new clan.
     */
    @NotNull
    public CompletableFuture<ClanResult> createClan(@NotNull Player owner, @NotNull String name,
                                                    @NotNull String tag) {
        UUID ownerUuid = owner.getUniqueId();

        // Validation
        int minName = plugin.getSocialConfig().getClansConfig().getMinNameLength();
        int maxName = plugin.getSocialConfig().getClansConfig().getMaxNameLength();
        int minTag = plugin.getSocialConfig().getClansConfig().getMinTagLength();
        int maxTag = plugin.getSocialConfig().getClansConfig().getMaxTagLength();

        if (name.length() < minName || name.length() > maxName) {
            return CompletableFuture.completedFuture(ClanResult.INVALID_NAME);
        }

        if (tag.length() < minTag || tag.length() > maxTag) {
            return CompletableFuture.completedFuture(ClanResult.INVALID_TAG);
        }

        // Check if player is already in a clan
        return repository.getPlayerClan(ownerUuid).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.completedFuture(ClanResult.ALREADY_IN_CLAN);
            }

            // Check if name is taken
            return repository.clanNameExists(name).thenCompose(nameTaken -> {
                if (nameTaken) {
                    return CompletableFuture.completedFuture(ClanResult.NAME_TAKEN);
                }

                // Check if tag is taken
                return repository.clanTagExists(tag).thenCompose(tagTaken -> {
                    if (tagTaken) {
                        return CompletableFuture.completedFuture(ClanResult.TAG_TAKEN);
                    }

                    // Create clan
                    int maxMembers = plugin.getSocialConfig().getClansConfig().getMaxMembers();
                    Clan clan = Clan.create(name, tag, ownerUuid, maxMembers);

                    return repository.createClan(clan).thenApply(v -> {
                        // Cache
                        clanCache.put(clan.getId(), clan);
                        playerClanCache.put(ownerUuid, clan.getId());

                        plugin.getLogger().info("Clan created: " + name + " [" + tag + "] by " + owner.getName());
                        return ClanResult.SUCCESS;
                    });
                });
            });
        });
    }

    /**
     * Disbands a clan.
     */
    @NotNull
    public CompletableFuture<ClanResult> disbandClan(@NotNull Player disbander) {
        UUID disbanderUuid = disbander.getUniqueId();

        return getPlayerClan(disbanderUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.NOT_IN_CLAN);
            }

            Clan clan = clanOpt.get();

            if (!clan.isOwner(disbanderUuid)) {
                return CompletableFuture.completedFuture(ClanResult.NOT_OWNER);
            }

            return repository.deleteClan(clan.getId()).thenApply(v -> {
                // Clear caches
                clanCache.invalidate(clan.getId());
                for (UUID member : clan.getMembers().keySet()) {
                    playerClanCache.invalidate(member);
                }

                // Notify all members
                notifyClanMembers(clan, "§cClan disbanded!");

                plugin.getLogger().info("Clan disbanded: " + clan.getName() + " by " + disbander.getName());
                return ClanResult.SUCCESS;
            });
        });
    }

    // ===== INVITES =====

    /**
     * Invites a player to the clan.
     */
    @NotNull
    public CompletableFuture<ClanResult> invitePlayer(@NotNull Player inviter, @NotNull UUID targetUuid,
                                                      @NotNull String targetName) {
        UUID inviterUuid = inviter.getUniqueId();

        return getPlayerClan(inviterUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.NOT_IN_CLAN);
            }

            Clan clan = clanOpt.get();

            if (!clan.hasPermission(inviterUuid, "invite")) {
                return CompletableFuture.completedFuture(ClanResult.NO_PERMISSION);
            }

            if (clan.isFull()) {
                return CompletableFuture.completedFuture(ClanResult.CLAN_FULL);
            }

            // Check if target is already in a clan
            return repository.getPlayerClan(targetUuid).thenCompose(targetClanOpt -> {
                if (targetClanOpt.isPresent()) {
                    return CompletableFuture.completedFuture(ClanResult.TARGET_IN_CLAN);
                }

                // Create invite
                int expireSeconds = 300; // 5 minutes
                return repository.createInvite(clan.getId(), inviterUuid, targetUuid, expireSeconds)
                        .thenApply(v -> {
                            // Send cross-server message
                            messenger.sendClanInvite(clan.getId(), inviterUuid, targetUuid);

                            plugin.getLogger().fine("Clan invite sent: " + inviter.getName() + " invited " + targetName + " to " + clan.getName());
                            return ClanResult.SUCCESS;
                        });
            });
        });
    }

    /**
     * Accepts a clan invite.
     */
    @NotNull
    public CompletableFuture<ClanResult> acceptInvite(@NotNull Player accepter, @NotNull String clanId) {
        UUID accepterUuid = accepter.getUniqueId();

        return repository.getClan(clanId).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.CLAN_NOT_FOUND);
            }

            Clan clan = clanOpt.get();

            if (clan.isFull()) {
                return CompletableFuture.completedFuture(ClanResult.CLAN_FULL);
            }

            // Add member
            return repository.addClanMember(clanId, accepterUuid, Clan.ClanRank.MEMBER)
                    .thenCompose(v -> repository.deleteInvite(clanId, accepterUuid))
                    .thenApply(v -> {
                        // Update cache
                        clan.addMember(accepterUuid, Clan.ClanRank.MEMBER);
                        clanCache.put(clanId, clan);
                        playerClanCache.put(accepterUuid, clanId);

                        // Send cross-server message
                        messenger.joinClan(clanId, accepterUuid, accepter.getName());

                        plugin.getLogger().info("Player joined clan: " + accepter.getName() + " joined " + clan.getName());
                        return ClanResult.SUCCESS;
                    });
        });
    }

    /**
     * Denies a clan invite.
     */
    @NotNull
    public CompletableFuture<Void> denyInvite(@NotNull UUID player, @NotNull String clanId) {
        return repository.deleteInvite(clanId, player);
    }

    // ===== LEAVE/KICK =====

    /**
     * Player leaves their clan.
     */
    @NotNull
    public CompletableFuture<ClanResult> leaveClan(@NotNull Player leaver) {
        UUID leaverUuid = leaver.getUniqueId();

        return getPlayerClan(leaverUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.NOT_IN_CLAN);
            }

            Clan clan = clanOpt.get();

            if (clan.isOwner(leaverUuid)) {
                return CompletableFuture.completedFuture(ClanResult.OWNER_CANNOT_LEAVE);
            }

            return repository.removeClanMember(clan.getId(), leaverUuid).thenApply(v -> {
                // Update caches
                clan.removeMember(leaverUuid);
                clanCache.put(clan.getId(), clan);
                playerClanCache.invalidate(leaverUuid);

                plugin.getLogger().info("Player left clan: " + leaver.getName() + " left " + clan.getName());
                return ClanResult.SUCCESS;
            });
        });
    }

    /**
     * Kicks a player from the clan.
     */
    @NotNull
    public CompletableFuture<ClanResult> kickPlayer(@NotNull Player kicker, @NotNull UUID targetUuid) {
        UUID kickerUuid = kicker.getUniqueId();

        return getPlayerClan(kickerUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.NOT_IN_CLAN);
            }

            Clan clan = clanOpt.get();

            if (!clan.hasPermission(kickerUuid, "kick")) {
                return CompletableFuture.completedFuture(ClanResult.NO_PERMISSION);
            }

            if (!clan.hasMember(targetUuid)) {
                return CompletableFuture.completedFuture(ClanResult.PLAYER_NOT_IN_CLAN);
            }

            if (clan.isOwner(targetUuid)) {
                return CompletableFuture.completedFuture(ClanResult.CANNOT_KICK_OWNER);
            }

            return repository.removeClanMember(clan.getId(), targetUuid).thenApply(v -> {
                // Update caches
                clan.removeMember(targetUuid);
                clanCache.put(clan.getId(), clan);
                playerClanCache.invalidate(targetUuid);

                // Notify kicked player
                Player kicked = Bukkit.getPlayer(targetUuid);
                if (kicked != null) {
                    kicked.sendMessage("§cYou were kicked from the clan!");
                }

                plugin.getLogger().info("Player kicked from clan: " + targetUuid + " kicked from " + clan.getName());
                return ClanResult.SUCCESS;
            });
        });
    }

    // ===== RANK MANAGEMENT =====

    /**
     * Promotes a member.
     */
    @NotNull
    public CompletableFuture<ClanResult> promoteMember(@NotNull Player promoter, @NotNull UUID targetUuid,
                                                       @NotNull Clan.ClanRank newRank) {
        UUID promoterUuid = promoter.getUniqueId();

        return getPlayerClan(promoterUuid).thenCompose(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return CompletableFuture.completedFuture(ClanResult.NOT_IN_CLAN);
            }

            Clan clan = clanOpt.get();

            if (!clan.hasPermission(promoterUuid, "promote")) {
                return CompletableFuture.completedFuture(ClanResult.NO_PERMISSION);
            }

            if (!clan.hasMember(targetUuid)) {
                return CompletableFuture.completedFuture(ClanResult.PLAYER_NOT_IN_CLAN);
            }

            return repository.updateMemberRank(clan.getId(), targetUuid, newRank).thenApply(v -> {
                // Update cache
                clan.setRank(targetUuid, newRank);
                clanCache.put(clan.getId(), clan);

                plugin.getLogger().info("Member promoted: " + targetUuid + " to " + newRank + " in " + clan.getName());
                return ClanResult.SUCCESS;
            });
        });
    }

    // ===== CHAT =====

    /**
     * Sends a message to the clan chat.
     */
    public void sendClanChat(@NotNull Player sender, @NotNull String message) {
        getPlayerClan(sender.getUniqueId()).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) {
                return;
            }

            Clan clan = clanOpt.get();

            if (!clan.hasPermission(sender.getUniqueId(), "chat")) {
                return;
            }

            // Send cross-server message
            messenger.sendClanChat(clan.getId(), sender.getUniqueId(), sender.getName(), message);
        });
    }

    // ===== QUERIES =====

    /**
     * Gets a player's clan (cached).
     */
    @NotNull
    public CompletableFuture<java.util.Optional<Clan>> getPlayerClan(@NotNull UUID playerUuid) {
        String cachedClanId = playerClanCache.getIfPresent(playerUuid);
        if (cachedClanId != null) {
            Clan cachedClan = clanCache.getIfPresent(cachedClanId);
            if (cachedClan != null) {
                return CompletableFuture.completedFuture(java.util.Optional.of(cachedClan));
            }
        }

        return repository.getPlayerClan(playerUuid).thenApply(clanOpt -> {
            clanOpt.ifPresent(clan -> {
                clanCache.put(clan.getId(), clan);
                playerClanCache.put(playerUuid, clan.getId());
            });
            return clanOpt;
        });
    }

    /**
     * Gets a clan by ID.
     */
    @NotNull
    public CompletableFuture<java.util.Optional<Clan>> getClan(@NotNull String clanId) {
        Clan cached = clanCache.getIfPresent(clanId);
        if (cached != null) {
            return CompletableFuture.completedFuture(java.util.Optional.of(cached));
        }

        return repository.getClan(clanId).thenApply(clanOpt -> {
            clanOpt.ifPresent(clan -> clanCache.put(clanId, clan));
            return clanOpt;
        });
    }

    /**
     * Gets a clan by name (cached).
     */
    @NotNull
    public CompletableFuture<java.util.Optional<Clan>> getClanByName(@NotNull String name) {
        // Check cache first
        Clan cached = clanCache.asMap().values().stream()
                .filter(clan -> clan.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        if (cached != null) {
            return CompletableFuture.completedFuture(java.util.Optional.of(cached));
        }

        // Load from repository
        return repository.getClanByName(name).thenApply(clanOpt -> {
            clanOpt.ifPresent(clan -> clanCache.put(clan.getId(), clan));
            return clanOpt;
        });
    }

    /**
     * Gets a clan by tag (cached).
     */
    @NotNull
    public CompletableFuture<java.util.Optional<Clan>> getClanByTag(@NotNull String tag) {
        // Check cache first
        Clan cached = clanCache.asMap().values().stream()
                .filter(clan -> clan.getTag().equalsIgnoreCase(tag))
                .findFirst()
                .orElse(null);

        if (cached != null) {
            return CompletableFuture.completedFuture(java.util.Optional.of(cached));
        }

        // Load from repository
        return repository.getClanByTag(tag).thenApply(clanOpt -> {
            clanOpt.ifPresent(clan -> clanCache.put(clan.getId(), clan));
            return clanOpt;
        });
    }

    /**
     * Gets pending clan invites.
     */
    @NotNull
    public CompletableFuture<List<MySQLClanRepository.ClanInvite>> getPendingInvites(@NotNull UUID playerUuid) {
        return repository.getPendingInvites(playerUuid);
    }

    // ===== MESSAGE HANDLERS =====

    private void handleInviteMessage(@NotNull SocialMessenger.SocialMessage msg) {
        Player target = Bukkit.getPlayer(msg.to);
        if (target == null) return;

        String clanName = msg.data.get("clanName");

        target.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                        "<yellow>You've been invited to clan <white>" + clanName +
                        "<yellow>! <click:run_command:/clan accept " + clanName + ">[Accept]</click>"
        ));
    }

    private void handleJoinMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String clanId = msg.data.get("clanId");
        String playerName = msg.data.get("playerName");

        getClan(clanId).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) return;

            Clan clan = clanOpt.get();
            notifyClanMembers(clan, "§a" + playerName + " joined the clan!");
        });
    }

    private void handleLeaveMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String clanId = msg.data.get("clanId");
        String playerName = msg.data.get("playerName");

        getClan(clanId).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) return;

            Clan clan = clanOpt.get();
            notifyClanMembers(clan, "§e" + playerName + " left the clan");
        });
    }

    private void handleChatMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String clanId = msg.data.get("clanId");
        String senderName = msg.data.get("senderName");
        String message = msg.data.get("message");

        getClan(clanId).thenAccept(clanOpt -> {
            if (clanOpt.isEmpty()) return;

            Clan clan = clanOpt.get();

            for (UUID member : clan.getMembers().keySet()) {
                Player p = Bukkit.getPlayer(member);
                if (p != null) {
                    p.sendMessage("§8[§2CLAN§8] §f" + senderName + "§8: §f" + message);
                }
            }
        });
    }

    // ===== UTILITY =====

    private void notifyClanMembers(@NotNull Clan clan, @NotNull String message) {
        for (UUID member : clan.getMembers().keySet()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    // ===== LIFECYCLE =====

    public void shutdown() {
        clanCache.invalidateAll();
        playerClanCache.invalidateAll();
        plugin.getLogger().info("ClanManager shut down - caches cleared");
    }

    // ===== RESULT ENUM =====

    public enum ClanResult {
        SUCCESS,
        NOT_IN_CLAN,
        ALREADY_IN_CLAN,
        NOT_OWNER,
        NO_PERMISSION,
        CLAN_NOT_FOUND,
        CLAN_FULL,
        TARGET_IN_CLAN,
        PLAYER_NOT_IN_CLAN,
        CANNOT_KICK_OWNER,
        OWNER_CANNOT_LEAVE,
        NAME_TAKEN,
        TAG_TAKEN,
        INVALID_NAME,
        INVALID_TAG
    }
}