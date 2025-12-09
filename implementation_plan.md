# Minecraft Server Development Plan

A comprehensive development plan for building a production-ready Minecraft server with core infrastructure plugins and a multiplayer battle royale gamemode featuring custom items, inspired by hoplite.gg.

> [!NOTE]
> **AI-Assisted Development**: This project is designed to be developed with AI coding assistants. See [AI Development Guide](ai_development_guide.md) for detailed specifications, prompt templates, and testability patterns.

## User Review Required

> [!IMPORTANT]
> **Confirmed Technology Stack**
> - **Minecraft Version**: 1.21.8 with ViaVersion support (allows players from newer versions to join)
> - **Server Software**: Paper (high-performance Spigot fork)
> - **Build Tool**: Gradle (modern dependency management and build performance)
> - **Database**: MySQL/MariaDB for persistent data storage (setup guide included below)
> - **Redis**: Required for multi-instance communication (each game runs on separate server instance)
> - **Proxy**: Velocity (recommended) or BungeeCord for multi-server network
> - **Testing**: JUnit 5, Mockito, MockBukkit for unit and integration tests

> [!IMPORTANT]
> **Confirmed Project Scope**
> - **Game Modes**: Start with **solo mode**, architecture designed to support teams (duos/squads) later
> - **Map Size**: 1000x1000 blocks, 100 players per game
> - **Ranked Mode**: Not in initial release, but architecture supports future implementation
> - **Cosmetics**: Primarily in lobby (particle trails, hub items, etc.)
> - **Development Approach**: Modular, testable architecture with dependency injection for AI-assisted development

---

## Project Architecture

### Technology Stack
- **Server Platform**: Paper 1.21.8
- **Proxy Layer**: Velocity (recommended) or BungeeCord for multi-instance setup
- **Language**: Java 21 (LTS, best performance for Paper 1.21.8)
- **Build System**: Gradle with Kotlin DSL
- **Database**: MySQL 8.0+ for persistent storage (shared across all instances)
- **Cache Layer**: Redis (required for multi-instance communication)
- **Version Support**: ViaVersion + ViaBackwards (allow newer client versions)
- **Testing Framework**: JUnit 5 + Mockito + MockBukkit
- **Libraries**:
  - **Adventure API** for modern text components
  - **ProtocolLib** for packet manipulation (custom items, visuals)
  - **HikariCP** for database connection pooling
  - **Lettuce** for Redis client (async, high-performance)
  - **Caffeine** for in-memory caching
  - **Configurate** for advanced configuration management

### Project Structure

> [!IMPORTANT]
> **Each module is a SEPARATE, INDEPENDENT plugin** that can be compiled and deployed individually. They communicate through the shared API module and Redis messaging.

```
minecraft-server/
├── proxy-plugin/                  # SEPARATE PLUGIN: Velocity proxy plugin
│   ├── src/main/java/
│   │   └── com/yourserver/proxy/
│   │       ├── ProxyPlugin.java
│   │       ├── queue/             # Queue system for games
│   │       ├── balancer/          # Load balancing
│   │       └── messaging/         # Cross-server messaging via Redis
│   ├── build.gradle.kts
│   └── plugin.yml / velocity-plugin.json
│
├── core-plugin/                   # SEPARATE PLUGIN: Core infrastructure (runs on ALL servers)
│   ├── src/main/java/
│   │   └── com/yourserver/core/
│   │       ├── CorePlugin.java
│   │       ├── database/          # Database abstraction layer
│   │       ├── redis/             # Redis pub/sub system
│   │       ├── player/            # Player data management
│   │       ├── config/            # Configuration system
│   │       └── utils/             # Shared utilities
│   ├── build.gradle.kts
│   └── plugin.yml
│
├── lobby-plugin/                  # SEPARATE PLUGIN: Lobby system (lobby servers only)
│   ├── src/main/java/
│   │   └── com/yourserver/lobby/
│   │       ├── LobbyPlugin.java
│   │       ├── scoreboard/        # Scoreboard manager
│   │       ├── tablist/           # Tab list manager
│   │       ├── spawn/             # Spawn management
│   │       ├── cosmetics/         # Cosmetic items (trails, etc.)
│   │       ├── gui/               # Custom GUI system for game selection
│   │       └── queue/             # Game queue UI
│   ├── build.gradle.kts
│   └── plugin.yml
│   ├── resources/
│   │   └── guis/                  # GUI overlay images
│   │       ├── game_selector.png
│   │       └── cosmetics_menu.png
│
├── battleroyale-plugin/           # SEPARATE PLUGIN: Battle Royale (game servers only)
│   ├── src/main/java/
│   │   └── com/yourserver/battleroyale/
│   │       ├── BattleRoyalePlugin.java
│   │       ├── game/              # Game state management
│   │       ├── arena/             # Arena/map management
│   │       ├── items/             # Custom items system
│   │       ├── abilities/         # Item abilities/effects
│   │       ├── worldborder/       # Shrinking zone mechanics
│   │       ├── loot/              # Loot table system
│   │       ├── stats/             # Player statistics
│   │       ├── team/              # Team system (for future duos/squads)
│   │       └── ui/                # Scoreboards, boss bars, etc.
│   ├── build.gradle.kts
│   └── plugin.yml
│
├── api-module/                    # SHARED API: Not a plugin, just a library
│   ├── src/main/java/
│   │   └── com/yourserver/api/
│   │       ├── player/            # Player API interfaces
│   │       ├── game/              # Game API interfaces
│   │       ├── events/            # Custom events
│   │       └── messaging/         # Redis message contracts
│   └── build.gradle.kts
│
├── buildSrc/                      # Build configuration
│   └── src/main/kotlin/
│       └── Dependencies.kt        # Centralized dependency versions
│
├── build.gradle.kts               # Root build file
├── settings.gradle.kts            # Multi-module configuration
└── gradle.properties              # Project properties
```

**Plugin Deployment**:
- **Velocity Proxy**: Install `proxy-plugin.jar`
- **Lobby Servers**: Install `core-plugin.jar` + `lobby-plugin.jar`
- **Game Servers**: Install `core-plugin.jar` + `battleroyale-plugin.jar`

**Plugin Dependencies**:
- All plugins depend on `api-module` (shaded into each plugin JAR)
- `lobby-plugin` and `battleroyale-plugin` both depend on `core-plugin` being present
- Plugins communicate via Redis pub/sub (no direct plugin-to-plugin dependencies)

---

## Multi-Instance Architecture

### Network Overview
```
                    ┌─────────────────┐
                    │  Velocity Proxy │
                    │   (Port 25565)  │
                    └────────┬────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
         │ Lobby-1 │    │ Lobby-2 │   │ Lobby-N │
         │ (25566) │    │ (25567) │   │ (2556X) │
         └─────────┘    └─────────┘   └─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────▼────┐    ┌────▼────┐   ┌────▼────┐
         │  Game-1 │    │  Game-2 │   │  Game-N │
         │ (25600) │    │ (25601) │   │ (2560X) │
         └─────────┘    └─────────┘   └─────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────▼────────┐
                    │  Shared MySQL   │
                    │  Shared Redis   │
                    └─────────────────┘
```

### Server Types

1. **Velocity Proxy**
   - Entry point for all players
   - Routes players to lobby servers
   - Handles queue system for games
   - Load balances across lobby servers
   - Manages cross-server messaging

2. **Lobby Servers**
   - Multiple instances for load balancing
   - Players spawn here after joining
   - Game queue interface
   - Cosmetics showcase
   - Server selector (future gamemodes)

3. **Game Servers**
   - Each server runs ONE battle royale game
   - Dynamically started when game begins
   - Automatically shut down after game ends
   - Isolated worlds (no interference)
   - Reports game state to Redis

### Communication Flow

**Player Joins Game**:
```
1. Player in Lobby clicks "Play"
2. Lobby plugin sends Redis message: "QUEUE_JOIN:<uuid>"
3. Proxy receives message, adds player to queue
4. When game ready, proxy finds available game server
5. Proxy sends player to game server
6. Game server receives player, starts game when full
```

**Cross-Server Messaging (Redis Pub/Sub)**:
```java
// Channels
- "server:heartbeat"     // Server status updates
- "game:state"           // Game state changes
- "player:queue"         // Queue operations
- "player:stats"         // Statistics updates
- "server:command"       // Remote commands
```

### Database & Redis Setup Guide

> [!IMPORTANT]
> **VPS Setup Instructions**
> Follow these steps to set up MySQL and Redis on your VPS for the Minecraft server network.

#### MySQL Setup on VPS

**1. Install MySQL**:
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install mysql-server

# Start MySQL service
sudo systemctl start mysql
sudo systemctl enable mysql

# Secure installation
sudo mysql_secure_installation
```

**2. Create Database and User**:
```sql
-- Login to MySQL
sudo mysql -u root -p

-- Create database
CREATE DATABASE minecraft_server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create user (replace 'your_password' with strong password)
CREATE USER 'minecraft'@'localhost' IDENTIFIED BY 'your_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON minecraft_server.* TO 'minecraft'@'localhost';
FLUSH PRIVILEGES;

-- Exit
EXIT;
```

**3. Configure MySQL for Performance**:
```bash
# Edit MySQL config
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf

# Add/modify these settings:
[mysqld]
max_connections = 200
innodb_buffer_pool_size = 1G  # 70% of available RAM for MySQL
innodb_log_file_size = 256M
innodb_flush_log_at_trx_commit = 2
```

**4. Restart MySQL**:
```bash
sudo systemctl restart mysql
```

#### Redis Setup on VPS

**1. Install Redis**:
```bash
# Ubuntu/Debian
sudo apt install redis-server

# Start Redis
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

**2. Configure Redis**:
```bash
# Edit Redis config
sudo nano /etc/redis/redis.conf

# Important settings:
maxmemory 512mb
maxmemory-policy allkeys-lru
bind 127.0.0.1  # Only local connections
requirepass your_redis_password  # Set strong password
```

**3. Restart Redis**:
```bash
sudo systemctl restart redis-server
```

**4. Test Connection**:
```bash
redis-cli
AUTH your_redis_password
PING  # Should return PONG
```

#### Plugin Configuration

**Core Plugin - database.yml**:
```yaml
mysql:
  host: "localhost"
  port: 3306
  database: "minecraft_server"
  username: "minecraft"
  password: "your_password"
  
  # Connection pool settings
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000

redis:
  host: "localhost"
  port: 6379
  password: "your_redis_password"
  
  # Connection pool settings
  pool:
    max-total: 20
    max-idle: 10
    min-idle: 2
```

**Database Connection Code**:
```java
public class DatabaseManager {
    private HikariDataSource dataSource;
    
    public void initialize(DatabaseConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        // Connection settings
        hikariConfig.setJdbcUrl(String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
            config.getHost(),
            config.getPort(),
            config.getDatabase()
        ));
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        
        // Pool settings
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        
        // Performance settings
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        
        this.dataSource = new HikariDataSource(hikariConfig);
        
        // Test connection
        try (Connection conn = dataSource.getConnection()) {
            logger.info("Database connection successful!");
        } catch (SQLException e) {
            logger.error("Failed to connect to database!", e);
        }
    }
    
    public CompletableFuture<Void> executeAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.error("Database error executing: " + sql, e);
            }
        });
    }
}
```

**Redis Connection Code**:
```java
public class RedisManager {
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;
    
    public void initialize(RedisConfig config) {
        // Build Redis URI
        RedisURI redisUri = RedisURI.builder()
            .withHost(config.getHost())
            .withPort(config.getPort())
            .withPassword(config.getPassword().toCharArray())
            .build();
        
        // Create client
        redisClient = RedisClient.create(redisUri);
        connection = redisClient.connect();
        syncCommands = connection.sync();
        
        // Test connection
        String pong = syncCommands.ping();
        logger.info("Redis connection successful! Response: " + pong);
    }
    
    // Publish message to channel
    public void publish(String channel, String message) {
        syncCommands.publish(channel, message);
    }
    
    // Subscribe to channel
    public void subscribe(String channel, Consumer<String> handler) {
        StatefulRedisPubSubConnection<String, String> pubSubConn = 
            redisClient.connectPubSub();
        
        pubSubConn.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                handler.accept(message);
            }
        });
        
        pubSubConn.sync().subscribe(channel);
    }
    
    // Cache operations
    public void set(String key, String value, long ttlSeconds) {
        syncCommands.setex(key, ttlSeconds, value);
    }
    
    public String get(String key) {
        return syncCommands.get(key);
    }
}
```

### Dynamic Game Server Management

**Game Server Lifecycle**:
```java
public class GameServerManager {
    private final Map<String, GameServerInfo> activeServers = new ConcurrentHashMap<>();
    
    // Called when queue has enough players
    public GameServerInfo startGameServer() {
        // 1. Find available port
        int port = findAvailablePort();
        
        // 2. Create server directory
        Path serverDir = createServerDirectory(port);
        
        // 3. Copy world template
        copyWorldTemplate(serverDir);
        
        // 4. Generate server.properties
        generateServerConfig(serverDir, port);
        
        // 5. Start server process
        Process serverProcess = startServerProcess(serverDir);
        
        // 6. Wait for server to be ready
        waitForServerReady(port);
        
        // 7. Register in Redis
        GameServerInfo info = new GameServerInfo(port, serverProcess);
        activeServers.put(info.getId(), info);
        redisManager.publish("server:started", info.toJson());
        
        return info;
    }
    
    // Called when game ends
    public void shutdownGameServer(String serverId) {
        GameServerInfo info = activeServers.remove(serverId);
        if (info != null) {
            // 1. Gracefully stop server
            info.getProcess().destroy();
            
            // 2. Wait for shutdown
            info.getProcess().waitFor(30, TimeUnit.SECONDS);
            
            // 3. Force kill if needed
            if (info.getProcess().isAlive()) {
                info.getProcess().destroyForcibly();
            }
            
            // 4. Clean up server directory (optional)
            deleteServerDirectory(info.getPort());
            
            // 5. Notify via Redis
            redisManager.publish("server:stopped", serverId);
        }
    }
}
```

---

## Proposed Changes

### Phase 1: Core Infrastructure

#### [NEW] API Module
**Purpose**: Shared interfaces and contracts for all plugins

**Key Components**:
- `PlayerData` interface for player information
- `GameAPI` for game state management
- Custom event system for cross-plugin communication
- Database repository interfaces
- Configuration abstractions

**Benefits**:
- Decouples plugins from implementation details
- Enables easy testing with mocks
- Provides clear contracts between modules

---

#### [NEW] Core Plugin
**Purpose**: Foundation for all server functionality

**Features**:
1. **Database Management**
   - HikariCP connection pool
   - Repository pattern for data access
   - Async query execution
   - Migration system for schema updates

2. **Player Data System**
   - UUID-based player profiles
   - Join/quit tracking
   - Playtime tracking
   - Permission integration (LuckPerms recommended)

3. **Configuration System**
   - YAML-based configs with validation
   - Hot-reload support
   - Environment-specific configs (dev/prod)

4. **Utilities**
   - Task scheduler wrapper
   - Message formatting with Adventure API
   - Math utilities (vectors, geometry)
   - Collection utilities

**Database Schema**:
```sql
CREATE TABLE players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    playtime_seconds BIGINT DEFAULT 0,
    INDEX idx_username (username)
);

CREATE TABLE player_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    games_played INT DEFAULT 0,
    games_won INT DEFAULT 0,
    kills INT DEFAULT 0,
    deaths INT DEFAULT 0,
    damage_dealt DOUBLE DEFAULT 0,
    damage_taken DOUBLE DEFAULT 0,
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);
```

---

#### [NEW] Lobby Plugin
**Purpose**: Hub experience with visual polish

**Features**:
1. **Spawn Management**
   - Protected spawn region
   - Teleport on join
   - Fall damage prevention
   - Void teleport

2. **Tab List System**
   - Header/footer with server info
   - Player count display
   - Rank prefixes (via LuckPerms)
   - Animated header (optional)
   - Update rate: 1 second

3. **Scoreboard System**
   - Personal scoreboards (per-player)
   - Dynamic content:
     - Player name with rank
     - Online players count
     - Current game stats
     - Server IP/website
   - Flicker-free updates using team-based approach
   - Update rate: 500ms for dynamic content

4. **Lobby Protection**
   - Block break/place prevention
   - PvP disabled
   - Item drop/pickup disabled
   - Hunger disabled
   - Weather always clear

5. **Custom GUI System**
   - **Game Selector GUI**: Item (e.g., compass) that opens a custom inventory GUI
   - **GUI Overlay Technique**: Use double chest (54 slots) with custom resource pack overlay image
   - **Click Detection**: Map inventory slot clicks to GUI buttons based on overlay design
   - **GUI Types**:
     - Game mode selector (Battle Royale, future modes)
     - Cosmetics menu (particle trails, hub items)
     - Statistics viewer
     - Settings menu
   - **Resource Pack Integration**: Custom GUI backgrounds sent to players on join
   - **Alternative**: NPC-based selectors (Citizens) or clickable holograms (DecentHolograms) as fallback

**Custom GUI System - Detailed Explanation**:

The custom GUI system works by overlaying a custom image from a resource pack on top of a standard Minecraft inventory (typically a double chest with 54 slots). Here's the complete flow:

1. **Resource Pack Setup**:
   - Create custom PNG images (e.g., `game_selector.png`, `cosmetics_menu.png`) that will overlay the chest GUI
   - Images should be 176x166 pixels (double chest size)
   - Design buttons/clickable areas in the image that align with specific inventory slots
   - Package in resource pack and host on a web server or include in server resource pack

2. **GUI Opening Flow**:
   - Player right-clicks compass item (or uses command)
   - Plugin creates a virtual chest inventory (54 slots)
   - Plugin populates specific slots with invisible/barrier blocks or custom items
   - Plugin opens the inventory for the player
   - Client displays the custom overlay image from resource pack

3. **Click Detection System**:
   - Listen for `InventoryClickEvent`
   - Map clicked slot number to GUI button based on your overlay design
   - Example mapping:
     - Slots 10-16: Different game modes
     - Slots 28-34: Cosmetics categories
     - Slot 49: Close button
   - Cancel the event to prevent item pickup
   - Execute the appropriate action (join queue, open sub-menu, etc.)

4. **Dynamic Content**:
   - Use item names/lore to display dynamic information (player count, stats)
   - Update inventory contents in real-time (e.g., show current queue size)
   - Use item glowing effect or enchantment glint for visual feedback

5. **Multi-Page GUIs**:
   - Implement pagination for cosmetics/stats menus
   - Use specific slots for "Next Page" / "Previous Page" buttons
   - Track current page per player using a HashMap

**Scoreboard & Tab List - Detailed Explanation**:

1. **Flicker-Free Scoreboard**:
   - Use team-based approach instead of directly setting scoreboard lines
   - Create invisible team entries for each line
   - Update team prefixes/suffixes instead of recreating lines
   - Use libraries like FastBoard or implement custom solution
   - Update only changed lines, not entire scoreboard

2. **Tab List System**:
   - Use Adventure API's `sendPlayerListHeaderAndFooter` method
   - Update header/footer every 1 second with current server info
   - For player list entries, integrate with LuckPerms for rank prefixes
   - Use team-based coloring for player names
   - Consider animated headers using frame-by-frame updates

3. **Performance Considerations**:
   - Batch scoreboard updates (update all players at once, not individually)
   - Use async tasks for fetching dynamic data (player counts from Redis)
   - Cache frequently accessed data (rank names, formatted strings)
   - Avoid updating scoreboards for players in-game (only lobby)

---

### Phase 2: Battle Royale Gamemode

#### [NEW] Battle Royale Plugin
**Purpose**: Complete battle royale experience with custom items

**Core Systems**:

1. **Game State Machine**
   - States: WAITING → STARTING → ACTIVE → ENDING
   - Minimum players to start (e.g., 8)
   - Countdown timers
   - Automatic game cycling

2. **Arena Management**
   - Multi-arena support
   - WorldEdit schematic integration
   - Async world loading/unloading
   - Spawn point configuration
   - Loot chest locations

3. **Custom Items System** (Hoplite.gg inspired)
   - **Item Rarity Tiers**: Common, Uncommon, Rare, Epic, Legendary
   - **Weapon Categories**:
     - Melee (swords, axes with special abilities)
     - Ranged (bows, crossbows with custom projectiles)
     - Magic (wands, staffs with spell casting)
     - Throwables (grenades, potions with AoE effects)
   
   - **Custom Item Features**:
     - NBT-based item data
     - Custom models (using CustomModelData)
     - Ability cooldowns
     - Particle effects
     - Sound effects
     - Damage/knockback modifiers
   
   - **Example Items**:
     - **Frost Blade**: Slows enemies on hit, ice particles
     - **Explosive Bow**: Arrows explode on impact
     - **Dash Boots**: Double-tap sprint to dash forward
     - **Healing Aura**: Creates healing zone for teammates
     - **Grappling Hook**: Pull yourself to locations

4. **Loot System**
   - Tiered loot tables (early/mid/late game)
   - Weighted random selection
   - Supply drops with rare items
   - Loot chest refill mechanics
   - Death loot drops

5. **World Border/Zone System**
   - Shrinking play area
   - Configurable shrink intervals
   - Damage outside zone
   - Visual indicators (particles, boss bar)
   - Center point calculation

6. **Combat System**
   - Custom damage calculation
   - Knockback modifications
   - Kill/death tracking
   - Assist system
   - Combat logging (prevent logout)

7. **UI/Visual Systems**
   - **Scoreboard**: Players alive, kills, zone timer
   - **Boss Bar**: Zone shrinking countdown
   - **Action Bar**: Current item info, cooldowns
   - **Title Messages**: Kill notifications, game events
   - **Particle Effects**: Item abilities, zone border
   - **Sound Design**: Custom sounds for all actions

8. **Statistics & Progression**
   - Per-game stats (kills, damage, placement)
   - Lifetime stats (wins, K/D ratio)
   - Leaderboards (daily/weekly/all-time)
   - Achievement system (optional)

9. **Team System Architecture** (for future duos/squads)
   - Team data structure with leader/members
   - Team chat channel
   - Shared kill/damage tracking
   - Team respawn mechanics (optional)
   - Team-based win conditions

> [!NOTE]
> **For AI-Assisted Development**: The battle royale plugin is the most complex component. When implementing with an AI assistant, break down each system into smaller tasks and provide clear specifications for custom items, game states, and loot mechanics. The AI can generate most of the boilerplate code, but you'll need to review and refine the game balance parameters.

---

## Coding Best Practices

### 1. **Clean Code Principles**
- **Single Responsibility**: Each class has one clear purpose
- **DRY (Don't Repeat Yourself)**: Extract common logic into utilities
- **Meaningful Names**: Use descriptive variable/method names
- **Small Methods**: Keep methods under 20 lines when possible
- **Comments**: Explain WHY, not WHAT (code should be self-documenting)

### 2. **Design Patterns**
- **Repository Pattern**: For database access
- **Factory Pattern**: For creating custom items
- **Observer Pattern**: For event handling
- **State Pattern**: For game state management
- **Singleton Pattern**: For managers (use dependency injection instead when possible)

### 3. **Performance Optimization**
- **Async Operations**: Database queries, file I/O
- **Object Pooling**: Reuse objects (particles, projectiles)
- **Caching**: Cache frequently accessed data (player data, configs)
- **Lazy Loading**: Load resources only when needed
- **Batch Operations**: Group database updates

### 4. **Error Handling**
```java
// Good: Specific exception handling
public PlayerData loadPlayerData(UUID uuid) {
    try {
        return repository.findByUuid(uuid)
            .orElseThrow(() -> new PlayerNotFoundException(uuid));
    } catch (SQLException e) {
        logger.error("Failed to load player data for {}", uuid, e);
        return PlayerData.createDefault(uuid);
    }
}

// Bad: Swallowing exceptions
public PlayerData loadPlayerData(UUID uuid) {
    try {
        return repository.findByUuid(uuid).orElse(null);
    } catch (Exception e) {
        // Silent failure
    }
    return null;
}
```

### 5. **Testing Strategy**
- **Unit Tests**: Test individual components (JUnit 5)
- **Integration Tests**: Test plugin interactions
- **Mock Framework**: Use Mockito for mocking Bukkit API
- **Test Coverage**: Aim for 70%+ coverage on business logic

### 6. **Code Organization**
```java
// Package structure example
com.yourserver.battleroyale/
├── game/
│   ├── Game.java              // Main game class
│   ├── GameState.java         // State enum
│   ├── GameManager.java       // Manages multiple games
│   └── GameConfig.java        // Game configuration
├── arena/
│   ├── Arena.java             // Arena data class
│   ├── ArenaLoader.java       // Loads arenas from disk
│   └── ArenaRegistry.java     // Registry of all arenas
├── items/
│   ├── CustomItem.java        // Abstract base class
│   ├── ItemRegistry.java      // Item registration
│   ├── ItemRarity.java        // Rarity enum
│   └── impl/                  // Concrete item implementations
│       ├── FrostBlade.java
│       ├── ExplosiveBow.java
│       └── ...
└── listeners/
    ├── PlayerJoinListener.java
    ├── PlayerQuitListener.java
    └── CombatListener.java
```

### 7. **Configuration Management**
```yaml
# config.yml - Well-documented configuration
game:
  # Minimum players required to start a game
  min-players: 8
  
  # Maximum players per game
  max-players: 100
  
  # Countdown duration in seconds before game starts
  countdown-seconds: 10
  
  # Game duration in seconds (if no winner)
  max-duration: 1200

world-border:
  # Initial border size (blocks)
  initial-size: 1000
  
  # Final border size (blocks)
  final-size: 50
  
  # Time between shrinks (seconds)
  shrink-interval: 120
  
  # Damage per tick outside border
  damage-per-tick: 1.0

loot:
  # Chest refill interval (seconds), -1 to disable
  chest-refill-interval: 180
  
  # Supply drop interval (seconds)
  supply-drop-interval: 300
```

### 8. **Dependency Injection**
```java
// Use constructor injection for testability
public class GameManager {
    private final ArenaRegistry arenaRegistry;
    private final PlayerDataRepository playerRepository;
    private final ItemRegistry itemRegistry;
    
    @Inject // If using DI framework like Guice
    public GameManager(
        ArenaRegistry arenaRegistry,
        PlayerDataRepository playerRepository,
        ItemRegistry itemRegistry
    ) {
        this.arenaRegistry = arenaRegistry;
        this.playerRepository = playerRepository;
        this.itemRegistry = itemRegistry;
    }
}
```

### 9. **Logging Standards**
```java
// Use SLF4J with appropriate log levels
private static final Logger logger = LoggerFactory.getLogger(GameManager.class);

// DEBUG: Detailed information for debugging
logger.debug("Player {} joined game {}", player.getName(), game.getId());

// INFO: Important business events
logger.info("Game {} started with {} players", game.getId(), game.getPlayers().size());

// WARN: Recoverable issues
logger.warn("Arena {} not found, using default", arenaName);

// ERROR: Serious issues that need attention
logger.error("Failed to save player data for {}", uuid, exception);
```

### 10. **Version Control**
- **Commit Messages**: Use conventional commits (feat:, fix:, docs:, refactor:)
- **Branching Strategy**: GitFlow (main, develop, feature/*, hotfix/*)
- **Pull Requests**: Require code review before merging
- **CI/CD**: Automated builds and tests (GitHub Actions, Jenkins)

---

## Development Workflow

### Phase 1: Proxy & Infrastructure (Weeks 1-2)
1. Set up Velocity proxy with Redis integration
2. Implement cross-server messaging system
3. Create MySQL database schema
4. Implement dynamic game server management
5. Test multi-instance communication

### Phase 2: Core Plugin (Week 3)
1. Implement database connection pooling
2. Create player data repository
3. Implement Redis manager
4. Create shared API module
5. Set up configuration system

### Phase 3: Lobby System (Weeks 4-5)
1. Implement lobby plugin
2. Create scoreboard system (flicker-free)
3. Create tab list system
4. Add spawn protection and management
5. Implement cosmetics system (particle trails, etc.)
6. Create game queue UI
7. Test and polish visual elements

### Phase 4: Battle Royale Core (Weeks 6-7)
1. Implement game state machine
2. Create arena management system
3. Implement world border mechanics
4. Add basic combat system
5. Create team system (for future duos/squads)
6. Create UI systems (scoreboards, boss bars)

### Phase 5: Custom Items (Weeks 8-10)
1. Design custom item framework
2. Implement item registry and factory
3. Create 10-15 unique items with abilities
4. Add particle and sound effects
5. Implement ability cooldown system
6. Balance testing

### Phase 6: Loot & Progression (Week 11)
1. Implement loot table system
2. Add supply drops
3. Create statistics tracking
4. Implement leaderboards
5. Add achievement system (optional)

### Phase 7: Testing & Polish (Weeks 12-13)
1. Comprehensive testing (unit + integration)
2. Performance optimization
3. Bug fixes
4. Balance adjustments
5. Documentation

---

## Verification Plan

### Automated Tests
```bash
# Run all unit tests
./gradlew test

# Run with coverage report
./gradlew test jacocoTestReport

# Build all plugins
./gradlew build
```

### Manual Testing Checklist
- [ ] Core plugin loads without errors
- [ ] Database connection successful
- [ ] Player data persists across restarts
- [ ] Lobby scoreboard updates correctly
- [ ] Tab list displays accurate information
- [ ] Spawn protection works in lobby
- [ ] Battle royale game starts with minimum players
- [ ] Custom items work as intended
- [ ] World border shrinks correctly
- [ ] Loot chests spawn items
- [ ] Statistics track accurately
- [ ] No memory leaks during extended play
- [ ] Performance acceptable with 100 players

### Performance Benchmarks
- Server TPS: Maintain 20 TPS with 100 players
- Memory usage: < 4GB with 100 players
- Database query time: < 50ms average
- Player join time: < 2 seconds

---

## Additional Recommendations

### 1. **Resource Pack**
Create a custom resource pack for:
- Custom item models (CustomModelData)
- Custom sounds for abilities
- Custom textures for UI elements
- Custom fonts for scoreboards

### 2. **Anti-Cheat Integration**
- Integrate with Spartan or Matrix anti-cheat
- Custom checks for item abilities
- Combat logging detection

### 3. **Scalability Considerations**
- Design for multi-server setup (BungeeCord/Velocity)
- Use Redis for cross-server communication
- Implement server selector in lobby
- Load balancing for game instances

### 4. **Monetization (Optional)**
- Cosmetics shop (particle trails, kill effects)
- Battle pass system
- Rank upgrades
- Loot boxes (ensure compliance with regulations)

### 5. **Community Features**
- Party system for playing with friends
- Friend system
- Chat channels (global, team, party)
- Report system for rule violations

---

## Technology Recommendations

### Essential Dependencies
```kotlin
// build.gradle.kts
dependencies {
    // Server API (Paper 1.21.8)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")  // Update to 1.21.8 when available
    
    // Modern text components
    implementation("net.kyori:adventure-api:4.17.0")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    
    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    
    // Redis client (for multi-instance communication)
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    
    // Caching
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")
    
    // Packet manipulation (for custom items)
    compileOnly("com.comphenix.protocol:ProtocolLib:5.2.0")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("com.github.seeseemelk:MockBukkit-v1.21:3.9.0")
}
```

### Proxy Dependencies (Velocity)
```kotlin
// proxy/build.gradle.kts
dependencies {
    // Velocity API
    compileOnly("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.3.0-SNAPSHOT")
    
    // Redis for cross-server communication
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
    
    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")
}
```

### Recommended Plugins
- **LuckPerms**: Permission management
- **Vault**: Economy API (if adding economy)
- **ProtocolLib**: Packet manipulation for custom items
- **ViaVersion + ViaBackwards**: Allow newer client versions to join 1.21.8 server
- **Citizens**: NPCs for game selectors (optional)
- **DecentHolograms**: Holographic displays (optional)
- **PlaceholderAPI**: Dynamic placeholders (optional)

---

## Timeline Summary

| Phase | Duration | Deliverables |
|-------|----------|--------------|  
| Proxy & Infrastructure | 2 weeks | Velocity setup, Redis/MySQL, multi-instance framework |
| Core Plugin | 1 week | Database layer, player data, Redis messaging |
| Lobby System | 1.5 weeks | Scoreboard, tab list, cosmetics, queue UI |
| BR Core | 2 weeks | Game loop, arenas, world border, team system |
| Custom Items | 2.5 weeks | Item framework, 10-15 unique items with abilities |
| Loot & Stats | 1 week | Loot system, statistics, leaderboards |
| Testing & Polish | 2 weeks | Bug fixes, optimization, documentation |
| **Total** | **12 weeks** | Production-ready multi-instance server |

---

## Next Steps

1. **Set up development environment**:
   - Install Java 21 (required for Paper 1.21.8)
   - Install IntelliJ IDEA (recommended IDE)
   - Install Gradle 8.5+
   - Set up VPS with MySQL and Redis (follow setup guide above)
   - Install Velocity proxy

2. **Initialize project structure**:
   - Create multi-module Gradle project
   - Set up version control (Git repository)
   - Configure build scripts
   - Set up CI/CD pipeline (optional)

3. **Begin Phase 1**: Proxy & Infrastructure
   - Configure Velocity proxy
   - Implement Redis pub/sub system
   - Set up MySQL database schema
   - Create multi-instance management system

4. **Establish workflows**:
   - Code review process
   - Testing standards
   - Deployment pipeline
   - Documentation practices
