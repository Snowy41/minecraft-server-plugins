package com.yourserver.social.database;

import com.yourserver.social.model.Clan;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL implementation of clan storage.
 * Uses CorePlugin's HikariCP connection pool.
 */
public class MySQLClanRepository {

    private final HikariDataSource dataSource;
    private final Executor executor;
    private final Logger logger;

    public MySQLClanRepository(@NotNull HikariDataSource dataSource,
                               @NotNull Executor executor,
                               @NotNull Logger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
    }

    // ===== CLANS =====

    @NotNull
    public CompletableFuture<Optional<Clan>> getClan(@NotNull String clanId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT name, tag, owner_uuid, created_at, max_members FROM social_clans WHERE id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String name = rs.getString("name");
                        String tag = rs.getString("tag");
                        UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();
                        int maxMembers = rs.getInt("max_members");

                        // Load members
                        Map<UUID, Clan.ClanRank> members = loadMembers(clanId);

                        return Optional.of(new Clan(clanId, name, tag, owner, members, createdAt, maxMembers));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get clan: " + clanId, e);
            }

            return Optional.empty();
        }, executor);
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByName(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM social_clans WHERE name = ? COLLATE utf8mb4_unicode_ci";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String clanId = rs.getString("id");
                        return getClan(clanId).join();
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get clan by name: " + name, e);
            }

            return Optional.empty();
        }, executor);
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getClanByTag(@NotNull String tag) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT id FROM social_clans WHERE tag = ? COLLATE utf8mb4_unicode_ci";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tag);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String clanId = rs.getString("id");
                        return getClan(clanId).join();
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get clan by tag: " + tag, e);
            }

            return Optional.empty();
        }, executor);
    }

    @NotNull
    public CompletableFuture<Optional<Clan>> getPlayerClan(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT clan_id FROM social_clan_members WHERE player_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String clanId = rs.getString("clan_id");
                        return getClan(clanId).join();
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get player clan: " + playerUuid, e);
            }

            return Optional.empty();
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> createClan(@NotNull Clan clan) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Insert clan
                    String clanSql = "INSERT INTO social_clans (id, name, tag, owner_uuid, max_members) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(clanSql)) {
                        stmt.setString(1, clan.getId());
                        stmt.setString(2, clan.getName());
                        stmt.setString(3, clan.getTag());
                        stmt.setString(4, clan.getOwner().toString());
                        stmt.setInt(5, clan.getMaxMembers());
                        stmt.executeUpdate();
                    }

                    // Insert owner as member
                    String memberSql = "INSERT INTO social_clan_members (clan_id, player_uuid, rank) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(memberSql)) {
                        stmt.setString(1, clan.getId());
                        stmt.setString(2, clan.getOwner().toString());
                        stmt.setString(3, "OWNER");
                        stmt.executeUpdate();
                    }

                    conn.commit();

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create clan: " + clan.getName(), e);
                throw new RuntimeException("Failed to create clan", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> deleteClan(@NotNull String clanId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM social_clans WHERE id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);
                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete clan: " + clanId, e);
                throw new RuntimeException("Failed to delete clan", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Boolean> clanNameExists(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM social_clans WHERE name = ? COLLATE utf8mb4_unicode_ci LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, name);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to check clan name", e);
                return false;
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Boolean> clanTagExists(@NotNull String tag) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM social_clans WHERE tag = ? COLLATE utf8mb4_unicode_ci LIMIT 1";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tag);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to check clan tag", e);
                return false;
            }
        }, executor);
    }

    // ===== CLAN MEMBERS =====

    @NotNull
    public CompletableFuture<Void> addClanMember(@NotNull String clanId, @NotNull UUID playerUuid,
                                                 @NotNull Clan.ClanRank rank) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO social_clan_members (clan_id, player_uuid, rank) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE rank = VALUES(rank)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, rank.name());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to add clan member", e);
                throw new RuntimeException("Failed to add clan member", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> removeClanMember(@NotNull String clanId, @NotNull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM social_clan_members WHERE clan_id = ? AND player_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);
                stmt.setString(2, playerUuid.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to remove clan member", e);
                throw new RuntimeException("Failed to remove clan member", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> updateMemberRank(@NotNull String clanId, @NotNull UUID playerUuid,
                                                    @NotNull Clan.ClanRank rank) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE social_clan_members SET rank = ? WHERE clan_id = ? AND player_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, rank.name());
                stmt.setString(2, clanId);
                stmt.setString(3, playerUuid.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to update member rank", e);
                throw new RuntimeException("Failed to update member rank", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Integer> getClanMemberCount(@NotNull String clanId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM social_clan_members WHERE clan_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get member count", e);
            }

            return 0;
        }, executor);
    }

    // ===== CLAN INVITES =====

    @NotNull
    public CompletableFuture<Void> createInvite(@NotNull String clanId, @NotNull UUID from,
                                                @NotNull UUID to, int expireSeconds) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO social_clan_invites (clan_id, from_uuid, to_uuid, expires_at) " +
                    "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);
                stmt.setString(2, from.toString());
                stmt.setString(3, to.toString());
                stmt.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(expireSeconds)));

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to create clan invite", e);
                throw new RuntimeException("Failed to create clan invite", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<List<ClanInvite>> getPendingInvites(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ClanInvite> invites = new ArrayList<>();

            String sql = "SELECT clan_id, from_uuid, created_at, expires_at " +
                    "FROM social_clan_invites WHERE to_uuid = ? AND expires_at > NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String clanId = rs.getString("clan_id");
                        UUID fromUuid = UUID.fromString(rs.getString("from_uuid"));
                        Instant createdAt = rs.getTimestamp("created_at").toInstant();
                        Instant expiresAt = rs.getTimestamp("expires_at").toInstant();

                        invites.add(new ClanInvite(clanId, fromUuid, playerUuid, createdAt, expiresAt));
                    }
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to get pending invites", e);
            }

            return invites;
        }, executor);
    }

    @NotNull
    public CompletableFuture<Void> deleteInvite(@NotNull String clanId, @NotNull UUID to) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM social_clan_invites WHERE clan_id = ? AND to_uuid = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, clanId);
                stmt.setString(2, to.toString());

                stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete invite", e);
            }
        }, executor);
    }

    @NotNull
    public CompletableFuture<Integer> deleteExpiredInvites() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM social_clan_invites WHERE expires_at < NOW()";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                int count = stmt.executeUpdate();
                if (count > 0) {
                    logger.info("Deleted " + count + " expired clan invites");
                }
                return count;

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to delete expired invites", e);
                return 0;
            }
        }, executor);
    }

    // ===== HELPER METHODS =====

    private Map<UUID, Clan.ClanRank> loadMembers(String clanId) {
        Map<UUID, Clan.ClanRank> members = new HashMap<>();

        String sql = "SELECT player_uuid, rank FROM social_clan_members WHERE clan_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, clanId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    Clan.ClanRank rank = Clan.ClanRank.valueOf(rs.getString("rank"));
                    members.put(playerUuid, rank);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load members for clan: " + clanId, e);
        }

        return members;
    }

    // ===== DATA MODEL =====

    public static class ClanInvite {
        public final String clanId;
        public final UUID fromUuid;
        public final UUID toUuid;
        public final Instant createdAt;
        public final Instant expiresAt;

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