package com.yourserver.social.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Represents a party (temporary group for gaming together).
 * Stored in Redis with TTL (parties are temporary, not persistent).
 */
public class Party {

    private final String id;
    private UUID leader;
    private final Set<UUID> members;
    private final Instant createdAt;
    private final int maxMembers;

    public Party(@NotNull String id, @NotNull UUID leader, int maxMembers) {
        this.id = Objects.requireNonNull(id);
        this.leader = Objects.requireNonNull(leader);
        this.members = new HashSet<>();
        this.members.add(leader);
        this.createdAt = Instant.now();
        this.maxMembers = maxMembers;
    }

    public Party(@NotNull String id, @NotNull UUID leader, @NotNull Set<UUID> members,
                 @NotNull Instant createdAt, int maxMembers) {
        this.id = Objects.requireNonNull(id);
        this.leader = Objects.requireNonNull(leader);
        this.members = new HashSet<>(members);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.maxMembers = maxMembers;
    }

    /**
     * Creates a new party with a random ID.
     */
    public static Party create(@NotNull UUID leader, int maxMembers) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return new Party(id, leader, maxMembers);
    }

    /**
     * Adds a member to the party.
     */
    public boolean addMember(@NotNull UUID member) {
        if (members.size() >= maxMembers) {
            return false;
        }
        return members.add(member);
    }

    /**
     * Removes a member from the party.
     */
    public boolean removeMember(@NotNull UUID member) {
        if (member.equals(leader)) {
            return false; // Cannot remove leader
        }
        return members.remove(member);
    }

    /**
     * Checks if a player is in the party.
     */
    public boolean hasMember(@NotNull UUID member) {
        return members.contains(member);
    }

    /**
     * Checks if the party is full.
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
     * Checks if a player is the leader.
     */
    public boolean isLeader(@NotNull UUID player) {
        return leader.equals(player);
    }

    /**
     * Transfers leadership to another member.
     */
    public boolean transferLeadership(@NotNull UUID newLeader) {
        if (!members.contains(newLeader)) {
            return false;
        }
        this.leader = newLeader;
        return true;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public UUID getLeader() {
        return leader;
    }

    @NotNull
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
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
        Party party = (Party) o;
        return id.equals(party.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Party{" +
                "id='" + id + '\'' +
                ", leader=" + leader +
                ", members=" + members.size() +
                ", max=" + maxMembers +
                '}';
    }
}