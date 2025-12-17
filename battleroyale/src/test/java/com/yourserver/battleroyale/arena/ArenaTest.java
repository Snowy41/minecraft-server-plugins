package com.yourserver.battleroyale.arena;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Arena system.
 * FIXED: Proper MockBukkit initialization - server/world are static,
 * but center/config are recreated per-test.
 */
class ArenaTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location center;
    private Arena.ArenaConfig config;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("arena_world");
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        center = new Location(world, 0, 100, 0);
        config = Arena.ArenaConfig.createDefault();
    }

    @Test
    void constructor_withValidData_createsArena() {
        // Act
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Assert
        assertNotNull(arena);
        assertEquals("test-1", arena.getId());
        assertEquals("Test Arena", arena.getName());
        assertEquals(world, arena.getWorld());
        assertEquals(1000, arena.getSize());
    }

    @Test
    void addSpawnPoint_withValidLocation_addsToList() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location spawnPoint = new Location(world, 100, 100, 100);

        // Act
        arena.addSpawnPoint(spawnPoint);

        // Assert
        assertEquals(1, arena.getSpawnPoints().size());
    }

    @Test
    void addLootChestLocation_withValidLocation_addsToList() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location lootLocation = new Location(world, 200, 100, 200);

        // Act
        arena.addLootChestLocation(lootLocation);

        // Assert
        assertTrue(arena.getLootChestLocations().size() > 0);
    }

    @Test
    void getRandomSpawnPoint_withNoSpawns_returnsCenter() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        Location spawn = arena.getRandomSpawnPoint();

        // Assert
        assertNotNull(spawn);
        assertEquals(center.getX(), spawn.getX());
        assertEquals(center.getZ(), spawn.getZ());
    }

    @Test
    void getRandomSpawnPoint_withSpawns_returnsSpawnPoint() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location spawnPoint = new Location(world, 100, 100, 100);
        arena.addSpawnPoint(spawnPoint);

        // Act
        Location spawn = arena.getRandomSpawnPoint();

        // Assert
        assertNotNull(spawn);
        assertEquals(100, spawn.getX());
        assertEquals(100, spawn.getZ());
    }

    @Test
    void getSpawnPoints_withCount_returnsRequestedNumber() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        List<Location> spawns = arena.getSpawnPoints(25);

        // Assert
        assertEquals(25, spawns.size());
    }

    @Test
    void getSpawnPoints_generatesCircularPattern() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        List<Location> spawns = arena.getSpawnPoints(8);

        // Assert
        assertEquals(8, spawns.size());
        // Verify they're spread out (not all at same location)
        Location first = spawns.get(0);
        Location second = spawns.get(1);
        assertTrue(first.distance(second) > 100); // Should be far apart
    }

    @Test
    void getLootChestLocations_withSpawnRate_returnsSubset() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Add 10 loot locations
        for (int i = 0; i < 10; i++) {
            arena.addLootChestLocation(new Location(world, i * 10, 100, i * 10));
        }

        // Act - 50% spawn rate
        List<Location> spawned = arena.getLootChestLocations(0.5);

        // Assert
        assertEquals(5, spawned.size());
    }

    @Test
    void isInArena_withLocationInside_returnsTrue() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location insideLocation = new Location(world, 100, 100, 100);

        // Act
        boolean result = arena.isInArena(insideLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInArena_withLocationOutside_returnsFalse() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 500, config);
        Location outsideLocation = new Location(world, 1000, 100, 1000);

        // Act
        boolean result = arena.isInArena(outsideLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void getDistanceToEdge_withLocationInside_returnsPositive() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location insideLocation = new Location(world, 100, 100, 0);

        // Act
        double distance = arena.getDistanceToEdge(insideLocation);

        // Assert
        assertTrue(distance > 0);
    }

    @Test
    void getPregameLobbyCenter_returnsElevatedLocation() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        Location lobbyCenter = arena.getPregameLobbyCenter();

        // Assert
        assertEquals(center.getX(), lobbyCenter.getX());
        assertEquals(center.getZ(), lobbyCenter.getZ());
        assertEquals(center.getY() + 320, lobbyCenter.getY()); // Default height
    }

    @Test
    void getDeathmatchCenter_returnsCenterLocation() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        Location dmCenter = arena.getDeathmatchCenter();

        // Assert
        assertEquals(center.getX(), dmCenter.getX());
        assertEquals(center.getY(), dmCenter.getY());
        assertEquals(center.getZ(), dmCenter.getZ());
    }

    @Test
    void arenaConfig_createDefault_hasCorrectValues() {
        // Act
        Arena.ArenaConfig defaultConfig = Arena.ArenaConfig.createDefault();

        // Assert
        assertEquals(320, defaultConfig.getPregameLobbyHeight());
        assertEquals(50, defaultConfig.getDeathmatchArenaSize());
        assertEquals(0.7, defaultConfig.getLootChestSpawnRate());
    }

    @Test
    void toString_returnsFormattedString() {
        // Arrange
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        // Act
        String result = arena.toString();

        // Assert
        assertTrue(result.contains("test-1"));
        assertTrue(result.contains("Test Arena"));
        assertTrue(result.contains("size=1000"));
    }
}