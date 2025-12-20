package com.yourserver.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.yourserver.core.config.DatabaseConfig;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern database manager using HikariCP.
 *
 * IMPROVEMENTS:
 * - HikariCP is the fastest connection pool
 * - Auto-reconnect on connection loss
 * - Health checks and monitoring
 * - Graceful shutdown
 * - Schema auto-creation
 */
public class DatabaseManager {

    private final Logger logger;
    private HikariDataSource dataSource;
    private final DatabaseConfig.MySQLConfig config;

    public DatabaseManager(@NotNull Logger logger, @NotNull DatabaseConfig.MySQLConfig config) {
        this.logger = logger;
        this.config = config;
    }

    /**
     * Initializes the database connection pool.
     *
     * @throws SQLException if connection fails
     */
    public void initialize() throws SQLException {
        logger.info("Initializing MySQL connection pool...");

        HikariConfig hikariConfig = new HikariConfig();

        // JDBC URL
        String jdbcUrl = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getHost(),
                config.getPort(),
                config.getDatabase()
        );

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Connection pool settings
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());

        // Connection test query
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // Pool name for monitoring
        hikariConfig.setPoolName("MySQLPool");

        // Performance properties
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

        // Create data source
        try {
            this.dataSource = new HikariDataSource(hikariConfig);
            logger.info("HikariCP connection pool created successfully!");

            // Test connection
            testConnection();

            // Initialize schema
            initializeSchema();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize MySQL connection pool!", e);
            throw new SQLException("Database initialization failed", e);
        }
    }

    /**
     * Tests the database connection.
     *
     * @throws SQLException if connection test fails
     */
    private void testConnection() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("SELECT 1");
            logger.info("✓ Database connection test successful!");

        } catch (SQLException e) {
            logger.severe("✗ Database connection test failed!");
            throw e;
        }
    }

    /**
     * Initializes database schema (creates tables if they don't exist).
     */
    private void initializeSchema() {
        logger.info("Initializing database schema...");

        String[] tables = {
                // Player data table
                """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                username VARCHAR(16) NOT NULL,
                first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                playtime_seconds BIGINT DEFAULT 0,
                last_server VARCHAR(64),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                INDEX idx_username (username),
                INDEX idx_last_join (last_join)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,

                // Player stats table
                """
            CREATE TABLE IF NOT EXISTS player_stats (
                uuid VARCHAR(36) PRIMARY KEY,
                games_played INT DEFAULT 0,
                games_won INT DEFAULT 0,
                kills INT DEFAULT 0,
                deaths INT DEFAULT 0,
                damage_dealt DOUBLE DEFAULT 0,
                damage_taken DOUBLE DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                FOREIGN KEY (uuid) REFERENCES player_data(uuid) ON DELETE CASCADE,
                INDEX idx_kills (kills)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """,

                // Player sessions table
                """
            CREATE TABLE IF NOT EXISTS player_sessions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36) NOT NULL,
                server_name VARCHAR(64) NOT NULL,
                join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                quit_time TIMESTAMP NULL,
                duration_seconds INT DEFAULT 0,
                FOREIGN KEY (uuid) REFERENCES player_data(uuid) ON DELETE CASCADE,
                INDEX idx_uuid (uuid),
                INDEX idx_server (server_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : tables) {
                stmt.execute(sql);
            }

            logger.info("✓ Database schema initialized successfully!");

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize database schema!", e);
        }
    }

    /**
     * Gets a connection from the pool.
     *
     * @return Database connection
     * @throws SQLException if connection cannot be obtained
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Data source is not initialized or is closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Gets the HikariCP data source.
     *
     * @return HikariDataSource
     */
    @NotNull
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Checks if the database is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Gets connection pool statistics.
     *
     * @return Formatted statistics string
     */
    @NotNull
    public String getPoolStats() {
        if (dataSource == null) {
            return "Data source not initialized";
        }

        return String.format(
                "Active: %d | Idle: %d | Total: %d | Waiting: %d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Closes the connection pool gracefully.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing database connection pool...");
            logger.info("Final pool stats: " + getPoolStats());

            dataSource.close();

            logger.info("✓ Database connection pool closed successfully!");
        }
    }

    /**
     * Performs a health check on the database.
     *
     * @return true if healthy, false otherwise
     */
    public boolean healthCheck() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("SELECT 1");
            return true;

        } catch (SQLException e) {
            logger.log(Level.WARNING, "Database health check failed", e);
            return false;
        }
    }
}