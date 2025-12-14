package com.yourserver.social.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Represents a clan (persistent player community).
 * Stored in MySQL with member ranks and permissions.
 */
public class Clan {

    private final String id;
    private final String name;
    private final String tag;
    private UUID owner;
    private final Map<UUID, ClanRank> members;
    private final Instant createdAt;
    private final int maxMembers;

    public Clan(@NotNull String id, @NotNull String name, @NotNull String tag,
                @NotNull UUID owner, int maxMembers) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.tag = Objects.requireNonNull(tag);
        this.owner = Objects.requireNonNull(owner);
        this.members = new HashMap<>();
        this.members.put(owner, ClanRank.OWNER);
        this.createdAt = Instant.now();
        this.maxMembers = maxMembers;
    }

    public Clan(@NotNull String id, @NotNull String name, @NotNull String tag,
                @NotNull UUID owner, @NotNull Map<UUID, ClanRank> members,
                @NotNull Instant createdAt, int maxMembers) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.tag = Objects.requireNonNull(tag);
        this.owner = Objects.requireNonNull(owner);
        this.members = new HashMap<>(members);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.maxMembers = maxMembers;
    }

    /**
     * Creates a new clan with a random ID.
     */
    public static Clan create(@NotNull String name, @NotNull String tag,
                              @NotNull UUID owner, int maxMembers) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return new Clan(id, name, tag, owner, maxMembers);
    }

    /**
     * Adds a member to the clan.
     */
    public boolean addMember(@NotNull UUID member, @NotNull ClanRank rank) {
        if (members.size() >= maxMembers) {
            return false;
        }
        members.put(member, rank);
        return true;
    }

    /**
     * Removes a member from the clan.
     */
    public boolean removeMember(@NotNull UUID member) {
        if (member.equals(owner)) {
            return false; // Cannot remove owner
        }
        return members.remove(member) != null;
    }

    /**
     * Checks if a player is in the clan.
     */
    public boolean hasMember(@NotNull UUID member) {
        return members.containsKey(member);
    }

    /**
     * Checks if the clan is full.
     */
    public boolean isFull() {
        return members.size() >= maxMembers;
    }

    /**
     * Gets the number of members.
     */
    public int size() {
        return members.size();
    }

    /**
     * Checks if a player is the owner.
     */
    public boolean isOwner(@NotNull UUID player) {
        return owner.equals(player);
    }

    /**
     * Gets a member's rank.
     */
    public ClanRank getRank(@NotNull UUID member) {
        return members.getOrDefault(member, ClanRank.MEMBER);
    }

    /**
     * Sets a member's rank.
     */
    public boolean setRank(@NotNull UUID member, @NotNull ClanRank rank) {
        if (!members.containsKey(member)) {
            return false;
        }
        if (member.equals(owner) && rank != ClanRank.OWNER) {
            return false; // Cannot demote owner
        }
        members.put(member, rank);
        return true;
    }

    /**
     * Transfers ownership to another member.
     */
    public boolean transferOwnership(@NotNull UUID newOwner) {
        if (!members.containsKey(newOwner)) {
            return false;
        }
        members.put(this.owner, ClanRank.ADMIN);
        members.put(newOwner, ClanRank.OWNER);
        this.owner = newOwner;
        return true;
    }

    /**
     * Checks if a member has a specific permission.
     */
    public boolean hasPermission(@NotNull UUID member, @NotNull String permission) {
        ClanRank rank = getRank(member);
        return rank.hasPermission(permission);
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getTag() {
        return tag;
    }

    @NotNull
    public UUID getOwner() {
        return owner;
    }

    @NotNull
    public Map<UUID, ClanRank> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Clan clan = (Clan) o;
        return id.equals(clan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Clan{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", tag='" + tag + '\'' +
                ", members=" + members.size() +
                ", max=" + maxMembers +
                '}';
    }

    /**
     * Clan ranks with permissions.
     */
    public enum ClanRank {
        OWNER("Owner", Set.of("*")),
        ADMIN("Admin", Set.of("invite", "kick", "promote", "demote", "chat")),
        MEMBER("Member", Set.of("chat"));

        private final String displayName;
        private final Set<String> permissions;

        ClanRank(String displayName, Set<String> permissions) {
            this.displayName = displayName;
            this.permissions = permissions;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean hasPermission(String permission) {
            return permissions.contains("*") || permissions.contains(permission);
        }
    }
}