package com.yourserver.social.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yourserver.api.messaging.RedisMessenger;
import com.yourserver.social.model.Party;
import com.yourserver.social.storage.InstantAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;

/**
 * Redis-based party storage (parties are temporary, stored with 1-hour TTL).
 * Uses CorePlugin's Redis infrastructure.
 */
public class RedisPartyRepository {

    private final RedisMessenger redis;
    private final Gson gson;

    private static final String KEY_PREFIX = "party:";
    private static final String PLAYER_PARTY_PREFIX = "player_party:";
    private static final long PARTY_TTL = 3600; // 1 hour

    public RedisPartyRepository(@NotNull RedisMessenger redis) {
        this.redis = redis;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .create();    }

    /**
     * Saves a party to Redis.
     */
    public void save(@NotNull Party party) {
        String key = KEY_PREFIX + party.getId();

        // Serialize party data
        Map<String, String> data = new HashMap<>();
        data.put("id", party.getId());
        data.put("leader", party.getLeader().toString());
        data.put("members", gson.toJson(party.getMembers()));
        data.put("createdAt", party.getCreatedAt().toString());
        data.put("maxMembers", String.valueOf(party.getMaxMembers()));

        String json = gson.toJson(data);
        redis.set(key, json, PARTY_TTL);

        // Also index by each member
        for (UUID member : party.getMembers()) {
            String playerKey = PLAYER_PARTY_PREFIX + member;
            redis.set(playerKey, party.getId(), PARTY_TTL);
        }
    }

    /**
     * Loads a party from Redis.
     */
    @Nullable
    public Party load(@NotNull String partyId) {
        String key = KEY_PREFIX + partyId;
        String json = redis.get(key);

        if (json == null) {
            return null;
        }

        try {
            Map<String, String> data = gson.fromJson(json,
                    new TypeToken<Map<String, String>>(){}.getType());

            String id = data.get("id");
            UUID leader = UUID.fromString(data.get("leader"));

            Set<UUID> members = gson.fromJson(data.get("members"),
                    new TypeToken<Set<UUID>>(){}.getType());

            Instant createdAt = Instant.parse(data.get("createdAt"));
            int maxMembers = Integer.parseInt(data.get("maxMembers"));

            return new Party(id, leader, members, createdAt, maxMembers);

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds a party by player UUID.
     */
    @Nullable
    public Party findByPlayer(@NotNull UUID playerUuid) {
        String playerKey = PLAYER_PARTY_PREFIX + playerUuid;
        String partyId = redis.get(playerKey);

        if (partyId == null) {
            return null;
        }

        return load(partyId);
    }

    /**
     * Deletes a party from Redis.
     */
    public void delete(@NotNull String partyId) {
        Party party = load(partyId);
        if (party == null) {
            return;
        }

        // Delete party data
        String key = KEY_PREFIX + partyId;
        redis.delete(key);

        // Delete player indexes
        for (UUID member : party.getMembers()) {
            String playerKey = PLAYER_PARTY_PREFIX + member;
            redis.delete(playerKey);
        }
    }

    /**
     * Removes a player from their party index.
     */
    public void removePlayerIndex(@NotNull UUID playerUuid) {
        String playerKey = PLAYER_PARTY_PREFIX + playerUuid;
        redis.delete(playerKey);
    }

    /**
     * Checks if a party exists.
     */
    public boolean exists(@NotNull String partyId) {
        String key = KEY_PREFIX + partyId;
        return redis.exists(key);
    }

    /**
     * Checks if a player is in a party.
     */
    public boolean isInParty(@NotNull UUID playerUuid) {
        String playerKey = PLAYER_PARTY_PREFIX + playerUuid;
        return redis.exists(playerKey);
    }
}