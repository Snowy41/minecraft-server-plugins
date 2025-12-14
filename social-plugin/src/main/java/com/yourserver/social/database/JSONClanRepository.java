package com.yourserver.social.database;

import com.google.gson.reflect.TypeToken;
import com.yourserver.social.model.Clan;
import com.yourserver.social.storage.JSONStorage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * JSON-based clan storage.
 */
public class JSONClanRepository {

    private final JSONStorage<ClanData> clanStorage;
    private final JSONStorage<InviteData> inviteStorage;
    private final Logger logger;

    public JSONClanRepository(@NotNull File dataFolder, @NotNull Logger logger) {
        this.logger = logger;

        this.clanStorage = new JSONStorage<>(
                dataFolder,
                "clans.json",
                new TypeToken<ClanData>(){},
                new ClanData(),
                logger
        );

        this.inviteStorage = new JSONStorage<>(
                dataFolder,
                "clan-invites.json",
                new TypeToken<InviteData>(){},
                new InviteData(),
                logger
        );
    }

    // ===== CLANS =====

    @NotNull
    public CompletableFuture<Optional<Clan>> getClan(@NotNull String clanId) {
        return clanStorage.load().thenApply(data ->
                Optional.ofNullable(data.clans.get(clanId))
        );
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByName(@NotNull String name) {
        return clanStorage.load().thenApply(data ->
                data.clans.values().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(name))
                        .findFirst()
        );
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByTag(@NotNull String tag) {
        return clanStorage.load().thenApply(data ->
                data.clans.values().stream()
                        .filter(c -> c.getTag().equalsIgnoreCase(tag))
                        .findFirst()
        );
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getPlayerClan(@NotNull UUID playerUuid) {
        return clanStorage.load().thenApply(data ->
                data.clans.values().stream()
                        .filter(c -> c.hasMember(playerUuid))
                        .findFirst()
        );
    }

    @NotNull
    public CompletableFuture<Void> createClan(@NotNull Clan clan) {
        return clanStorage.load().thenCompose(data -> {
            data.clans.put(clan.getId(), clan);
            return clanStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Void> deleteClan(@NotNull String clanId) {
        return clanStorage.load().thenCompose(data -> {
            data.clans.remove(clanId);
            return clanStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Boolean> clanNameExists(@NotNull String name) {
        return clanStorage.load().thenApply(data ->
                data.clans.values().stream()
                        .anyMatch(c -> c.getName().equalsIgnoreCase(name))
        );
    }

    @NotNull
    public CompletableFuture<Boolean> clanTagExists(@NotNull String tag) {
        return clanStorage.load().thenApply(data ->
                data.clans.values().stream()
                        .anyMatch(c -> c.getTag().equalsIgnoreCase(tag))
        );
    }

    // ===== CLAN MEMBERS =====

    @NotNull
    public CompletableFuture<Void> addClanMember(@NotNull String clanId, @NotNull UUID playerUuid,
                                                 @NotNull Clan.ClanRank rank) {
        return clanStorage.load().thenCompose(data -> {
            Clan clan = data.clans.get(clanId);
            if (clan != null) {
                clan.addMember(playerUuid, rank);
            }
            return clanStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Void> removeClanMember(@NotNull String clanId, @NotNull UUID playerUuid) {
        return clanStorage.load().thenCompose(data -> {
            Clan clan = data.clans.get(clanId);
            if (clan != null) {
                clan.removeMember(playerUuid);
            }
            return clanStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Void> updateMemberRank(@NotNull String clanId, @NotNull UUID playerUuid,
                                                    @NotNull Clan.ClanRank rank) {
        return clanStorage.load().thenCompose(data -> {
            Clan clan = data.clans.get(clanId);
            if (clan != null) {
                clan.setRank(playerUuid, rank);
            }
            return clanStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Integer> getClanMemberCount(@NotNull String clanId) {
        return clanStorage.load().thenApply(data -> {
            Clan clan = data.clans.get(clanId);
            return clan != null ? clan.size() : 0;
        });
    }

    // ===== CLAN INVITES =====

    @NotNull
    public CompletableFuture<Void> createInvite(@NotNull String clanId, @NotNull UUID from,
                                                @NotNull UUID to, int expireSeconds) {
        return inviteStorage.load().thenCompose(data -> {
            ClanInvite invite = new ClanInvite(
                    clanId, from, to,
                    Instant.now(),
                    Instant.now().plusSeconds(expireSeconds)
            );

            data.invites.computeIfAbsent(to, k -> new ArrayList<>()).add(invite);
            return inviteStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<List<ClanInvite>> getPendingInvites(@NotNull UUID playerUuid) {
        return inviteStorage.load().thenApply(data -> {
            List<ClanInvite> all = data.invites.getOrDefault(playerUuid, new ArrayList<>());

            return all.stream()
                    .filter(i -> !i.isExpired())
                    .collect(Collectors.toList());
        });
    }

    @NotNull
    public CompletableFuture<Void> deleteInvite(@NotNull String clanId, @NotNull UUID to) {
        return inviteStorage.load().thenCompose(data -> {
            List<ClanInvite> invites = data.invites.get(to);
            if (invites != null) {
                invites.removeIf(i -> i.clanId.equals(clanId));
            }
            return inviteStorage.save(data);
        });
    }

    @NotNull
    public CompletableFuture<Integer> deleteExpiredInvites() {
        return inviteStorage.load().thenCompose(data -> {
            int count = 0;

            for (List<ClanInvite> invites : data.invites.values()) {
                int before = invites.size();
                invites.removeIf(ClanInvite::isExpired);
                count += (before - invites.size());
            }

            int finalCount = count;
            return inviteStorage.save(data).thenApply(v -> finalCount);
        });
    }

    // ===== DATA MODELS =====

    public static class ClanData {
        public Map<String, Clan> clans = new HashMap<>();
    }

    public static class InviteData {
        public Map<UUID, List<ClanInvite>> invites = new HashMap<>();
    }

    public static class ClanInvite {
        public String clanId;
        public UUID fromUuid;
        public UUID toUuid;
        public Instant createdAt;
        public Instant expiresAt;

        public ClanInvite(String clanId, UUID fromUuid, UUID toUuid, Instant createdAt, Instant expiresAt) {
            this.clanId = clanId;
            this.fromUuid = fromUuid;
            this.toUuid = toUuid;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}