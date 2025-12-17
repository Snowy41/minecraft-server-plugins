package com.yourserver.battleroyale.arena;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeathmatchArena.
 * FIXED: Proper MockBukkit initialization - server/world are static,
 * but center/arena are recreated per-test.
 */
class DeathmatchArenaTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location center;
    private DeathmatchArena arena;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        WorldCreator creator = new WorldCreator("deathmatch_world");
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
    }

    @Test
    void constructor_withValidData_createsArena() {
        // Act
        arena = new DeathmatchArena(center, 25);

        // Assert
        assertNotNull(arena);
        assertEquals(25, arena.getSize());
        assertFalse(arena.isBuilt());
    }

    @Test
    void createDefault_createsArenaWithDefaultSettings() {
        // Act
        arena = DeathmatchArena.createDefault(center);

        // Assert
        assertNotNull(arena);
        assertEquals(25, arena.getSize());
    }

    @Test
    void getCenter_returnsCenterLocation() {
        // Arrange
        arena = new DeathmatchArena(center, 25);

        // Act
        Location arenaCenter = arena.getCenter();

        // Assert
        assertEquals(0, arenaCenter.getX(), 0.01);
        assertEquals(100, arenaCenter.getY(), 0.01);
        assertEquals(0, arenaCenter.getZ(), 0.01);
    }

    @Test
    void build_createsStructure() {
        // Arrange
        arena = new DeathmatchArena(center, 25);

        // Act
        arena.build();

        // Assert
        assertTrue(arena.isBuilt());
    }

    @Test
    void build_generatesSpawnPoints() {
        // Arrange
        arena = new DeathmatchArena(center, 25);

        // Act
        arena.build();
        List<Location> spawns = arena.getSpawnPoints();

        // Assert
        assertEquals(16, spawns.size()); // Default 16 spawn points
    }

    @Test
    void build_whenAlreadyBuilt_doesNotRebuild() {
        // Arrange
        arena = new DeathmatchArena(center, 25);
        arena.build();

        // Act
        arena.build(); // Try to build again

        // Assert
        assertTrue(arena.isBuilt());
    }

    @Test
    void getSpawnPoints_returnsCorrectCount() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        // Act
        List<Location> spawns = arena.getSpawnPoints();

        // Assert
        assertEquals(16, spawns.size());
    }

    @Test
    void spawnPoints_faceCenter() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        // Act
        List<Location> spawns = arena.getSpawnPoints();

        // Assert - spawns should face center (yaw should be set)
        for (Location spawn : spawns) {
            assertNotNull(spawn.getYaw());
            // Yaw should not be default 0
            assertTrue(Math.abs(spawn.getYaw()) > 0.1);
        }
    }

    @Test
    void spawnPoints_areCircularlyDistributed() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        List<Location> spawns = arena.getSpawnPoints();

        // Act & Assert - check circular distribution
        for (Location spawn : spawns) {
            double distance = Math.sqrt(
                    Math.pow(spawn.getX() - center.getX(), 2) +
                            Math.pow(spawn.getZ() - center.getZ(), 2)
            );
            // Spawns should be at roughly half the arena size
            assertTrue(distance > 10 && distance < 15,
                    "Spawn should be at half radius: " + distance);
        }
    }

    @Test
    void teleportPlayer_withValidIndex_teleportsPlayer() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        arena.teleportPlayer(player, 0);

        // Assert
        assertNotNull(player.getLocation());
        assertEquals(world, player.getWorld());
        assertEquals(20.0, player.getHealth(), 0.01);
        assertEquals(20, player.getFoodLevel());
    }

    @Test
    void teleportPlayer_withInvalidIndex_teleportsToCenter() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        arena.teleportPlayer(player, 999);

        // Assert
        Location playerLoc = player.getLocation();
        assertEquals(world, playerLoc.getWorld());
        // Should be at center
        assertEquals(0, playerLoc.getX(), 1.0);
        assertEquals(0, playerLoc.getZ(), 1.0);
    }

    @Test
    void teleportPlayer_resetsPlayerState() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Set player to damaged state
        player.setHealth(10.0);
        player.setFoodLevel(10);
        player.setSaturation(5.0f);
        player.setFireTicks(100);

        // Act
        arena.teleportPlayer(player, 0);

        // Assert - player should be fully healed
        assertEquals(20.0, player.getHealth(), 0.01);
        assertEquals(20, player.getFoodLevel());
        assertEquals(20.0f, player.getSaturation(), 0.01f);
        assertEquals(0, player.getFireTicks());
    }

    @Test
    void isInArena_withLocationInside_returnsTrue() {
        // Arrange
        arena = new DeathmatchArena(center, 25);
        Location insideLocation = new Location(world, 10, 100, 10);

        // Act
        boolean result = arena.isInArena(insideLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInArena_withLocationOutside_returnsFalse() {
        // Arrange
        arena = new DeathmatchArena(center, 25);
        Location outsideLocation = new Location(world, 50, 100, 50);

        // Act
        boolean result = arena.isInArena(outsideLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void isInArena_atExactBoundary_returnsTrue() {
        // Arrange
        arena = new DeathmatchArena(center, 25);
        Location boundaryLocation = new Location(world, 25, 100, 0);

        // Act
        boolean result = arena.isInArena(boundaryLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInArena_withWrongWorld_returnsFalse() {
        // Arrange
        arena = new DeathmatchArena(center, 25);
        WorldMock otherWorld = server.addSimpleWorld("other_world");
        Location wrongWorldLocation = new Location(otherWorld, 10, 100, 10);

        // Act
        boolean result = arena.isInArena(wrongWorldLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void enforceBoundaries_withPlayerOutside_teleportsInside() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Place player outside arena
        Location outsideLocation = new Location(world, 50, 100, 0);
        player.teleport(outsideLocation);

        // Act
        arena.enforceBoundaries(player);

        // Assert
        Location newLocation = player.getLocation();
        assertTrue(arena.isInArena(newLocation),
                "Player should be teleported inside arena");
    }

    @Test
    void enforceBoundaries_withPlayerInside_doesNotMove() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Place player inside arena
        Location insideLocation = new Location(world, 10, 100, 10);
        player.teleport(insideLocation);
        Location originalLocation = insideLocation.clone();

        // Act
        arena.enforceBoundaries(player);

        // Assert
        Location newLocation = player.getLocation();
        assertEquals(originalLocation.getX(), newLocation.getX(), 0.1);
        assertEquals(originalLocation.getZ(), newLocation.getZ(), 0.1);
    }

    @Test
    void enforceBoundaries_appliesDamage() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player = server.addPlayer("TestPlayer");
        player.setHealth(20.0);

        // Place player outside arena
        Location outsideLocation = new Location(world, 50, 100, 0);
        player.teleport(outsideLocation);

        // Act
        arena.enforceBoundaries(player);

        // Assert
        assertTrue(player.getHealth() < 20.0,
                "Player should take damage for being outside");
    }

    @Test
    void remove_clearsStructure() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        // Act
        arena.remove();

        // Assert
        assertFalse(arena.isBuilt());
    }

    @Test
    void remove_whenNotBuilt_doesNothing() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);

        // Act
        arena.remove(); // Should not throw exception

        // Assert
        assertFalse(arena.isBuilt());
    }

    @Test
    void multiplePlayers_canBeSpawnedAtDifferentPoints() {
        // Arrange
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        PlayerMock player1 = server.addPlayer("Player1");
        PlayerMock player2 = server.addPlayer("Player2");
        PlayerMock player3 = server.addPlayer("Player3");

        // Act
        arena.teleportPlayer(player1, 0);
        arena.teleportPlayer(player2, 4);
        arena.teleportPlayer(player3, 8);

        // Assert
        Location loc1 = player1.getLocation();
        Location loc2 = player2.getLocation();
        Location loc3 = player3.getLocation();

        // Players should be at different locations
        assertFalse(loc1.equals(loc2));
        assertFalse(loc2.equals(loc3));
        assertFalse(loc1.equals(loc3));
    }

    @Test
    void smallArena_hasCorrectBoundaries() {
        // Arrange
        int smallSize = 10;
        arena = new DeathmatchArena(center, smallSize);

        // Act & Assert
        assertTrue(arena.isInArena(new Location(world, 5, 100, 5)));
        assertFalse(arena.isInArena(new Location(world, 15, 100, 15)));
    }

    @Test
    void largeArena_hasCorrectBoundaries() {
        // Arrange
        int largeSize = 50;
        arena = new DeathmatchArena(center, largeSize);

        // Act & Assert
        assertTrue(arena.isInArena(new Location(world, 40, 100, 0)));
        assertFalse(arena.isInArena(new Location(world, 60, 100, 0)));
    }

    @Test
    void getSize_returnsCorrectSize() {
        // Arrange
        int expectedSize = 30;
        arena = new DeathmatchArena(center, expectedSize);

        // Act
        int actualSize = arena.getSize();

        // Assert
        assertEquals(expectedSize, actualSize);
    }

    @Test
    void customCenterLocation_affectsSpawnPoints() {
        // Arrange
        Location customCenter = new Location(world, 100, 150, 100);
        arena = new DeathmatchArena(customCenter, 25);
        arena.build();

        // Act
        List<Location> spawns = arena.getSpawnPoints();

        // Assert - spawns should be around custom center
        for (Location spawn : spawns) {
            double distance = Math.sqrt(
                    Math.pow(spawn.getX() - 100, 2) +
                            Math.pow(spawn.getZ() - 100, 2)
            );
            assertTrue(distance < 25, "Spawn should be within arena bounds");
        }
    }
}