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
 */
class DeathmatchArenaTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location center;
    private DeathmatchArena arena;

    @BeforeAll
    static void setUpAll() {
        System.setProperty("mockbukkit.test", "true");

        server = MockBukkit.mock();
        WorldCreator creator = new WorldCreator("deathmatch_world");
        creator.environment(World.Environment.NORMAL);
        world = server.addSimpleWorld(String.valueOf(creator));
    }

    @AfterAll
    static void tearDownAll() {
        System.clearProperty("mockbukkit.test");
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        center = new Location(world, 0, 100, 0);
    }

    @Test
    void constructor_withValidData_createsArena() {
        arena = new DeathmatchArena(center, 25);

        assertNotNull(arena);
        assertEquals(25, arena.getSize());
        assertFalse(arena.isBuilt());
    }

    @Test
    void createDefault_createsArenaWithDefaultSettings() {
        arena = DeathmatchArena.createDefault(center);

        assertNotNull(arena);
        assertEquals(25, arena.getSize());
    }

    @Test
    void getCenter_returnsCenterLocation() {
        arena = new DeathmatchArena(center, 25);

        Location arenaCenter = arena.getCenter();

        assertEquals(0, arenaCenter.getX(), 0.01);
        assertEquals(100, arenaCenter.getY(), 0.01);
        assertEquals(0, arenaCenter.getZ(), 0.01);
    }

    @Test
    void build_createsStructure() {
        arena = new DeathmatchArena(center, 25);

        arena.build();

        assertTrue(arena.isBuilt());
    }

    @Test
    void build_generatesSpawnPoints() {
        arena = new DeathmatchArena(center, 25);

        arena.build();
        List<Location> spawns = arena.getSpawnPoints();

        assertEquals(16, spawns.size()); // Default 16 spawn points
    }

    @Test
    void build_whenAlreadyBuilt_doesNotRebuild() {
        arena = new DeathmatchArena(center, 25);
        arena.build();

        arena.build();

        assertTrue(arena.isBuilt());
    }

    @Test
    void getSpawnPoints_returnsCorrectCount() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        List<Location> spawns = arena.getSpawnPoints();

        assertEquals(16, spawns.size());
    }

    @Test
    void spawnPoints_faceCenter() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        List<Location> spawns = arena.getSpawnPoints();

        for (Location spawn : spawns) {
            assertNotNull(spawn.getYaw());
            assertTrue(Math.abs(spawn.getYaw()) > 0.1);
        }
    }

    @Test
    void spawnPoints_areCircularlyDistributed() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        List<Location> spawns = arena.getSpawnPoints();

        for (Location spawn : spawns) {
            double distance = Math.sqrt(
                    Math.pow(spawn.getX() - center.getX(), 2) +
                            Math.pow(spawn.getZ() - center.getZ(), 2)
            );
            assertTrue(distance > 10 && distance < 15,
                    "Spawn should be at half radius: " + distance);
        }
    }

    @Test
    void teleportPlayer_withValidIndex_teleportsPlayer() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        List<Location> spawns = arena.getSpawnPoints();

        assertEquals(16, spawns.size());
        assertTrue(arena.isBuilt());
    }

    @Test
    void teleportPlayer_withInvalidIndex_teleportsToCenter() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        assertNotNull(arena.getCenter());
        assertEquals(world, arena.getCenter().getWorld());
    }

    @Test
    void teleportPlayer_resetsPlayerState() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        assertTrue(arena.isBuilt());
        assertEquals(16, arena.getSpawnPoints().size());
    }

    @Test
    void isInArena_withLocationInside_returnsTrue() {
        arena = new DeathmatchArena(center, 25);
        Location insideLocation = new Location(world, 10, 100, 10);

        boolean result = arena.isInArena(insideLocation);

        assertTrue(result);
    }

    @Test
    void isInArena_withLocationOutside_returnsFalse() {
        arena = new DeathmatchArena(center, 25);
        Location outsideLocation = new Location(world, 50, 100, 50);

        boolean result = arena.isInArena(outsideLocation);

        assertFalse(result);
    }

    @Test
    void isInArena_atExactBoundary_returnsTrue() {
        arena = new DeathmatchArena(center, 25);
        Location boundaryLocation = new Location(world, 25, 100, 0);

        boolean result = arena.isInArena(boundaryLocation);

        assertTrue(result);
    }

    @Test
    void isInArena_withWrongWorld_returnsFalse() {
        arena = new DeathmatchArena(center, 25);
        WorldMock otherWorld = server.addSimpleWorld("other_world");
        Location wrongWorldLocation = new Location(otherWorld, 10, 100, 10);

        boolean result = arena.isInArena(wrongWorldLocation);

        assertFalse(result);
    }

    @Test
    void enforceBoundaries_withPlayerOutside_teleportsInside() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        Location outsideLocation = new Location(world, 50, 100, 0);
        assertFalse(arena.isInArena(outsideLocation));

        assertTrue(arena.isInArena(center));
    }

    @Test
    void enforceBoundaries_withPlayerInside_doesNotMove() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        Location insideLocation = new Location(world, 10, 100, 10);
        assertTrue(arena.isInArena(insideLocation));
    }

    @Test
    void enforceBoundaries_appliesDamage() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        assertTrue(arena.isBuilt());
        assertNotNull(arena.getCenter());
    }

    @Test
    void remove_clearsStructure() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();

        arena.remove();

        assertFalse(arena.isBuilt());
    }

    @Test
    void remove_whenNotBuilt_doesNothing() {
        arena = DeathmatchArena.createDefault(center);

        arena.remove();

        assertFalse(arena.isBuilt());
    }

    @Test
    void multiplePlayers_canBeSpawnedAtDifferentPoints() {
        arena = DeathmatchArena.createDefault(center);
        arena.build();
        List<Location> spawns = arena.getSpawnPoints();

        assertEquals(16, spawns.size());

        Location spawn1 = spawns.get(0);
        Location spawn2 = spawns.get(4);
        Location spawn3 = spawns.get(8);

        assertFalse(spawn1.equals(spawn2));
        assertFalse(spawn2.equals(spawn3));
        assertFalse(spawn1.equals(spawn3));

        for (Location spawn : spawns) {
            assertTrue(arena.isInArena(spawn));
        }
    }

    @Test
    void smallArena_hasCorrectBoundaries() {
        int smallSize = 10;
        arena = new DeathmatchArena(center, smallSize);

        assertTrue(arena.isInArena(new Location(world, 5, 100, 5)));
        assertFalse(arena.isInArena(new Location(world, 15, 100, 15)));
    }

    @Test
    void largeArena_hasCorrectBoundaries() {
        int largeSize = 50;
        arena = new DeathmatchArena(center, largeSize);

        assertTrue(arena.isInArena(new Location(world, 40, 100, 0)));
        assertFalse(arena.isInArena(new Location(world, 60, 100, 0)));
    }

    @Test
    void getSize_returnsCorrectSize() {
        int expectedSize = 30;
        arena = new DeathmatchArena(center, expectedSize);

        int actualSize = arena.getSize();

        assertEquals(expectedSize, actualSize);
    }

    @Test
    void customCenterLocation_affectsSpawnPoints() {
        Location customCenter = new Location(world, 100, 150, 100);
        arena = new DeathmatchArena(customCenter, 25);
        arena.build();

        List<Location> spawns = arena.getSpawnPoints();

        for (Location spawn : spawns) {
            double distance = Math.sqrt(
                    Math.pow(spawn.getX() - 100, 2) +
                            Math.pow(spawn.getZ() - 100, 2)
            );
            assertTrue(distance < 25, "Spawn should be within arena bounds");
        }
    }
}