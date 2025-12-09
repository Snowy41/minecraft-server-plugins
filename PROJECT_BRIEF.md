# Minecraft Battle Royale Server - Project Brief

## Project Overview

Building a production-ready Minecraft 1.21.8 server network featuring a custom battle royale gamemode with unique items, inspired by hoplite.gg. The server uses a multi-instance architecture where each game runs on a separate server instance.

## Technology Stack

- **Minecraft**: 1.21.8 (Paper server)
- **Language**: Java 21
- **Build Tool**: Gradle with Kotlin DSL
- **Proxy**: Velocity (multi-instance routing)
- **Database**: MySQL 8.0+ (shared across instances, persistent storage)
- **Cache/Messaging**: Redis (cross-server communication, real-time data)
- **Testing**: JUnit 5, Mockito, MockBukkit
- **Version Support**: ViaVersion + ViaBackwards

## Core Principles

### 1. **Dynamic Configuration**
- **NO hard-coded values** - everything configurable via YAML
- Hot-reload support for config changes
- Environment-specific configs (dev/prod)
- Runtime behavior modification without recompilation

### 2. **Persistent Data**
- All player data stored in MySQL (survives server restarts)
- Statistics tracked across sessions
- Game history preserved
- Async database operations (non-blocking)
- Automatic data migration system

### 3. **Modular Architecture**
- **Each plugin is COMPLETELY SEPARATE** - can be compiled and deployed independently
- No direct plugin-to-plugin dependencies
- Communication ONLY via Redis pub/sub and shared API interfaces
- Plugins can be updated without affecting others

### 4. **Testability**
- Every component unit-testable in isolation
- Dependency injection for all classes
- Interface-based design
- Mock-friendly architecture

## Architecture

### Multi-Instance Setup
```
Velocity Proxy (25565)
├── Lobby Servers (multiple instances for load balancing)
│   └── Plugins: core-plugin.jar + lobby-plugin.jar
└── Game Servers (dynamically started/stopped, one per game)
    └── Plugins: core-plugin.jar + battleroyale-plugin.jar
    
All servers connect to:
├── MySQL (persistent data)
└── Redis (real-time messaging)
```

### Plugin Independence

**CRITICAL**: Each plugin is a SEPARATE JAR file:
- `proxy-plugin.jar` → Install on Velocity proxy
- `core-plugin.jar` → Install on ALL Paper servers (lobby + game)
- `lobby-plugin.jar` → Install ONLY on lobby servers
- `battleroyale-plugin.jar` → Install ONLY on game servers

**Communication Flow**:
1. Plugins never directly call each other
2. All cross-plugin communication via Redis pub/sub
3. Shared data structures defined in `api-module` (shaded into each plugin)
4. Database accessed through `core-plugin` repository interfaces

### Plugin Structure

**Each plugin is SEPARATE and INDEPENDENT:**

1. **proxy-plugin** (Velocity)
   - Queue system for games
   - Load balancing across lobbies
   - Cross-server messaging via Redis

2. **core-plugin** (runs on ALL servers)
   - MySQL database layer (HikariCP)
   - Redis pub/sub messaging
   - Player data management
   - Shared utilities

3. **lobby-plugin** (lobby servers only)
   - Custom GUI system (inventory overlays)
   - Scoreboard (flicker-free, team-based)
   - Tab list system
   - Cosmetics (particle trails, etc.)
   - Game queue UI

4. **battleroyale-plugin** (game servers only)
   - Game state machine (WAITING → STARTING → ACTIVE → ENDING)
   - Custom items system (hoplite.gg style)
   - Loot system (tiered, weighted random)
   - World border (shrinking zone)
   - Team system (for future duos/squads)
   - Statistics tracking

### Custom GUI System (Inventory Overlay Technique)

**How it works:**
1. **Resource Pack**: Create 176x166px PNG image (double chest size)
2. **Overlay**: Image overlays the chest GUI on client side
3. **Slot Mapping**: Design buttons in image that align with inventory slots
4. **Click Detection**: Listen for `InventoryClickEvent`, map slot number to action

**Example Slot Mapping:**
```
Slots 0-53 (double chest):
- Slot 10: "Battle Royale" button
- Slot 12: "Cosmetics" button  
- Slot 14: "Statistics" button
- Slot 49: "Close" button
- Other slots: Barrier blocks (invisible, prevent pickup)
```

**Implementation Flow:**
```java
// 1. Create inventory
Inventory gui = Bukkit.createInventory(null, 54, "Game Selector");

// 2. Populate with barrier blocks (invisible items)
for (int i = 0; i < 54; i++) {
    gui.setItem(i, new ItemStack(Material.BARRIER));
}

// 3. Open for player (resource pack shows custom overlay)
player.openInventory(gui);

// 4. Listen for clicks
@EventHandler
public void onInventoryClick(InventoryClickEvent event) {
    int slot = event.getSlot();
    
    if (slot == 10) {
        // Battle Royale button clicked
        joinBattleRoyaleQueue(player);
    } else if (slot == 12) {
        // Cosmetics button clicked
        openCosmeticsMenu(player);
    }
    
    event.setCancelled(true); // Prevent item pickup
}
```

**Dynamic Content:**
- Update item lore to show live data (player counts, queue status)
- Use glowing effect on available options
- Pagination for multi-page menus

### Custom Items System
- **Rarity Tiers**: Common, Uncommon, Rare, Epic, Legendary
- **Categories**: Melee, Ranged, Magic, Throwables
- **Features**: NBT-based IDs, CustomModelData, abilities, cooldowns, particles, sounds
- **Example Items**:
  - Frost Blade: Slows enemies, ice particles
  - Explosive Bow: Arrows explode on impact
  - Dash Boots: Double-tap sprint to dash
  - Grappling Hook: Pull to locations
  - Healing Aura: Team healing zone

### Game Flow
1. Players join lobby via Velocity proxy
2. Click compass → custom GUI opens (inventory overlay)
3. Select "Battle Royale" → join queue (Redis message to proxy)
4. Queue fills → proxy starts new game server instance
5. Players teleported to game server
6. Game: WAITING (8+ players) → STARTING (30s countdown) → ACTIVE (gameplay) → ENDING (winner celebration)
7. Game server shuts down, players return to lobby

### World Border System
- Initial: 1000x1000 blocks
- Final: 50x50 blocks
- Shrinks every 2 minutes (20% reduction)
- Damage outside zone (increases over time)
- Boss bar countdown to next shrink

## Dynamic & Persistent Coding Patterns

### 1. Configuration-Driven Behavior (NO Hard-Coding)

**Bad Example:**
```java
public class GameManager {
    private static final int MAX_PLAYERS = 100; // Hard-coded!
    
    public boolean canJoin(Game game) {
        return game.getPlayerCount() < MAX_PLAYERS;
    }
}
```

**Good Example:**
```java
public class GameManager {
    private final GameConfig config; // Injected configuration
    
    public GameManager(GameConfig config) {
        this.config = config;
    }
    
    public boolean canJoin(Game game) {
        return game.getPlayerCount() < config.getMaxPlayers();
    }
}

// Config can be changed without recompilation
// Supports hot-reload, environment-specific values
```

### 2. Persistent Data Patterns

**Repository Pattern (Database Access):**
```java
public interface PlayerDataRepository {
    // All database operations async (non-blocking)
    CompletableFuture<Optional<PlayerData>> findByUuid(UUID uuid);
    CompletableFuture<Void> save(PlayerData data);
    CompletableFuture<Void> updateStats(UUID uuid, PlayerStats stats);
}

// Implementation handles:
// - Connection pooling (HikariCP)
// - Async execution
// - Error handling
// - Transaction management
```

**Data Persistence Flow:**
```
Player Action → Update in-memory state → Queue database save (async)
                                      → Continue gameplay (no blocking)
                                      → Database updated in background
```

### 3. Event-Driven Architecture (Extensibility)

**Custom Events:**
```java
// Define events for major actions
public class GameStartEvent extends Event {
    private final Game game;
    // ... implementation
}

// Components listen independently
public class StatisticsTracker implements Listener {
    @EventHandler
    public void onGameStart(GameStartEvent event) {
        // Track game start in database
    }
}

public class DiscordNotifier implements Listener {
    @EventHandler
    public void onGameStart(GameStartEvent event) {
        // Send Discord notification
    }
}

// Easy to add new features without modifying existing code!
```

### 4. Strategy Pattern (Runtime Behavior Changes)

```java
// Different damage calculations based on game mode
public interface DamageCalculator {
    double calculateDamage(Player attacker, Player victim, ItemStack weapon);
}

public class StandardDamageCalculator implements DamageCalculator {
    // Standard calculation
}

public class CustomItemDamageCalculator implements DamageCalculator {
    // Custom item calculation with abilities
}

// Swap at runtime
public class CombatManager {
    private DamageCalculator calculator;
    
    public void setDamageCalculator(DamageCalculator calculator) {
        this.calculator = calculator; // Change behavior dynamically
    }
}
```

### 5. Immutable Data Objects (Thread-Safe, Cacheable)

```java
// Immutable player data - safe to cache, share across threads
public final class PlayerData {
    private final UUID uuid;
    private final String username;
    private final Instant firstJoin;
    private final long playtimeSeconds;
    
    // Constructor, getters only (no setters)
    
    // Builder pattern for "modifications" (creates new instance)
    public PlayerData withPlaytime(long newPlaytime) {
        return new PlayerData(uuid, username, firstJoin, newPlaytime);
    }
}

// Benefits:
// - Thread-safe without synchronization
// - Can be cached safely
// - Prevents accidental mutations
// - Clear data flow
```

### 6. Hot-Reload Configuration

```java
public class ConfigManager {
    private GameConfig config;
    
    public void reload() {
        // Reload from disk
        config = loadFromFile("config.yml");
        
        // Notify all components of config change
        Bukkit.getPluginManager().callEvent(new ConfigReloadEvent(config));
    }
}

// Components react to config changes
@EventHandler
public void onConfigReload(ConfigReloadEvent event) {
    this.maxPlayers = event.getConfig().getMaxPlayers();
    // Update runtime behavior without restart
}
```

### 7. Database Migration System

```java
public class DatabaseMigrator {
    // Automatically update schema on plugin start
    public void migrate() {
        int currentVersion = getCurrentSchemaVersion();
        
        if (currentVersion < 1) {
            runMigration("V1__create_players_table.sql");
        }
        if (currentVersion < 2) {
            runMigration("V2__add_stats_table.sql");
        }
        // ... more migrations
        
        updateSchemaVersion(LATEST_VERSION);
    }
}

// Ensures database stays in sync with code
// Supports rolling updates
```



### Code Architecture Principles

**1. Dependency Injection**
- All dependencies injected via constructor
- No hard-coded dependencies
- Easy to mock for testing

**2. Interface-Based Design**
- Define interfaces in `api-module`
- Implement in specific plugins
- Depend on abstractions, not concrete classes

**3. Separation of Concerns**
- Business logic: Pure Java, easily testable
- Bukkit integration: Thin layer, minimal logic
- Configuration-driven behavior

**4. Test-Driven Development**
- Write tests BEFORE implementation
- Each component independently testable
- Use Mockito for mocking
- AAA pattern (Arrange, Act, Assert)

### Example Component Structure

```java
// Interface (in api-module)
public interface PlayerDataRepository {
    Optional<PlayerData> findByUuid(UUID uuid);
    void save(PlayerData data);
}

// Implementation (in core-plugin)
public class MySQLPlayerRepository implements PlayerDataRepository {
    private final HikariDataSource dataSource;
    
    public MySQLPlayerRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Optional<PlayerData> findByUuid(UUID uuid) {
        // MySQL implementation
    }
}

// Usage (with dependency injection)
public class GameManager {
    private final PlayerDataRepository repository;
    
    public GameManager(PlayerDataRepository repository) {
        this.repository = repository;
    }
}

// Test (with mock)
@Test
void testFindPlayer() {
    PlayerDataRepository mockRepo = mock(PlayerDataRepository.class);
    GameManager manager = new GameManager(mockRepo);
    // Test logic
}
```

## Database Schema

### Players Table
```sql
CREATE TABLE players (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    playtime_seconds BIGINT DEFAULT 0,
    INDEX idx_username (username)
);
```

### Player Stats Table
```sql
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

## Redis Channels

- `server:heartbeat` - Server status updates
- `game:state` - Game state changes
- `player:queue` - Queue operations
- `player:stats` - Statistics updates
- `server:command` - Remote commands

## Configuration Example

```yaml
# battleroyale-plugin/config.yml
game:
  min-players: 8
  max-players: 100
  countdown-seconds: 30
  max-duration: 1200

world-border:
  initial-size: 1000
  final-size: 50
  shrink-interval: 120
  damage-per-tick: 1.0

loot:
  chest-refill-interval: 180
  supply-drop-interval: 300
```

## Development Timeline

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

## Coding Standards

### Required for All Code

- [ ] Constructor dependency injection
- [ ] Interface-based design
- [ ] Unit tests included
- [ ] JavaDoc on public methods
- [ ] Error handling for all failure paths
- [ ] Logging at appropriate levels
- [ ] No hard-coded values (use config)
- [ ] Thread-safe if used concurrently
- [ ] Resources properly cleaned up

### Package Structure

```
com.yourserver.plugin/
├── api/              # Interfaces and contracts
├── impl/             # Concrete implementations
├── model/            # Data classes (POJOs)
├── listener/         # Bukkit event listeners
├── command/          # Command handlers
├── config/           # Configuration classes
└── util/             # Utility classes
```

### Test Naming Convention

```java
@Test
void methodName_givenCondition_expectedBehavior() {
    // Example: findByUuid_whenPlayerExists_returnsPlayerData()
}
```

## Working with AI Assistants

### Prompt Template

```
Create a [COMPONENT_NAME] class that [DESCRIPTION].

Requirements:
- Implements [INTERFACE_NAME] interface
- Dependencies: [LIST_DEPENDENCIES]
- Constructor uses dependency injection
- Include JavaDoc comments
- Follow single responsibility principle

Methods:
1. [METHOD_NAME]
   - Input: [PARAMETERS]
   - Output: [RETURN_TYPE]
   - Edge cases: [NULL_HANDLING, etc.]

Include JUnit 5 unit tests with Mockito.
```

### Example Request

```
Create a CustomItemRegistry class that manages custom battle royale items.

Requirements:
- Implements ItemRegistry interface
- Dependencies: Logger
- Constructor uses dependency injection
- Thread-safe for concurrent access

Methods:
1. register(CustomItem item) -> void
   - Input: CustomItem instance
   - Output: void
   - Edge cases: Duplicate IDs throw IllegalArgumentException

2. getItem(String id) -> Optional<CustomItem>
   - Input: Item ID string
   - Output: Optional containing item or empty
   - Edge cases: Null/empty ID returns Optional.empty()

3. getItem(ItemStack stack) -> Optional<CustomItem>
   - Input: Bukkit ItemStack
   - Output: Optional containing item or empty
   - Edge cases: Null stack or no PDC data returns Optional.empty()

Include JUnit 5 unit tests with Mockito.
```

## Project Goals

### Primary Goals
- ✅ Modular, testable architecture
- ✅ Each component independently testable
- ✅ Dynamic, configuration-driven behavior
- ✅ Persistent data storage (MySQL)
- ✅ Multi-instance scalability
- ✅ Clean, maintainable code

### Game Features
- Solo battle royale (100 players)
- Custom items with unique abilities
- Shrinking world border
- Tiered loot system
- Statistics and leaderboards
- Cosmetics in lobby
- Team support (future)

## Next Steps

1. Set up development environment (Java 21, Gradle, IntelliJ)
2. Set up VPS with MySQL and Redis
3. Initialize Gradle multi-module project
4. Begin with proxy-plugin and core-plugin
5. Use TDD approach: tests first, then implementation
6. Deploy and test multi-instance setup

---

**For detailed specifications, see:**
- `implementation_plan.md` - Complete technical plan
- `ai_development_guide.md` - AI-assisted development patterns
- `task.md` - Development checklist
