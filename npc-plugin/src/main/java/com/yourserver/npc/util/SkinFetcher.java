package com.yourserver.npc.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkinFetcher {

    private static final long CACHE_DURATION = 3600000; // 1 hour
    private static final Map<String, CachedSkin> skinCache = new ConcurrentHashMap<>();

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

    public static CompletableFuture<String[]> fetchSkinAsync(String username) {

        CachedSkin cached = skinCache.get(username.toLowerCase());
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.data);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get UUID from username
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                String uuid = json.get("id").getAsString();

                // 2. Get skin data from UUID
                url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                conn = (HttpURLConnection) url.openConnection();
                json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

                JsonObject textures = json.getAsJsonArray("properties").get(0).getAsJsonObject();
                String texture = textures.get("value").getAsString();
                String signature = textures.get("signature").getAsString();

                String[] result = new String[] {texture, signature};
                skinCache.put(username.toLowerCase(), new CachedSkin(result));
                return result;

            } catch (Exception e) {
                return null;
            }
        });
    }


    private static String[] getDefaultSkin() {
        // Steve's default skin texture
        return new String[] {
                "eyJ0aW1lc3RhbXAiOjE0MTE4...", // Shortened for example
                ""
        };
    }
}