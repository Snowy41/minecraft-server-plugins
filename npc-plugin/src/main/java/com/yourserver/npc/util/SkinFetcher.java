package com.yourserver.npc.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class SkinFetcher {

    public static CompletableFuture<String[]> fetchSkinAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get UUID from username
                URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                String uuid = json.get("id").getAsString();

                // 2. Get skin data from UUID
                url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
                conn = (HttpURLConnection) url.openConnection();
                json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();

                JsonObject textures = json.getAsJsonArray("properties").get(0).getAsJsonObject();
                String texture = textures.get("value").getAsString();
                String signature = textures.get("signature").getAsString();

                return new String[] {texture, signature};

            } catch (Exception e) {
                return null;
            }
        });
    }
}