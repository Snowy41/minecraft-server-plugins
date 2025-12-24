package com.yourserver.gamelobby;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GameLobbyPlugin components.
 *
 * AVOIDS MockBukkit due to known bugs. Uses pure Mockito instead.
 * NO API MODULE IMPORTS - all models defined locally for testing.
 *
 * Tests cover:
 * - GameServiceManager (service tracking)
 * - Redis message parsing
 * - Service state management
 * - Heartbeat detection
 * - Stale service removal
 *
 * Note: These are self-contained tests that don't require any external dependencies
 * beyond JUnit 5 and Mockito.
 */
class GameLobbyPluginTests {

    // ===== GameServiceManager Tests =====

    @Nested
    @DisplayName("GameServiceManager Tests")
    class GameServiceManagerTests {

        private GameServiceManager serviceManager;

        @BeforeEach
        void setUp() {
            serviceManager = new GameServiceManager();
        }

        @Test
        @DisplayName("Should register new service")
        void shouldRegisterNewService() {
            // Arrange
            GameService service = new GameService(
                    "BattleRoyale-1",
                    GameState.WAITING,
                    0,
                    100,
                    0
            );

            // Act
            serviceManager.registerService("battleroyale", service);

            // Assert
            List<GameService> services = serviceManager.getServices("battleroyale");
            assertEquals(1, services.size());
            assertEquals("BattleRoyale-1", services.get(0).getName());
        }

        @Test
        @DisplayName("Should update existing service")
        void shouldUpdateExistingService() {
            // Arrange
            GameService service1 = new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0);
            serviceManager.registerService("battleroyale", service1);

            // Act
            GameService service2 = new GameService("BattleRoyale-1", GameState.STARTING, 25, 100, 25);
            serviceManager.registerService("battleroyale", service2);

            // Assert
            List<GameService> services = serviceManager.getServices("battleroyale");
            assertEquals(1, services.size());
            assertEquals(GameState.STARTING, services.get(0).getState());
            assertEquals(25, services.get(0).getPlayerCount());
        }

        @Test
        @DisplayName("Should handle multiple services")
        void shouldHandleMultipleServices() {
            // Arrange & Act
            serviceManager.registerService("battleroyale",
                    new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0));
            serviceManager.registerService("battleroyale",
                    new GameService("BattleRoyale-2", GameState.ACTIVE, 50, 100, 45));
            serviceManager.registerService("battleroyale",
                    new GameService("BattleRoyale-3", GameState.ENDING, 2, 100, 2));

            // Assert
            List<GameService> services = serviceManager.getServices("battleroyale");
            assertEquals(3, services.size());
        }

        @Test
        @DisplayName("Should remove stale services")
        void shouldRemoveStaleServices() throws InterruptedException {
            // Arrange
            GameService service = new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0);
            serviceManager.registerService("battleroyale", service);

            // Act - Wait longer to ensure the service is definitely stale
            Thread.sleep(100); // Wait 100ms
            serviceManager.removeStaleServices(50); // 50ms threshold

            // Assert
            List<GameService> services = serviceManager.getServices("battleroyale");
            assertEquals(0, services.size(), "Stale service should be removed after 100ms with 50ms threshold");
        }

        @Test
        @DisplayName("Should not remove active services")
        void shouldNotRemoveActiveServices() {
            // Arrange
            GameService service = new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0);
            serviceManager.registerService("battleroyale", service);

            // Act - Check immediately (service just created)
            serviceManager.removeStaleServices(1000); // 1 second threshold - service is fresh

            // Assert
            List<GameService> services = serviceManager.getServices("battleroyale");
            assertEquals(1, services.size(), "Active service should not be removed");
        }

        @Test
        @DisplayName("Should clear all services for gamemode")
        void shouldClearAllServicesForGamemode() {
            // Arrange
            serviceManager.registerService("battleroyale",
                    new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0));
            serviceManager.registerService("battleroyale",
                    new GameService("BattleRoyale-2", GameState.WAITING, 0, 100, 0));
            serviceManager.registerService("skywars",
                    new GameService("SkyWars-1", GameState.WAITING, 0, 12, 0));

            // Act
            serviceManager.clearServices("battleroyale");

            // Assert
            assertEquals(0, serviceManager.getServices("battleroyale").size());
            assertEquals(1, serviceManager.getServices("skywars").size(),
                    "Other gamemodes should not be affected");
        }
    }

    // ===== Redis Message Parser Tests =====

    @Nested
    @DisplayName("Redis Message Parser Tests")
    class RedisMessageParserTests {

        private RedisMessageParser parser;

        @BeforeEach
        void setUp() {
            parser = new RedisMessageParser();
        }

        @Test
        @DisplayName("Should parse valid state update message")
        void shouldParseValidStateUpdate() {
            // Arrange
            String json = """
                {
                    "service": "BattleRoyale-1",
                    "state": "WAITING",
                    "players": 10,
                    "maxPlayers": 100,
                    "alive": 10,
                    "timestamp": 1234567890
                }
                """;

            // Act
            Optional<GameService> result = parser.parseStateUpdate(json);

            // Assert
            assertTrue(result.isPresent());
            GameService service = result.get();
            assertEquals("BattleRoyale-1", service.getName());
            assertEquals(GameState.WAITING, service.getState());
            assertEquals(10, service.getPlayerCount());
            assertEquals(100, service.getMaxPlayers());
            assertEquals(10, service.getAliveCount());
        }

        @Test
        @DisplayName("Should handle all game states")
        void shouldHandleAllGameStates() {
            // Test each state
            String[] states = {"WAITING", "STARTING", "ACTIVE", "DEATHMATCH", "ENDING"};
            GameState[] expectedStates = {
                    GameState.WAITING,
                    GameState.STARTING,
                    GameState.ACTIVE,
                    GameState.DEATHMATCH,
                    GameState.ENDING
            };

            for (int i = 0; i < states.length; i++) {
                String json = String.format("""
                    {
                        "service": "BattleRoyale-1",
                        "state": "%s",
                        "players": 50,
                        "maxPlayers": 100,
                        "alive": 45
                    }
                    """, states[i]);

                Optional<GameService> result = parser.parseStateUpdate(json);
                assertTrue(result.isPresent());
                assertEquals(expectedStates[i], result.get().getState(),
                        "State " + states[i] + " should parse correctly");
            }
        }

        @Test
        @DisplayName("Should parse heartbeat message")
        void shouldParseHeartbeatMessage() {
            // Arrange
            String json = """
                {
                    "service": "BattleRoyale-1",
                    "players": 25,
                    "timestamp": 1234567890
                }
                """;

            // Act
            Optional<String> result = parser.parseHeartbeat(json);

            // Assert
            assertTrue(result.isPresent());
            assertEquals("BattleRoyale-1", result.get());
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            // Arrange
            String malformedJson = "{ invalid json }";

            // Act
            Optional<GameService> result = parser.parseStateUpdate(malformedJson);

            // Assert
            assertFalse(result.isPresent(), "Malformed JSON should return empty Optional");
        }

        @Test
        @DisplayName("Should handle missing required fields")
        void shouldHandleMissingRequiredFields() {
            // Arrange - missing 'service' field
            String json = """
                {
                    "state": "WAITING",
                    "players": 10
                }
                """;

            // Act
            Optional<GameService> result = parser.parseStateUpdate(json);

            // Assert
            assertFalse(result.isPresent(), "Missing required fields should return empty Optional");
        }

        @Test
        @DisplayName("Should handle null or empty strings")
        void shouldHandleNullOrEmptyStrings() {
            // Act & Assert
            assertFalse(parser.parseStateUpdate(null).isPresent());
            assertFalse(parser.parseStateUpdate("").isPresent());
            assertFalse(parser.parseStateUpdate("   ").isPresent());

            assertFalse(parser.parseHeartbeat(null).isPresent());
            assertFalse(parser.parseHeartbeat("").isPresent());
        }
    }

    // ===== GameService Model Tests =====

    @Nested
    @DisplayName("GameService Model Tests")
    class GameServiceTests {

        @Test
        @DisplayName("Should create service with all fields")
        void shouldCreateServiceWithAllFields() {
            // Act
            GameService service = new GameService(
                    "BattleRoyale-1",
                    GameState.ACTIVE,
                    75,
                    100,
                    70
            );

            // Assert
            assertEquals("BattleRoyale-1", service.getName());
            assertEquals(GameState.ACTIVE, service.getState());
            assertEquals(75, service.getPlayerCount());
            assertEquals(100, service.getMaxPlayers());
            assertEquals(70, service.getAliveCount());
            assertNotNull(service.getLastUpdate());
        }

        @Test
        @DisplayName("Should calculate fill percentage")
        void shouldCalculateFillPercentage() {
            // Arrange
            GameService service = new GameService("BattleRoyale-1", GameState.WAITING, 50, 100, 50);

            // Act
            double fillPercentage = service.getFillPercentage();

            // Assert
            assertEquals(50.0, fillPercentage, 0.01);
        }

        @Test
        @DisplayName("Should determine if joinable")
        void shouldDetermineIfJoinable() {
            // Waiting state - joinable
            GameService waiting = new GameService("BR-1", GameState.WAITING, 10, 100, 10);
            assertTrue(waiting.isJoinable());

            // Starting state - joinable
            GameService starting = new GameService("BR-1", GameState.STARTING, 25, 100, 25);
            assertTrue(starting.isJoinable());

            // Active state - not joinable
            GameService active = new GameService("BR-1", GameState.ACTIVE, 50, 100, 45);
            assertFalse(active.isJoinable());

            // Full server - not joinable
            GameService full = new GameService("BR-1", GameState.WAITING, 100, 100, 100);
            assertFalse(full.isJoinable());

            // Ending state - not joinable
            GameService ending = new GameService("BR-1", GameState.ENDING, 2, 100, 2);
            assertFalse(ending.isJoinable());
        }

        @Test
        @DisplayName("Should determine if stale")
        void shouldDetermineIfStale() throws InterruptedException {
            // Arrange
            GameService service = new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0);

            // Act & Assert - Fresh service should not be stale
            assertFalse(service.isStale(1000), "Fresh service should not be stale");

            // Wait for service to become stale
            Thread.sleep(60); // Wait 60ms
            assertTrue(service.isStale(50), "Service older than 50ms should be stale");

            // Service within threshold should not be stale
            assertFalse(service.isStale(100), "Service within 100ms threshold should not be stale");
        }

        @Test
        @DisplayName("Should update service state")
        void shouldUpdateServiceState() {
            // Arrange
            GameService service = new GameService("BattleRoyale-1", GameState.WAITING, 0, 100, 0);
            long initialUpdate = service.getLastUpdate();

            // Act
            try {
                Thread.sleep(10); // Ensure time difference
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            service.updateState(GameState.STARTING, 25, 25);

            // Assert
            assertEquals(GameState.STARTING, service.getState());
            assertEquals(25, service.getPlayerCount());
            assertEquals(25, service.getAliveCount());
            assertTrue(service.getLastUpdate() > initialUpdate, "Last update timestamp should increase");
        }

        @Test
        @DisplayName("Should handle edge cases in player counts")
        void shouldHandleEdgeCasesInPlayerCounts() {
            // Zero players
            GameService empty = new GameService("BR-1", GameState.WAITING, 0, 100, 0);
            assertEquals(0.0, empty.getFillPercentage(), 0.01);
            assertTrue(empty.isJoinable());

            // Negative players (invalid, but should handle)
            GameService invalid = new GameService("BR-1", GameState.WAITING, -5, 100, 0);
            assertTrue(invalid.getPlayerCount() < 0);

            // More players than max (edge case)
            GameService overfull = new GameService("BR-1", GameState.ACTIVE, 105, 100, 100);
            assertTrue(overfull.getFillPercentage() > 100.0);
            assertFalse(overfull.isJoinable());
        }
    }

    // ===== Service Priority and Sorting Tests =====

    @Nested
    @DisplayName("Service Priority and Sorting Tests")
    class ServiceSortingTests {

        @Test
        @DisplayName("Should sort services by priority (joinable first)")
        void shouldSortServicesByPriority() {
            // Arrange
            List<GameService> services = new ArrayList<>();
            services.add(new GameService("BR-1", GameState.ACTIVE, 75, 100, 70)); // Not joinable
            services.add(new GameService("BR-2", GameState.WAITING, 10, 100, 10)); // Joinable
            services.add(new GameService("BR-3", GameState.ENDING, 2, 100, 2)); // Not joinable
            services.add(new GameService("BR-4", GameState.STARTING, 25, 100, 25)); // Joinable

            // Act
            services.sort(Comparator.comparing(GameService::isJoinable).reversed()
                    .thenComparing(GameService::getPlayerCount));

            // Assert
            assertTrue(services.get(0).isJoinable(), "First service should be joinable");
            assertTrue(services.get(1).isJoinable(), "Second service should be joinable");
            assertFalse(services.get(2).isJoinable(), "Third service should not be joinable");
            assertFalse(services.get(3).isJoinable(), "Fourth service should not be joinable");
        }

        @Test
        @DisplayName("Should sort joinable services by player count")
        void shouldSortJoinableServicesByPlayerCount() {
            // Arrange
            List<GameService> services = new ArrayList<>();
            services.add(new GameService("BR-1", GameState.WAITING, 50, 100, 50));
            services.add(new GameService("BR-2", GameState.WAITING, 10, 100, 10));
            services.add(new GameService("BR-3", GameState.WAITING, 75, 100, 75));
            services.add(new GameService("BR-4", GameState.WAITING, 25, 100, 25));

            // Act
            services.sort(Comparator.comparing(GameService::getPlayerCount).reversed());

            // Assert
            assertEquals(75, services.get(0).getPlayerCount());
            assertEquals(50, services.get(1).getPlayerCount());
            assertEquals(25, services.get(2).getPlayerCount());
            assertEquals(10, services.get(3).getPlayerCount());
        }
    }

    // ===== Integration Tests (without actual Bukkit) =====

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should handle complete service lifecycle")
        void shouldHandleCompleteServiceLifecycle() {
            // Arrange
            GameServiceManager manager = new GameServiceManager();
            RedisMessageParser parser = new RedisMessageParser();

            // Act 1: Service starts (WAITING)
            String message1 = """
                {
                    "service": "BattleRoyale-1",
                    "state": "WAITING",
                    "players": 0,
                    "maxPlayers": 100,
                    "alive": 0
                }
                """;
            parser.parseStateUpdate(message1)
                    .ifPresent(service -> manager.registerService("battleroyale", service));

            // Assert 1
            assertEquals(1, manager.getServices("battleroyale").size());
            assertEquals(GameState.WAITING, manager.getServices("battleroyale").get(0).getState());

            // Act 2: Players join (STARTING)
            String message2 = """
                {
                    "service": "BattleRoyale-1",
                    "state": "STARTING",
                    "players": 25,
                    "maxPlayers": 100,
                    "alive": 25
                }
                """;
            parser.parseStateUpdate(message2)
                    .ifPresent(service -> manager.registerService("battleroyale", service));

            // Assert 2
            assertEquals(GameState.STARTING, manager.getServices("battleroyale").get(0).getState());
            assertEquals(25, manager.getServices("battleroyale").get(0).getPlayerCount());

            // Act 3: Game starts (ACTIVE)
            String message3 = """
                {
                    "service": "BattleRoyale-1",
                    "state": "ACTIVE",
                    "players": 100,
                    "maxPlayers": 100,
                    "alive": 100
                }
                """;
            parser.parseStateUpdate(message3)
                    .ifPresent(service -> manager.registerService("battleroyale", service));

            // Assert 3
            assertEquals(GameState.ACTIVE, manager.getServices("battleroyale").get(0).getState());
            assertFalse(manager.getServices("battleroyale").get(0).isJoinable());

            // Act 4: Game ends (ENDING)
            String message4 = """
                {
                    "service": "BattleRoyale-1",
                    "state": "ENDING",
                    "players": 1,
                    "maxPlayers": 100,
                    "alive": 1
                }
                """;
            parser.parseStateUpdate(message4)
                    .ifPresent(service -> manager.registerService("battleroyale", service));

            // Assert 4
            assertEquals(GameState.ENDING, manager.getServices("battleroyale").get(0).getState());
            assertEquals(1, manager.getServices("battleroyale").get(0).getPlayerCount());
        }

        @Test
        @DisplayName("Should handle multiple concurrent gamemodes")
        void shouldHandleMultipleConcurrentGamemodes() {
            // Arrange
            GameServiceManager manager = new GameServiceManager();
            RedisMessageParser parser = new RedisMessageParser();

            // Act - Add BattleRoyale services
            parser.parseStateUpdate("""
                {"service": "BattleRoyale-1", "state": "WAITING", "players": 10, "maxPlayers": 100, "alive": 10}
                """).ifPresent(s -> manager.registerService("battleroyale", s));

            parser.parseStateUpdate("""
                {"service": "BattleRoyale-2", "state": "ACTIVE", "players": 75, "maxPlayers": 100, "alive": 70}
                """).ifPresent(s -> manager.registerService("battleroyale", s));

            // Act - Add SkyWars services
            parser.parseStateUpdate("""
                {"service": "SkyWars-1", "state": "WAITING", "players": 5, "maxPlayers": 12, "alive": 5}
                """).ifPresent(s -> manager.registerService("skywars", s));

            // Assert
            assertEquals(2, manager.getServices("battleroyale").size());
            assertEquals(1, manager.getServices("skywars").size());

            // Services should be independent
            GameService br1 = manager.getServices("battleroyale").stream()
                    .filter(s -> s.getName().equals("BattleRoyale-1"))
                    .findFirst().orElseThrow();
            assertEquals(10, br1.getPlayerCount());

            GameService sw1 = manager.getServices("skywars").get(0);
            assertEquals(5, sw1.getPlayerCount());
        }
    }

    // ===== Helper Classes (Simplified versions for testing) =====

    /**
     * Simplified GameServiceManager for testing.
     */
    static class GameServiceManager {
        private final Map<String, Map<String, GameService>> services = new ConcurrentHashMap<>();

        public void registerService(String gamemode, GameService service) {
            services.computeIfAbsent(gamemode, k -> new ConcurrentHashMap<>())
                    .put(service.getName(), service);
        }

        public List<GameService> getServices(String gamemode) {
            return new ArrayList<>(services.getOrDefault(gamemode, Map.of()).values());
        }

        public void removeStaleServices(long staleThresholdMs) {
            services.values().forEach(gamemodeServices ->
                    gamemodeServices.entrySet().removeIf(entry ->
                            entry.getValue().isStale(staleThresholdMs)));
        }

        public void clearServices(String gamemode) {
            services.remove(gamemode);
        }
    }

    /**
     * Simplified Redis message parser for testing.
     */
    static class RedisMessageParser {

        public Optional<GameService> parseStateUpdate(String json) {
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }

            try {
                // Simple JSON parsing (in real code, use Gson)
                if (!json.contains("service") || !json.contains("state")) {
                    return Optional.empty();
                }

                String name = extractValue(json, "service");
                String stateStr = extractValue(json, "state");
                int players = extractInt(json, "players");
                int maxPlayers = extractInt(json, "maxPlayers");
                int alive = extractInt(json, "alive");

                GameState state = GameState.valueOf(stateStr);

                return Optional.of(new GameService(name, state, players, maxPlayers, alive));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        public Optional<String> parseHeartbeat(String json) {
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }

            try {
                String name = extractValue(json, "service");
                return Optional.of(name);
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        private String extractValue(String json, String key) {
            int keyIndex = json.indexOf("\"" + key + "\"");
            int valueStart = json.indexOf(":", keyIndex) + 1;
            int valueEnd = json.indexOf(",", valueStart);
            if (valueEnd == -1) valueEnd = json.indexOf("}", valueStart);

            String value = json.substring(valueStart, valueEnd).trim();
            return value.replaceAll("\"", "").trim();
        }

        private int extractInt(String json, String key) {
            try {
                return Integer.parseInt(extractValue(json, key));
            } catch (Exception e) {
                return 0;
            }
        }
    }
}

/**
 * GameService model for testing.
 */
class GameService {
    private final String name;
    private GameState state;
    private int playerCount;
    private final int maxPlayers;
    private int aliveCount;
    private long lastUpdate;

    public GameService(String name, GameState state, int playerCount, int maxPlayers, int aliveCount) {
        this.name = name;
        this.state = state;
        this.playerCount = playerCount;
        this.maxPlayers = maxPlayers;
        this.aliveCount = aliveCount;
        this.lastUpdate = System.currentTimeMillis();
    }

    public void updateState(GameState state, int playerCount, int aliveCount) {
        this.state = state;
        this.playerCount = playerCount;
        this.aliveCount = aliveCount;
        this.lastUpdate = System.currentTimeMillis();
    }

    public boolean isJoinable() {
        return (state == GameState.WAITING || state == GameState.STARTING)
                && playerCount < maxPlayers;
    }

    public boolean isStale(long thresholdMs) {
        return (System.currentTimeMillis() - lastUpdate) > thresholdMs;
    }

    public double getFillPercentage() {
        return (double) playerCount / maxPlayers * 100.0;
    }

    // Getters
    public String getName() { return name; }
    public GameState getState() { return state; }
    public int getPlayerCount() { return playerCount; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getAliveCount() { return aliveCount; }
    public long getLastUpdate() { return lastUpdate; }
}

/**
 * GameState enum for testing.
 */
enum GameState {
    WAITING,
    STARTING,
    ACTIVE,
    DEATHMATCH,
    ENDING
}