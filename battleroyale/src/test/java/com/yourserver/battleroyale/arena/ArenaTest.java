package com.yourserver.battleroyale.arena;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Arena system.
 */
class ArenaTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location center;
    private Arena.ArenaConfig config;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        WorldCreator creator = new WorldCreator("arena_world");
        creator.environment(World.Environment.NORMAL);
        world = server.addSimpleWorld(String.valueOf(creator));
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
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        assertNotNull(arena);
        assertEquals("test-1", arena.getId());
        assertEquals("Test Arena", arena.getName());
        assertEquals(world, arena.getWorld());
        assertEquals(1000, arena.getSize());
    }

    @Test
    void addSpawnPoint_withValidLocation_addsToList() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location spawnPoint = new Location(world, 100, 100, 100);

        arena.addSpawnPoint(spawnPoint);

        assertEquals(1, arena.getSpawnPoints().size());
    }

    @Test
    void addLootChestLocation_withValidLocation_addsToList() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location lootLocation = new Location(world, 200, 100, 200);

        arena.addLootChestLocation(lootLocation);

        assertTrue(arena.getLootChestLocations().size() > 0);
    }

    @Test
    void getRandomSpawnPoint_withNoSpawns_returnsCenter() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        Location spawn = arena.getRandomSpawnPoint();

        assertNotNull(spawn);
        assertEquals(center.getX(), spawn.getX());
        assertEquals(center.getZ(), spawn.getZ());
    }

    @Test
    void getRandomSpawnPoint_withSpawns_returnsSpawnPoint() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location spawnPoint = new Location(world, 100, 100, 100);
        arena.addSpawnPoint(spawnPoint);

        Location spawn = arena.getRandomSpawnPoint();

        assertNotNull(spawn);
        assertEquals(100, spawn.getX());
        assertEquals(100, spawn.getZ());
    }

    @Test
    void getSpawnPoints_withCount_returnsRequestedNumber() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        List<Location> spawns = arena.getSpawnPoints(25);

        assertEquals(25, spawns.size());
    }

    @Test
    void getSpawnPoints_generatesCircularPattern() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        List<Location> spawns = arena.getSpawnPoints(8);

        assertEquals(8, spawns.size());
        // Verify they're spread out (not all at same location)
        Location first = spawns.get(0);
        Location second = spawns.get(1);
        assertTrue(first.distance(second) > 100); // Should be far apart
    }

    @Test
    void getLootChestLocations_withSpawnRate_returnsSubset() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        for (int i = 0; i < 10; i++) {
            arena.addLootChestLocation(new Location(world, i * 10, 100, i * 10));
        }

        List<Location> spawned = arena.getLootChestLocations(0.5);

        assertEquals(5, spawned.size());
    }

    @Test
    void isInArena_withLocationInside_returnsTrue() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location insideLocation = new Location(world, 100, 100, 100);

        boolean result = arena.isInArena(insideLocation);

        assertTrue(result);
    }

    @Test
    void isInArena_withLocationOutside_returnsFalse() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 500, config);
        Location outsideLocation = new Location(world, 1000, 100, 1000);

        boolean result = arena.isInArena(outsideLocation);

        assertFalse(result);
    }

    @Test
    void getDistanceToEdge_withLocationInside_returnsPositive() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);
        Location insideLocation = new Location(world, 100, 100, 0);

        double distance = arena.getDistanceToEdge(insideLocation);

        assertTrue(distance > 0);
    }

    @Test
    void getPregameLobbyCenter_returnsElevatedLocation() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        Location lobbyCenter = arena.getPregameLobbyCenter();

        assertEquals(center.getX(), lobbyCenter.getX());
        assertEquals(center.getZ(), lobbyCenter.getZ());
        assertEquals(center.getY() + 320, lobbyCenter.getY()); // Default height
    }

    @Test
    void getDeathmatchCenter_returnsCenterLocation() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        Location dmCenter = arena.getDeathmatchCenter();

        assertEquals(center.getX(), dmCenter.getX());
        assertEquals(center.getY(), dmCenter.getY());
        assertEquals(center.getZ(), dmCenter.getZ());
    }

    @Test
    void arenaConfig_createDefault_hasCorrectValues() {
        Arena.ArenaConfig defaultConfig = Arena.ArenaConfig.createDefault();

        assertEquals(320, defaultConfig.getPregameLobbyHeight());
        assertEquals(50, defaultConfig.getDeathmatchArenaSize());
        assertEquals(0.7, defaultConfig.getLootChestSpawnRate());
    }

    @Test
    void toString_returnsFormattedString() {
        Arena arena = new Arena("test-1", "Test Arena", world, center, 1000, config);

        String result = arena.toString();

        assertTrue(result.contains("test-1"));
        assertTrue(result.contains("Test Arena"));
        assertTrue(result.contains("size=1000"));
    }
}