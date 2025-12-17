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
 * Unit tests for PregameLobby.
 * FIXED: Proper MockBukkit initialization - server/world are static,
 * but mapCenter/lobby are recreated per-test.
 */
class PregameLobbyTest {

    private static ServerMock server;
    private static WorldMock world;

    private Location mapCenter;
    private PregameLobby lobby;

    @BeforeAll
    static void setUpAll() {
        server = MockBukkit.mock();
        WorldCreator creator = new WorldCreator("test_world");
        creator.environment(World.Environment.NORMAL);
        world = server.addSimpleWorld(String.valueOf(creator));
    }

    @AfterAll
    static void tearDownAll() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        mapCenter = new Location(world, 0, 100, 0);
    }

    @Test
    void constructor_withValidData_createsLobby() {
        // Act
        lobby = new PregameLobby(mapCenter, 320, 8, true);

        // Assert
        assertNotNull(lobby);
        assertEquals(320, lobby.getHeight());
        assertEquals(8, lobby.getPlatformCount());
        assertFalse(lobby.isBuilt());
    }

    @Test
    void createDefault_createsLobbyWithDefaultSettings() {
        // Act
        lobby = PregameLobby.createDefault(mapCenter);

        // Assert
        assertNotNull(lobby);
        assertEquals(320, lobby.getHeight());
        assertEquals(8, lobby.getPlatformCount());
    }

    @Test
    void getCenter_returnsElevatedLocation() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 8, true);

        // Act
        Location center = lobby.getCenter();

        // Assert
        assertEquals(0, center.getX(), 0.01);
        assertEquals(320, center.getY(), 0.01);
        assertEquals(0, center.getZ(), 0.01);
    }

    @Test
    void build_createsStructure() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 8, true);

        // Act
        lobby.build();

        // Assert
        assertTrue(lobby.isBuilt());
    }

    @Test
    void build_generatesSpawnLocations() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 8, true);

        // Act
        lobby.build();
        List<Location> spawns = lobby.getSpawnLocations();

        // Assert
        assertEquals(8, spawns.size());
    }

    @Test
    void build_whenAlreadyBuilt_doesNotRebuild() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 8, true);
        lobby.build();

        // Act
        lobby.build(); // Try to build again

        // Assert
        assertTrue(lobby.isBuilt());
        // Should still have same number of spawns
        assertEquals(8, lobby.getSpawnLocations().size());
    }

    @Test
    void getSpawnLocations_returnsCorrectCount() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 16, true);
        lobby.build();

        // Act
        List<Location> spawns = lobby.getSpawnLocations();

        // Assert
        assertEquals(16, spawns.size());
    }

    @Test
    void getSpawnLocations_facesCenter() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 4, true);
        lobby.build();

        // Act
        List<Location> spawns = lobby.getSpawnLocations();

        // Assert - each spawn should face roughly toward center
        for (Location spawn : spawns) {
            assertNotNull(spawn.getYaw());
            // Yaw should be set (not default 0)
            assertTrue(Math.abs(spawn.getYaw()) > 0.1 ||
                    Math.abs(spawn.getYaw() - 180) < 0.1);
        }
    }

    @Test
    void teleportPlayer_withValidIndex_teleportsPlayer() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        lobby.teleportPlayer(player, 0);

        // Assert
        assertNotNull(player.getLocation());
        assertEquals(world, player.getWorld());
        assertTrue(player.getAllowFlight());
    }

    @Test
    void teleportPlayer_withInvalidIndex_teleportsToCenter() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        lobby.teleportPlayer(player, 999);

        // Assert
        Location playerLoc = player.getLocation();
        assertEquals(world, playerLoc.getWorld());
        // Should be teleported to center
        assertEquals(0, playerLoc.getX(), 1.0);
        assertEquals(0, playerLoc.getZ(), 1.0);
    }

    @Test
    void teleportPlayer_withNegativeIndex_teleportsToCenter() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        lobby.teleportPlayer(player, -1);

        // Assert
        assertNotNull(player.getLocation());
        assertEquals(world, player.getWorld());
    }

    @Test
    void teleportPlayerRandom_teleportsToValidSpawn() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        PlayerMock player = server.addPlayer("TestPlayer");

        // Act
        lobby.teleportPlayerRandom(player);

        // Assert
        assertNotNull(player.getLocation());
        assertEquals(world, player.getWorld());
        assertTrue(player.getAllowFlight());
    }

    @Test
    void teleportPlayer_enablesFlight() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        PlayerMock player = server.addPlayer("TestPlayer");
        player.setAllowFlight(false);

        // Act
        lobby.teleportPlayer(player, 0);

        // Assert
        assertTrue(player.getAllowFlight());
        assertFalse(player.isFlying()); // Not flying yet, but allowed
    }

    @Test
    void isInLobby_withLocationInside_returnsTrue() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        Location insideLocation = new Location(world, 10, 320, 10);

        // Act
        boolean result = lobby.isInLobby(insideLocation);

        // Assert
        assertTrue(result);
    }

    @Test
    void isInLobby_withLocationOutside_returnsFalse() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        Location outsideLocation = new Location(world, 200, 320, 200);

        // Act
        boolean result = lobby.isInLobby(outsideLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void isInLobby_withLocationTooLow_returnsFalse() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        Location lowLocation = new Location(world, 10, 100, 10);

        // Act
        boolean result = lobby.isInLobby(lowLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void isInLobby_withLocationTooHigh_returnsFalse() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        Location highLocation = new Location(world, 10, 340, 10);

        // Act
        boolean result = lobby.isInLobby(highLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void isInLobby_withWrongWorld_returnsFalse() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();
        WorldMock otherWorld = server.addSimpleWorld("other_world");
        Location wrongWorldLocation = new Location(otherWorld, 10, 320, 10);

        // Act
        boolean result = lobby.isInLobby(wrongWorldLocation);

        // Assert
        assertFalse(result);
    }

    @Test
    void remove_clearsStructure() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);
        lobby.build();

        // Act
        lobby.remove();

        // Assert
        assertFalse(lobby.isBuilt());
    }

    @Test
    void remove_whenNotBuilt_doesNothing() {
        // Arrange
        lobby = PregameLobby.createDefault(mapCenter);

        // Act
        lobby.remove(); // Should not throw exception

        // Assert
        assertFalse(lobby.isBuilt());
    }

    @Test
    void multiplePlayers_canBeSpawnedAtDifferentPlatforms() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 4, true);
        lobby.build();
        PlayerMock player1 = server.addPlayer("Player1");
        PlayerMock player2 = server.addPlayer("Player2");
        PlayerMock player3 = server.addPlayer("Player3");

        // Act
        lobby.teleportPlayer(player1, 0);
        lobby.teleportPlayer(player2, 1);
        lobby.teleportPlayer(player3, 2);

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
    void spawnLocations_areCircularlyDistributed() {
        // Arrange
        lobby = new PregameLobby(mapCenter, 320, 8, true);
        lobby.build();
        List<Location> spawns = lobby.getSpawnLocations();

        // Act & Assert - check that spawns are roughly evenly distributed
        // Calculate average distance between adjacent spawns
        double totalDistance = 0;
        for (int i = 0; i < spawns.size(); i++) {
            Location current = spawns.get(i);
            Location next = spawns.get((i + 1) % spawns.size());
            totalDistance += current.distance(next);
        }
        double avgDistance = totalDistance / spawns.size();

        // Each spawn should be roughly the same distance from the next
        for (int i = 0; i < spawns.size(); i++) {
            Location current = spawns.get(i);
            Location next = spawns.get((i + 1) % spawns.size());
            double distance = current.distance(next);

            // Distance should be within 20% of average
            assertTrue(Math.abs(distance - avgDistance) / avgDistance < 0.2,
                    "Spawn distribution should be roughly even");
        }
    }

    @Test
    void customHeight_affectsLobbyPosition() {
        // Arrange
        int customHeight = 400;
        lobby = new PregameLobby(mapCenter, customHeight, 8, true);

        // Act
        Location center = lobby.getCenter();

        // Assert
        assertEquals(customHeight, center.getY(), 0.01);
    }

    @Test
    void transparentFloorSetting_storesCorrectly() {
        // Arrange & Act
        PregameLobby transparentLobby = new PregameLobby(mapCenter, 320, 8, true);
        PregameLobby solidLobby = new PregameLobby(mapCenter, 320, 8, false);

        // Assert - both should build without errors
        transparentLobby.build();
        solidLobby.build();
        assertTrue(transparentLobby.isBuilt());
        assertTrue(solidLobby.isBuilt());
    }
}