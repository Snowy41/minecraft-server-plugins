package com.yourserver.battleroyale.listener;

import com.yourserver.battleroyale.game.Game;
import com.yourserver.battleroyale.game.GameManager;
import com.yourserver.battleroyale.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MinimalProtectionListener.
 */
@ExtendWith(MockitoExtension.class)
class MinimalProtectionListenerTest {

    @Mock
    private GameManager gameManager;

    @Mock
    private Game game;

    @Mock
    private Player player;

    @Mock
    private BlockBreakEvent blockBreakEvent;

    @Mock
    private BlockPlaceEvent blockPlaceEvent;

    @Mock
    private FoodLevelChangeEvent foodLevelChangeEvent;

    @Mock
    private PlayerDropItemEvent dropItemEvent;

    @Mock
    private EntityDamageByEntityEvent pvpEvent;

    private MinimalProtectionListener listener;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        listener = new MinimalProtectionListener(gameManager);
        playerUuid = UUID.randomUUID();

        lenient().when(player.getUniqueId()).thenReturn(playerUuid);
        lenient().when(gameManager.getPlayerGame(player)).thenReturn(game);
    }

    // ===== BLOCK BREAK TESTS =====

    @Test
    void testBlockBreakProtectedInWaiting() {
        when(game.getState()).thenReturn(GameState.WAITING);
        when(blockBreakEvent.getPlayer()).thenReturn(player);

        listener.onBlockBreak(blockBreakEvent);

        verify(blockBreakEvent).setCancelled(true);
        verify(player).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void testBlockBreakProtectedInStarting() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(blockBreakEvent.getPlayer()).thenReturn(player);

        listener.onBlockBreak(blockBreakEvent);

        verify(blockBreakEvent).setCancelled(true);
    }

    @Test
    void testBlockBreakAllowedInActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(blockBreakEvent.getPlayer()).thenReturn(player);

        listener.onBlockBreak(blockBreakEvent);

        verify(blockBreakEvent, never()).setCancelled(anyBoolean());
    }

    @Test
    void testBlockBreakAllowedInDeathmatch() {
        when(game.getState()).thenReturn(GameState.DEATHMATCH);
        when(blockBreakEvent.getPlayer()).thenReturn(player);

        listener.onBlockBreak(blockBreakEvent);

        verify(blockBreakEvent, never()).setCancelled(anyBoolean());
    }

    // ===== BLOCK PLACE TESTS =====

    @Test
    void testBlockPlaceProtectedInLobby() {
        when(game.getState()).thenReturn(GameState.WAITING);
        when(blockPlaceEvent.getPlayer()).thenReturn(player);

        listener.onBlockPlace(blockPlaceEvent);

        verify(blockPlaceEvent).setCancelled(true);
    }

    @Test
    void testBlockPlaceAllowedInActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(blockPlaceEvent.getPlayer()).thenReturn(player);

        listener.onBlockPlace(blockPlaceEvent);

        verify(blockPlaceEvent, never()).setCancelled(anyBoolean());
    }

    // ===== PVP TESTS =====

    @Test
    void testPvPProtectedInWaiting() {
        Player attacker = mock(Player.class);

        when(game.getState()).thenReturn(GameState.WAITING);
        when(pvpEvent.getEntity()).thenReturn(player);
        when(pvpEvent.getDamager()).thenReturn(attacker);
        when(gameManager.getPlayerGame(player)).thenReturn(game);

        listener.onPvP(pvpEvent);

        verify(pvpEvent).setCancelled(true);
        verify(attacker).sendActionBar(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    void testPvPAllowedInActive() {
        Player attacker = mock(Player.class);

        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(pvpEvent.getEntity()).thenReturn(player);
        when(pvpEvent.getDamager()).thenReturn(attacker);
        when(gameManager.getPlayerGame(player)).thenReturn(game);

        listener.onPvP(pvpEvent);

        verify(pvpEvent, never()).setCancelled(anyBoolean());
    }

    // ===== HUNGER TESTS =====

    @Test
    void testHungerDisabledInWaiting() {
        when(game.getState()).thenReturn(GameState.WAITING);
        when(foodLevelChangeEvent.getEntity()).thenReturn(player);

        listener.onFoodLevelChange(foodLevelChangeEvent);

        verify(foodLevelChangeEvent).setCancelled(true);
        verify(player).setFoodLevel(20);
        verify(player).setSaturation(20.0f);
    }

    @Test
    void testHungerDisabledInStarting() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(foodLevelChangeEvent.getEntity()).thenReturn(player);

        listener.onFoodLevelChange(foodLevelChangeEvent);

        verify(foodLevelChangeEvent).setCancelled(true);
    }

    @Test
    void testHungerEnabledInActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(foodLevelChangeEvent.getEntity()).thenReturn(player);

        listener.onFoodLevelChange(foodLevelChangeEvent);

        verify(foodLevelChangeEvent, never()).setCancelled(anyBoolean());
    }

    @Test
    void testHungerDisabledInDeathmatch() {
        when(game.getState()).thenReturn(GameState.DEATHMATCH);
        when(foodLevelChangeEvent.getEntity()).thenReturn(player);

        listener.onFoodLevelChange(foodLevelChangeEvent);

        verify(foodLevelChangeEvent).setCancelled(true);
        verify(player).setFoodLevel(20);
    }

    // ===== ITEM DROP TESTS =====

    @Test
    void testItemDropProtectedInLobby() {
        when(game.getState()).thenReturn(GameState.STARTING);
        when(dropItemEvent.getPlayer()).thenReturn(player);

        listener.onItemDrop(dropItemEvent);

        verify(dropItemEvent).setCancelled(true);
    }

    @Test
    void testItemDropAllowedInActive() {
        when(game.getState()).thenReturn(GameState.ACTIVE);
        when(dropItemEvent.getPlayer()).thenReturn(player);

        listener.onItemDrop(dropItemEvent);

        verify(dropItemEvent, never()).setCancelled(anyBoolean());
    }

    // ===== NO GAME TESTS =====

    @Test
    void testNoGameDoesNothing() {
        when(gameManager.getPlayerGame(player)).thenReturn(null);
        when(blockBreakEvent.getPlayer()).thenReturn(player);

        listener.onBlockBreak(blockBreakEvent);

        verify(blockBreakEvent, never()).setCancelled(anyBoolean());
    }
}