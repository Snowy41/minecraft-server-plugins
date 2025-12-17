package com.yourserver.battleroyale.player;

import com.yourserver.battleroyale.game.Game;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpectatorManagerTest {

    @Mock
    private Game game;

    @Mock
    private Player player;

    @Mock
    private Location location;

    private SpectatorManager spectatorManager;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        spectatorManager = new SpectatorManager(game);
        playerUuid = UUID.randomUUID();

        // Only stub the basics - use lenient to avoid UnnecessaryStubbingException
        lenient().when(player.getUniqueId()).thenReturn(playerUuid);
        lenient().when(player.getName()).thenReturn("TestPlayer");
        lenient().when(player.getLocation()).thenReturn(location);
        lenient().when(player.getActivePotionEffects()).thenReturn(new ArrayList<>());
        lenient().when(game.getAlivePlayers()).thenReturn(Collections.emptySet());
        lenient().when(game.getOnlinePlayers()).thenReturn(Collections.emptyList());
        lenient().when(game.getAliveCount()).thenReturn(0);
    }

    @Test
    void testMakeSpectator() {
        spectatorManager.makeSpectator(player);

        verify(player).setGameMode(GameMode.SPECTATOR);
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
        verify(player).addPotionEffect(any(PotionEffect.class));

        assertTrue(spectatorManager.isSpectator(playerUuid));
        assertEquals(1, spectatorManager.getSpectatorCount());
    }

    @Test
    void testMakeSpectatorTwice() {
        spectatorManager.makeSpectator(player);
        spectatorManager.makeSpectator(player);

        assertEquals(1, spectatorManager.getSpectatorCount());
        verify(player, times(1)).setGameMode(GameMode.SPECTATOR);
    }

    @Test
    void testIsSpectatorFalseByDefault() {
        assertFalse(spectatorManager.isSpectator(playerUuid));
    }

    @Test
    void testIsSpectatorTrueAfterMaking() {
        spectatorManager.makeSpectator(player);
        assertTrue(spectatorManager.isSpectator(playerUuid));
    }

    @Test
    void testGetSpectatorCountZeroByDefault() {
        assertEquals(0, spectatorManager.getSpectatorCount());
    }

    @Test
    void testGetSpectatorCountIncreases() {
        spectatorManager.makeSpectator(player);
        assertEquals(1, spectatorManager.getSpectatorCount());

        Player player2 = mock(Player.class);
        UUID uuid2 = UUID.randomUUID();
        when(player2.getUniqueId()).thenReturn(uuid2);
        when(player2.getName()).thenReturn("Player2");
        when(player2.getLocation()).thenReturn(location);
        when(player2.getActivePotionEffects()).thenReturn(new ArrayList<>());

        spectatorManager.makeSpectator(player2);
        assertEquals(2, spectatorManager.getSpectatorCount());
    }

    @Test
    void testRemoveSpectator() {
        spectatorManager.makeSpectator(player);
        spectatorManager.removeSpectator(player);

        verify(player).setGameMode(GameMode.SURVIVAL);
        verify(player).setFlying(false);
        verify(player).setAllowFlight(false);

        assertFalse(spectatorManager.isSpectator(playerUuid));
        assertEquals(0, spectatorManager.getSpectatorCount());
    }

    @Test
    void testClearAllWithNoSpectators() {
        spectatorManager.clearAll();
        assertEquals(0, spectatorManager.getSpectatorCount());
    }

    @Test
    void testClearAllWithOneSpectator() {
        when(player.isOnline()).thenReturn(true);

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(playerUuid)).thenReturn(player);

            spectatorManager.makeSpectator(player);
            assertEquals(1, spectatorManager.getSpectatorCount());

            spectatorManager.clearAll();

            assertEquals(0, spectatorManager.getSpectatorCount());
            verify(player).setGameMode(GameMode.SURVIVAL);
        }
    }

    @Test
    void testTeleportSpectatorsToArena() {
        Location arenaCenter = mock(Location.class);
        Location clonedLocation = mock(Location.class);

        when(arenaCenter.clone()).thenReturn(clonedLocation);
        when(clonedLocation.add(anyDouble(), anyDouble(), anyDouble())).thenReturn(clonedLocation);
        when(player.isOnline()).thenReturn(true);

        try (var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(playerUuid)).thenReturn(player);

            spectatorManager.makeSpectator(player);
            spectatorManager.teleportSpectatorsToArena(arenaCenter);

            verify(player, atLeastOnce()).teleport(any(Location.class));
            verify(player).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void testSpectatePlayerWhenNotSpectator() {
        Player target = mock(Player.class);

        spectatorManager.spectatePlayer(player, target);

        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void testSpectatePlayerWhenIsSpectator() {
        Player target = mock(Player.class);
        UUID targetUuid = UUID.randomUUID();

        when(target.getUniqueId()).thenReturn(targetUuid);
        when(target.getName()).thenReturn("Target");
        when(target.getLocation()).thenReturn(location);
        when(game.isPlayerAlive(targetUuid)).thenReturn(true);

        spectatorManager.makeSpectator(player);
        spectatorManager.spectatePlayer(player, target);

        verify(player).teleport(location);
        verify(player).setSpectatorTarget(target);
        verify(player).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void testSpectateEliminatedPlayer() {
        Player target = mock(Player.class);
        UUID targetUuid = UUID.randomUUID();

        when(target.getUniqueId()).thenReturn(targetUuid);
        when(game.isPlayerAlive(targetUuid)).thenReturn(false);

        spectatorManager.makeSpectator(player);
        spectatorManager.spectatePlayer(player, target);

        verify(player).sendActionBar(any(net.kyori.adventure.text.Component.class));
        verify(player, never()).teleport(any(Location.class));
    }

    @Test
    void testMultipleSpectators() {
        Player player2 = mock(Player.class);
        UUID uuid2 = UUID.randomUUID();

        when(player2.getUniqueId()).thenReturn(uuid2);
        when(player2.getName()).thenReturn("Player2");
        when(player2.getLocation()).thenReturn(location);
        when(player2.getActivePotionEffects()).thenReturn(new ArrayList<>());

        spectatorManager.makeSpectator(player);
        spectatorManager.makeSpectator(player2);

        assertTrue(spectatorManager.isSpectator(playerUuid));
        assertTrue(spectatorManager.isSpectator(uuid2));
        assertEquals(2, spectatorManager.getSpectatorCount());
    }
}