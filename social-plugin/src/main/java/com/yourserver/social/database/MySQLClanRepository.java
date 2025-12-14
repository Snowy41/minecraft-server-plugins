package com.yourserver.social.database;

import com.yourserver.core.database.DatabaseManager;
import com.yourserver.social.model.Clan;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation for clan storage.
 * Uses CorePlugin's DatabaseManager.
 */
public class MySQLClanRepository {

    private final DatabaseManager databaseManager;

    public MySQLClanRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ===== CLANS =====

    /**
     * Gets a clan by ID.
     */
    @NotNull
    public CompletableFuture<Optional<Clan>> getClan(@NotNull String clanId) {
        String sql = """
            SELECT c.id, c.name, c.tag, c.owner_uuid, c.created_at, c.max_members
            FROM clans c
            WHERE c.id = ?
            LIMIT 1
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    Clan clan = mapClan(rs);

                    // Load members
                    return loadClanMembers(clan).join();
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan", e);
            }
        }, clanId);
    }

    /**
     * Gets a clan by name.
     */
    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByName(@NotNull String name) {
        String sql = """
            SELECT c.id, c.name, c.tag, c.owner_uuid, c.created_at, c.max_members
            FROM clans c
            WHERE LOWER(c.name) = LOWER(?)
            LIMIT 1
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    Clan clan = mapClan(rs);
                    return loadClanMembers(clan).join();
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan", e);
            }
        }, name);
    }

    /**
     * Gets a clan by tag.
     */
    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByTag(@NotNull String tag) {
        String sql = """
            SELECT c.id, c.name, c.tag, c.owner_uuid, c.created_at, c.max_members
            FROM clans c
            WHERE LOWER(c.tag) = LOWER(?)
            LIMIT 1
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    Clan clan = mapClan(rs);
                    return loadClanMembers(clan).join();
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan", e);
            }
        }, tag);
    }

    /**
     * Gets a player's clan.
     */
    @NotNull
    public CompletableFuture<Optional<Clan>> getPlayerClan(@NotNull UUID playerUuid) {
        String sql = """
            SELECT c.id, c.name, c.tag, c.owner_uuid, c.created_at, c.max_members
            FROM clans c
            JOIN clan_members cm ON c.id = cm.clan_id
            WHERE cm.player_uuid = ?
            LIMIT 1
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    Clan clan = mapClan(rs);
                    return loadClanMembers(clan).join();
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan", e);
            }
        }, playerUuid.toString());
    }

    /**
     * Creates a new clan.
     */
    @NotNull
    public CompletableFuture<Void> createClan(@NotNull Clan clan) {
        String sql = """
            INSERT INTO clans (id, name, tag, owner_uuid, created_at, max_members)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        return databaseManager.executeUpdate(
                sql,
                clan.getId(),
                clan.getName(),
                clan.getTag(),
                clan.getOwner().toString(),
                Timestamp.from(clan.getCreatedAt()),
                clan.getMaxMembers()
        ).thenCompose(v -> {
            // Add owner as member
            return addClanMember(clan.getId(), clan.getOwner(), Clan.ClanRank.OWNER);
        });
    }

    /**
     * Deletes a clan.
     */
    @NotNull
    public CompletableFuture<Void> deleteClan(@NotNull String clanId) {
        String sql = "DELETE FROM clans WHERE id = ?";
        return databaseManager.executeUpdate(sql, clanId).thenApply(v -> null);
    }

    /**
     * Checks if clan name exists.
     */
    @NotNull
    public CompletableFuture<Boolean> clanNameExists(@NotNull String name) {
        String sql = "SELECT 1 FROM clans WHERE LOWER(name) = LOWER(?) LIMIT 1";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                return rs.next();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check clan name", e);
            }
        }, name);
    }

    /**
     * Checks if clan tag exists.
     */
    @NotNull
    public CompletableFuture<Boolean> clanTagExists(@NotNull String tag) {
        String sql = "SELECT 1 FROM clans WHERE LOWER(tag) = LOWER(?) LIMIT 1";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                return rs.next();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check clan tag", e);
            }
        }, tag);
    }

    // ===== CLAN MEMBERS =====

    /**
     * Adds a member to a clan.
     */
    @NotNull
    public CompletableFuture<Void> addClanMember(@NotNull String clanId, @NotNull UUID playerUuid,
                                                 @NotNull Clan.ClanRank rank) {
        String sql = """
            INSERT INTO clan_members (clan_id, player_uuid, rank, joined_at)
            VALUES (?, ?, ?, NOW())
            """;

        return databaseManager.executeUpdate(
                sql,
                clanId,
                playerUuid.toString(),
                rank.name()
        ).thenApply(v -> null);
    }

    /**
     * Removes a member from a clan.
     */
    @NotNull
    public CompletableFuture<Void> removeClanMember(@NotNull String clanId, @NotNull UUID playerUuid) {
        String sql = "DELETE FROM clan_members WHERE clan_id = ? AND player_uuid = ?";

        return databaseManager.executeUpdate(
                sql,
                clanId,
                playerUuid.toString()
        ).thenApply(v -> null);
    }

    /**
     * Updates a member's rank.
     */
    @NotNull
    public CompletableFuture<Void> updateMemberRank(@NotNull String clanId, @NotNull UUID playerUuid,
                                                    @NotNull Clan.ClanRank rank) {
        String sql = "UPDATE clan_members SET rank = ? WHERE clan_id = ? AND player_uuid = ?";

        return databaseManager.executeUpdate(
                sql,
                rank.name(),
                clanId,
                playerUuid.toString()
        ).thenApply(v -> null);
    }

    /**
     * Loads all members for a clan.
     */
    @NotNull
    private CompletableFuture<Optional<Clan>> loadClanMembers(@NotNull Clan clan) {
        String sql = """
            SELECT player_uuid, rank
            FROM clan_members
            WHERE clan_id = ?
            """;

        return databaseManager.executeQuery(sql, rs -> {
            try {
                Map<UUID, Clan.ClanRank> members = new HashMap<>();

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    Clan.ClanRank rank = Clan.ClanRank.valueOf(rs.getString("rank"));
                    members.put(playerUuid, rank);
                }

                // Rebuild clan with members
                Clan fullClan = new Clan(
                        clan.getId(),
                        clan.getName(),
                        clan.getTag(),
                        clan.getOwner(),
                        members,
                        clan.getCreatedAt(),
                        clan.getMaxMembers()
                );

                return Optional.of(fullClan);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan members", e);
            }
        }, clan.getId());
    }

    /**
     * Gets member count for a clan.
     */
    @NotNull
    public CompletableFuture<Integer> getClanMemberCount(@NotNull String clanId) {
        String sql = "SELECT COUNT(*) FROM clan_members WHERE clan_id = ?";

        return databaseManager.executeQuery(sql, rs -> {
            try {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to count members", e);
            }
        }, clanId);
    }

    // ===== CLAN INVITES =====

    /**
     * Creates a clan invite.
     */
    @NotNull
    public CompletableFuture<Void> createInvite(@NotNull String clanId, @NotNull UUID from,
                                                @NotNull UUID to, int expireSeconds) {
        String sql = """
            INSERT INTO clan_invites (clan_id, from_uuid, to_uuid, created_at, expires_at)
            VALUES (?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? SECOND))
            """;

        return databaseManager.executeUpdate(
                sql,
                clanId,
                from.toString(),
                to.toString(),
                expireSeconds
        ).thenApply(v -> null);
    }

    /**
     * Gets pending invites for a player.
     */
    @NotNull
    public CompletableFuture<List<ClanInvite>> getPendingInvites(@NotNull UUID playerUuid) {
        String sql = """
            SELECT ci.id, ci.clan_id, c.name as clan_name, ci.from_uuid, 
                   ci.created_at, ci.expires_at
            FROM clan_invites ci
            JOIN clans c ON ci.clan_id = c.id
            WHERE ci.to_uuid = ? AND ci.expires_at > NOW()
            ORDER BY ci.created_at DESC
            """;

        return databaseManager.executeQuery(sql, rs -> {
            List<ClanInvite> invites = new ArrayList<>();
            try {
                while (rs.next()) {
                    invites.add(new ClanInvite(
                            rs.getInt("id"),
                            rs.getString("clan_id"),
                            rs.getString("clan_name"),
                            UUID.fromString(rs.getString("from_uuid")),
                            playerUuid,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("expires_at").toInstant()
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load clan invites", e);
            }
            return invites;
        }, playerUuid.toString());
    }

    /**
     * Deletes a clan invite.
     */
    @NotNull
    public CompletableFuture<Void> deleteInvite(@NotNull String clanId, @NotNull UUID to) {
        String sql = "DELETE FROM clan_invites WHERE clan_id = ? AND to_uuid = ?";

        return databaseManager.executeUpdate(
                sql,
                clanId,
                to.toString()
        ).thenApply(v -> null);
    }

    /**
     * Deletes expired invites (cleanup).
     */
    @NotNull
    public CompletableFuture<Integer> deleteExpiredInvites() {
        String sql = "DELETE FROM clan_invites WHERE expires_at < NOW()";
        return databaseManager.executeUpdate(sql);
    }

    // ===== LEADERBOARDS =====

    /**
     * Gets top clans by member count.
     */
    @NotNull
    public CompletableFuture<List<ClanInfo>> getTopClans(int limit) {
        String sql = """
            SELECT c.id, c.name, c.tag, COUNT(cm.player_uuid) as member_count
            FROM clans c
            LEFT JOIN clan_members cm ON c.id = cm.clan_id
            GROUP BY c.id
            ORDER BY member_count DESC
            LIMIT ?
            """;

        return databaseManager.executeQuery(sql, rs -> {
            List<ClanInfo> clans = new ArrayList<>();
            try {
                while (rs.next()) {
                    clans.add(new ClanInfo(
                            rs.getString("id"),
                            rs.getString("name"),
                            rs.getString("tag"),
                            rs.getInt("member_count")
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to load top clans", e);
            }
            return clans;
        }, limit);
    }

    // ===== HELPER METHODS =====

    private Clan mapClan(ResultSet rs) throws SQLException {
        return new Clan(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getInt("max_members")
        );
    }

    // ===== DATA CLASSES =====

    public record ClanInvite(
            int id,
            String clanId,
            String clanName,
            UUID fromUuid,
            UUID toUuid,
            Instant createdAt,
            Instant expiresAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public record ClanInfo(
            String id,
            String name,
            String tag,
            int memberCount
    ) {}
}