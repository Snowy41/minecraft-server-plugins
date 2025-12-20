package com.yourserver.core.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yourserver.api.model.PlayerData;
import com.yourserver.api.model.PlayerStats;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to migrate player data from JSON files to MySQL database.
 *
 * Usage:
 * 1. Stop your server
 * 2. Run this class as a standalone Java application
 * 3. Start server with MySQL-enabled CorePlugin
 */
public class JSONToMySQLMigration {

    private static final String INSERT_PLAYER_DATA =
            "INSERT INTO player_data (uuid, username, first_join, last_join, playtime_seconds) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), " +
                    "last_join = VALUES(last_join), " +
                    "playtime_seconds = VALUES(playtime_seconds)";

    private static final String INSERT_PLAYER_STATS =
            "INSERT INTO player_stats (uuid, games_played, games_won, kills, deaths, damage_dealt, damage_taken) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "games_played = VALUES(games_played), " +
                    "games_won = VALUES(games_won), " +
                    "kills = VALUES(kills), " +
                    "deaths = VALUES(deaths), " +
                    "damage_dealt = VALUES(damage_dealt), " +
                    "damage_taken = VALUES(damage_taken)";

    public static void main(String[] args) {
        // Configuration - CHANGE THESE VALUES
        String dbUrl = "jdbc:mysql://localhost:3306/minecraft_server";
        String dbUser = "minecraft";
        String dbPassword = "your_password_here";

        String jsonDataPath = "plugins/CorePlugin/data";

        System.out.println("═══════════════════════════════════════");
        System.out.println("  JSON to MySQL Migration Tool");
        System.out.println("═══════════════════════════════════════");
        System.out.println();

        try {
            // Connect to database
            System.out.println("Connecting to MySQL database...");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            System.out.println("✓ Connected to database");
            System.out.println();

            // Migrate player data
            File playersFile = new File(jsonDataPath, "players.json");
            if (playersFile.exists()) {
                System.out.println("Migrating player data from: " + playersFile.getAbsolutePath());
                int count = migratePlayerData(conn, playersFile);
                System.out.println("✓ Migrated " + count + " player records");
            } else {
                System.out.println("⚠ Player data file not found: " + playersFile.getAbsolutePath());
            }
            System.out.println();

            // Migrate player stats
            File statsFile = new File(jsonDataPath, "player-stats.json");
            if (statsFile.exists()) {
                System.out.println("Migrating player stats from: " + statsFile.getAbsolutePath());
                int count = migratePlayerStats(conn, statsFile);
                System.out.println("✓ Migrated " + count + " player stats records");
            } else {
                System.out.println("⚠ Player stats file not found: " + statsFile.getAbsolutePath());
            }
            System.out.println();

            conn.close();

            System.out.println("═══════════════════════════════════════");
            System.out.println("  ✓ Migration completed successfully!");
            System.out.println("═══════════════════════════════════════");
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("1. Backup your JSON files (just in case)");
            System.out.println("2. Update CorePlugin to use MySQL");
            System.out.println("3. Start your server");

        } catch (Exception e) {
            System.err.println("✗ Migration failed!");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int migratePlayerData(Connection conn, File file) throws Exception {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();

        // Read JSON structure: { "players": { "uuid": PlayerData, ... } }
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> root = gson.fromJson(reader,
                    new TypeToken<Map<String, Object>>(){}.getType());

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> players =
                    (Map<String, Map<String, Object>>) root.get("players");

            if (players == null || players.isEmpty()) {
                return 0;
            }

            AtomicInteger count = new AtomicInteger(0);
            PreparedStatement stmt = conn.prepareStatement(INSERT_PLAYER_DATA);

            players.forEach((uuid, data) -> {
                try {
                    stmt.setString(1, uuid);
                    stmt.setString(2, (String) data.get("username"));
                    stmt.setTimestamp(3, Timestamp.from(
                            Instant.parse((String) data.get("firstJoin"))));
                    stmt.setTimestamp(4, Timestamp.from(
                            Instant.parse((String) data.get("lastJoin"))));
                    stmt.setLong(5, ((Number) data.get("playtimeSeconds")).longValue());

                    stmt.addBatch();
                    count.incrementAndGet();

                    // Execute batch every 100 records
                    if (count.get() % 100 == 0) {
                        stmt.executeBatch();
                        System.out.println("  Processed " + count.get() + " players...");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to migrate player: " + uuid);
                    e.printStackTrace();
                }
            });

            // Execute remaining
            if (count.get() % 100 != 0) {
                stmt.executeBatch();
            }

            stmt.close();
            return count.get();
        }
    }

    private static int migratePlayerStats(Connection conn, File file) throws Exception {
        Gson gson = new Gson();

        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> root = gson.fromJson(reader,
                    new TypeToken<Map<String, Object>>(){}.getType());

            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> stats =
                    (Map<String, Map<String, Object>>) root.get("stats");

            if (stats == null || stats.isEmpty()) {
                return 0;
            }

            AtomicInteger count = new AtomicInteger(0);
            PreparedStatement stmt = conn.prepareStatement(INSERT_PLAYER_STATS);

            stats.forEach((uuid, data) -> {
                try {
                    stmt.setString(1, uuid);
                    stmt.setInt(2, ((Number) data.get("gamesPlayed")).intValue());
                    stmt.setInt(3, ((Number) data.get("gamesWon")).intValue());
                    stmt.setInt(4, ((Number) data.get("kills")).intValue());
                    stmt.setInt(5, ((Number) data.get("deaths")).intValue());
                    stmt.setDouble(6, ((Number) data.get("damageDealt")).doubleValue());
                    stmt.setDouble(7, ((Number) data.get("damageTaken")).doubleValue());

                    stmt.addBatch();
                    count.incrementAndGet();

                    if (count.get() % 100 == 0) {
                        stmt.executeBatch();
                        System.out.println("  Processed " + count.get() + " stats...");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to migrate stats for: " + uuid);
                    e.printStackTrace();
                }
            });

            if (count.get() % 100 != 0) {
                stmt.executeBatch();
            }

            stmt.close();
            return count.get();
        }
    }

    // Instant type adapter for Gson
    private static class InstantTypeAdapter extends com.google.gson.TypeAdapter<Instant> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}