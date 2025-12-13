package com.yourserver.social.manager;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.database.RedisPartyRepository;
import com.yourserver.social.messaging.SocialMessenger;
import com.yourserver.social.model.Party;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages party system with Redis storage and cross-server messaging.
 */
public class PartyManager {

    private final SocialPlugin plugin;
    private final RedisPartyRepository repository;
    private final SocialMessenger messenger;

    // Local cache of parties (synced via Redis Pub/Sub)
    private final Map<String, Party> partyCache;

    // Pending invites (player UUID -> party ID)
    private final Map<UUID, String> pendingInvites;

    public PartyManager(@NotNull SocialPlugin plugin,
                        @NotNull RedisPartyRepository repository,
                        @NotNull SocialMessenger messenger) {
        this.plugin = plugin;
        this.repository = repository;
        this.messenger = messenger;
        this.partyCache = new ConcurrentHashMap<>();
        this.pendingInvites = new ConcurrentHashMap<>();

        // Register message handlers
        setupMessageHandlers();
    }

    /**
     * Sets up cross-server message handlers.
     */
    private void setupMessageHandlers() {
        messenger.onMessage(SocialMessenger.MessageType.PARTY, msg -> {
            String action = msg.data.get("action");

            switch (action) {
                case "invite" -> handleInviteMessage(msg);
                case "join" -> handleJoinMessage(msg);
                case "leave" -> handleLeaveMessage(msg);
                case "chat" -> handleChatMessage(msg);
                case "warp" -> handleWarpMessage(msg);
            }
        });
    }

    // ===== PARTY CREATION =====

    /**
     * Creates a new party.
     */
    @NotNull
    public PartyResult createParty(@NotNull Player leader) {
        UUID leaderUuid = leader.getUniqueId();

        // Check if already in a party
        if (repository.isInParty(leaderUuid)) {
            return PartyResult.ALREADY_IN_PARTY;
        }

        // Create party
        int maxMembers = 8; // TODO: Get from config
        Party party = Party.create(leaderUuid, maxMembers);

        // Save to Redis
        repository.save(party);
        partyCache.put(party.getId(), party);

        return PartyResult.SUCCESS;
    }

    /**
     * Disbands a party.
     */
    @NotNull
    public PartyResult disbandParty(@NotNull Player disbander) {
        Party party = getPlayerParty(disbander.getUniqueId());

        if (party == null) {
            return PartyResult.NOT_IN_PARTY;
        }

        if (!party.isLeader(disbander.getUniqueId())) {
            return PartyResult.NOT_LEADER;
        }

        // Remove from Redis
        repository.delete(party.getId());
        partyCache.remove(party.getId());

        // Notify all members
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                "<red>Party disbanded!"
                ));
            }
        }

        return PartyResult.SUCCESS;
    }

    // ===== INVITES =====

    /**
     * Invites a player to the party.
     */
    @NotNull
    public PartyResult invitePlayer(@NotNull Player inviter, @NotNull UUID targetUuid) {
        Party party = getPlayerParty(inviter.getUniqueId());

        if (party == null) {
            return PartyResult.NOT_IN_PARTY;
        }

        if (!party.isLeader(inviter.getUniqueId())) {
            return PartyResult.NOT_LEADER; // TODO: Check config for member invites
        }

        if (party.isFull()) {
            return PartyResult.PARTY_FULL;
        }

        if (repository.isInParty(targetUuid)) {
            return PartyResult.TARGET_IN_PARTY;
        }

        // Send invite
        pendingInvites.put(targetUuid, party.getId());

        // Remove invite after 60 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingInvites.remove(targetUuid, party.getId());
        }, 1200L);

        // Send cross-server message
        messenger.sendPartyInvite(party.getId(), inviter.getUniqueId(), targetUuid);

        return PartyResult.SUCCESS;
    }

    /**
     * Accepts a party invite.
     */
    @NotNull
    public PartyResult acceptInvite(@NotNull Player accepter) {
        UUID accepterUuid = accepter.getUniqueId();
        String partyId = pendingInvites.remove(accepterUuid);

        if (partyId == null) {
            return PartyResult.NO_PENDING_INVITE;
        }

        Party party = repository.load(partyId);

        if (party == null) {
            return PartyResult.PARTY_NOT_FOUND;
        }

        if (party.isFull()) {
            return PartyResult.PARTY_FULL;
        }

        // Add member
        party.addMember(accepterUuid);
        repository.save(party);
        partyCache.put(party.getId(), party);

        // Send cross-server message
        messenger.joinParty(partyId, accepterUuid, accepter.getName());

        return PartyResult.SUCCESS;
    }

    /**
     * Denies a party invite.
     */
    public void denyInvite(@NotNull UUID player) {
        pendingInvites.remove(player);
    }

    // ===== LEAVE/KICK =====

    /**
     * Player leaves their party.
     */
    @NotNull
    public PartyResult leaveParty(@NotNull Player leaver) {
        UUID leaverUuid = leaver.getUniqueId();
        Party party = getPlayerParty(leaverUuid);

        if (party == null) {
            return PartyResult.NOT_IN_PARTY;
        }

        // If leader leaves, disband or transfer leadership
        if (party.isLeader(leaverUuid)) {
            if (party.size() > 1) {
                // Transfer leadership to next member
                UUID newLeader = party.getMembers().stream()
                        .filter(uuid -> !uuid.equals(leaverUuid))
                        .findFirst()
                        .orElse(null);

                if (newLeader != null) {
                    party.transferLeadership(newLeader);
                }
            } else {
                // Last member, disband party
                repository.delete(party.getId());
                partyCache.remove(party.getId());
                return PartyResult.SUCCESS;
            }
        }

        // Remove member
        party.removeMember(leaverUuid);
        repository.save(party);
        repository.removePlayerIndex(leaverUuid);

        // Update cache
        if (party.size() == 0) {
            repository.delete(party.getId());
            partyCache.remove(party.getId());
        } else {
            partyCache.put(party.getId(), party);
        }

        // Send cross-server message
        messenger.leaveParty(party.getId(), leaverUuid, leaver.getName());

        return PartyResult.SUCCESS;
    }

    /**
     * Kicks a player from the party.
     */
    @NotNull
    public PartyResult kickPlayer(@NotNull Player kicker, @NotNull UUID targetUuid) {
        Party party = getPlayerParty(kicker.getUniqueId());

        if (party == null) {
            return PartyResult.NOT_IN_PARTY;
        }

        if (!party.isLeader(kicker.getUniqueId())) {
            return PartyResult.NOT_LEADER;
        }

        if (!party.hasMember(targetUuid)) {
            return PartyResult.PLAYER_NOT_IN_PARTY;
        }

        if (party.isLeader(targetUuid)) {
            return PartyResult.CANNOT_KICK_LEADER;
        }

        // Remove member
        party.removeMember(targetUuid);
        repository.save(party);
        repository.removePlayerIndex(targetUuid);
        partyCache.put(party.getId(), party);

        // Notify kicked player
        Player kicked = Bukkit.getPlayer(targetUuid);
        if (kicked != null) {
            kicked.sendMessage(plugin.getMiniMessage().deserialize(
                    plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                            "<red>You were kicked from the party!"
            ));
        }

        return PartyResult.SUCCESS;
    }

    // ===== CHAT =====

    /**
     * Sends a message to the party.
     */
    public void sendPartyChat(@NotNull Player sender, @NotNull String message) {
        Party party = getPlayerParty(sender.getUniqueId());

        if (party == null) {
            return;
        }

        // Send cross-server message
        messenger.sendPartyChat(party.getId(), sender.getUniqueId(), sender.getName(), message);
    }

    // ===== QUERIES =====

    /**
     * Gets a player's party.
     */
    @Nullable
    public Party getPlayerParty(@NotNull UUID playerUuid) {
        // Check cache first
        for (Party party : partyCache.values()) {
            if (party.hasMember(playerUuid)) {
                return party;
            }
        }

        // Load from Redis
        Party party = repository.findByPlayer(playerUuid);
        if (party != null) {
            partyCache.put(party.getId(), party);
        }
        return party;
    }

    /**
     * Gets a party by ID.
     */
    @Nullable
    public Party getParty(@NotNull String partyId) {
        Party cached = partyCache.get(partyId);
        if (cached != null) {
            return cached;
        }

        Party party = repository.load(partyId);
        if (party != null) {
            partyCache.put(partyId, party);
        }
        return party;
    }

    /**
     * Checks if a player is in a party.
     */
    public boolean isInParty(@NotNull UUID playerUuid) {
        return repository.isInParty(playerUuid);
    }

    // ===== MESSAGE HANDLERS =====

    private void handleInviteMessage(@NotNull SocialMessenger.SocialMessage msg) {
        Player target = Bukkit.getPlayer(msg.to);
        if (target == null) return;

        Player inviter = Bukkit.getPlayer(msg.from);
        String inviterName = inviter != null ? inviter.getName() : "Someone";

        // Show notification
        target.sendMessage(plugin.getMiniMessage().deserialize(
                plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                        "<yellow>" + inviterName + " <green>invited you to their party! " +
                        "<click:run_command:/party accept>[Accept]</click> " +
                        "<click:run_command:/party deny>[Deny]</click>"
        ));
    }

    private void handleJoinMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String partyId = msg.data.get("partyId");
        String playerName = msg.data.get("playerName");

        Party party = getParty(partyId);
        if (party == null) return;

        // Notify all members
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && !member.equals(msg.from)) {
                p.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                "<green>" + playerName + " joined the party!"
                ));
            }
        }
    }

    private void handleLeaveMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String partyId = msg.data.get("partyId");
        String playerName = msg.data.get("playerName");

        Party party = getParty(partyId);
        if (party == null) return;

        // Notify remaining members
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getSocialConfig().getMessagesConfig().getPrefix() +
                                "<red>" + playerName + " left the party"
                ));
            }
        }
    }

    private void handleChatMessage(@NotNull SocialMessenger.SocialMessage msg) {
        String partyId = msg.data.get("partyId");
        String senderName = msg.data.get("senderName");
        String message = msg.data.get("message");

        Party party = getParty(partyId);
        if (party == null) return;

        // Send to all party members on this server
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(plugin.getMiniMessage().deserialize(
                        "<dark_gray>[<green>PARTY<dark_gray>] <white>" + senderName +
                                "<dark_gray>: <white>" + message
                ));
            }
        }
    }

    private void handleWarpMessage(@NotNull SocialMessenger.SocialMessage msg) {
        // TODO: Implement cross-server warp via Velocity
        // This will be handled by the Velocity plugin
    }

    // ===== LIFECYCLE =====

    public void shutdown() {
        partyCache.clear();
        pendingInvites.clear();
    }

    // ===== RESULT ENUM =====

    public enum PartyResult {
        SUCCESS,
        NOT_IN_PARTY,
        ALREADY_IN_PARTY,
        NOT_LEADER,
        PARTY_FULL,
        PARTY_NOT_FOUND,
        NO_PENDING_INVITE,
        TARGET_IN_PARTY,
        PLAYER_NOT_IN_PARTY,
        CANNOT_KICK_LEADER
    }
}