package com.yourserver.core.database;

import com.yourserver.core.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pooling.
 * Provides async query execution to avoid blocking the server thread.
 */
public class DatabaseManager {

    private final Logger logger;
    private HikariDataSource dataSource;
    private ExecutorService executorService;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Initializes the database connection pool.
     *
     * @param config MySQL configuration
     */
    public void initialize(DatabaseConfig.MySQLConfig config) {
        logger.info("Initializing database connection pool...");

        HikariConfig hikariConfig = new HikariConfig();

        // Connection settings
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Pool settings
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());

        // Performance settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
        hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
        hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
        hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
        hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        hikariConfig.addDataSourceProperty("maintainTimeStats", "false");

        // Pool name for logging
        hikariConfig.setPoolName("MinecraftServer-DB-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);
        this.executorService = Executors.newFixedThreadPool(
                config.getMaximumPoolSize(),
                r -> {
                    Thread thread = new Thread(r, "Database-Worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );

        // Test connection
        try (Connection conn = dataSource.getConnection()) {
            logger.info("Database connection test successful!");

            // Create tables if they don't exist
            createTables();

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to database!", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    /**
     * Creates database tables if they don't exist.
     * FIXED: Removed DEFAULT CURRENT_TIMESTAMP from last_join to support older MySQL versions
     */
    private void createTables() {
        logger.info("Creating database tables if they don't exist...");

        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_join TIMESTAMP NULL DEFAULT NULL,
                playtime_seconds BIGINT DEFAULT 0,
                INDEX idx_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;

        String createStatsTable = """
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid VARCHAR(36) PRIMARY KEY,
                games_played INT DEFAULT 0,
                games_won INT DEFAULT 0,
                kills INT DEFAULT 0,
                deaths INT DEFAULT 0,
                damage_dealt DOUBLE DEFAULT 0,
                damage_taken DOUBLE DEFAULT 0,
                FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt1 = conn.prepareStatement(createPlayersTable);
             PreparedStatement stmt2 = conn.prepareStatement(createStatsTable)) {

            stmt1.executeUpdate();
            stmt2.executeUpdate();

            logger.info("Database tables created/verified successfully");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create database tables!", e);
            throw new RuntimeException("Table creation failed", e);
        }
    }

    /**
     * Executes a query asynchronously and processes the result.
     *
     * @param sql SQL query string
     * @param processor Function to process the ResultSet
     * @param params Query parameters
     * @param <T> Return type
     * @return CompletableFuture containing the result
     */
    public <T> CompletableFuture<T> executeQuery(
            String sql,
            Function<ResultSet, T> processor,
            Object... params
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                // Execute query
                try (ResultSet rs = stmt.executeQuery()) {
                    return processor.apply(rs);
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database query error: " + sql, e);
                throw new RuntimeException("Database query failed", e);
            }
        }, executorService);
    }

    /**
     * Executes an update asynchronously (INSERT, UPDATE, DELETE).
     *
     * @param sql SQL update string
     * @param params Update parameters
     * @return CompletableFuture that completes when the update finishes
     */
    public CompletableFuture<Integer> executeUpdate(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                // Set parameters
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                // Execute update
                return stmt.executeUpdate();

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database update error: " + sql, e);
                throw new RuntimeException("Database update failed", e);
            }
        }, executorService);
    }

    /**
     * Gets a connection from the pool (for advanced usage).
     * IMPORTANT: Must be closed after use!
     *
     * @return Database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Shuts down the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            logger.info("Database executor service shut down");
        }
    }

    /**
     * Checks if the database is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}