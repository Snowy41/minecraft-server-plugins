package com.yourserver.npc.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Fetches player skins from Mojang API with caching and error handling.
 *
 * FIXED:
 * - Added proper error logging
 * - Added fallback to default Steve skin
 * - Added timeout handling
 * - Improved error messages
 */
public class SkinFetcher {

    private static final Logger LOGGER = Logger.getLogger(SkinFetcher.class.getName());
    private static final long CACHE_DURATION = 3600000; // 1 hour
    private static final Map<String, CachedSkin> skinCache = new ConcurrentHashMap<>();

    // Connection timeouts
    private static final int CONNECT_TIMEOUT = 5000; // 5 seconds
    private static final int READ_TIMEOUT = 5000;    // 5 seconds

    private static class CachedSkin {
        final String[] data;
        final long timestamp;

        CachedSkin(String[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION;
        }
    }

    /**
     * Fetches a player's skin asynchronously.
     *
     * @param username The player's username
     * @return CompletableFuture containing [texture, signature] or default skin on error
     */
    public static CompletableFuture<String[]> fetchSkinAsync(String username) {
        // Check cache first
        CachedSkin cached = skinCache.get(username.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            LOGGER.fine("Using cached skin for: " + username);
            return CompletableFuture.completedFuture(cached.data);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Fetching skin for: " + username);

                // 1. Get UUID from username
                String uuid = fetchUUID(username);
                if (uuid == null) {
                    LOGGER.warning("Player not found: " + username + " - Using default skin");
                    return getDefaultSkin();
                }

                // 2. Get skin data from UUID
                String[] skinData = fetchSkinData(uuid);
                if (skinData == null) {
                    LOGGER.warning("Failed to fetch skin data for: " + username + " - Using default skin");
                    return getDefaultSkin();
                }

                // Cache successful result
                skinCache.put(username.toLowerCase(), new CachedSkin(skinData));
                LOGGER.info("✓ Successfully fetched skin for: " + username);
                return skinData;

            } catch (Exception e) {
                LOGGER.warning("Error fetching skin for " + username + ": " + e.getMessage());
                return getDefaultSkin();
            }
        });
    }

    /**
     * Fetches UUID from Mojang API.
     */
    private static String fetchUUID(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "MinecraftServer/1.0");

            if (conn.getResponseCode() != 200) {
                LOGGER.warning("Mojang API returned " + conn.getResponseCode() + " for: " + username);
                return null;
            }

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())
            ).getAsJsonObject();

            return json.get("id").getAsString();

        } catch (Exception e) {
            LOGGER.warning("Failed to fetch UUID for " + username + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Fetches skin data from Mojang session server.
     */
    private static String[] fetchSkinData(String uuid) {
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/"
                    + uuid + "?unsigned=false");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "MinecraftServer/1.0");

            if (conn.getResponseCode() != 200) {
                LOGGER.warning("Session server returned " + conn.getResponseCode() + " for UUID: " + uuid);
                return null;
            }

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(conn.getInputStream())
            ).getAsJsonObject();

            JsonObject textures = json.getAsJsonArray("properties")
                    .get(0)
                    .getAsJsonObject();

            String texture = textures.get("value").getAsString();
            String signature = textures.get("signature").getAsString();

            return new String[] {texture, signature};

        } catch (Exception e) {
            LOGGER.warning("Failed to fetch skin data for UUID " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns default Steve skin.
     * This is a fallback when Mojang API fails or player doesn't exist.
     */
    private static String[] getDefaultSkin() {
        LOGGER.fine("Using default Steve skin");
        // Return null to use Minecraft's default Steve skin
        // The client will render default skin if no texture is provided
        return null;
    }

    /**
     * Clears the skin cache.
     */
    public static void clearCache() {
        skinCache.clear();
        LOGGER.info("Skin cache cleared");
    }

    /**
     * Gets cache size for debugging.
     */
    public static int getCacheSize() {
        return skinCache.size();
    }

    /**
     * Pre-fetches skin for a username and caches it.
     * Useful for warming up cache on server start.
     */
    public static void prefetchSkin(String username) {
        fetchSkinAsync(username).thenAccept(skinData -> {
            if (skinData != null) {
                LOGGER.info("Pre-fetched skin for: " + username);
            }
        });
    }
}