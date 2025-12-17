package com.yourserver.battleroyale.combat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CombatManager.
 * Tests combat tracking, kill attribution, and assist tracking.
 */
class CombatManagerTest {

    private CombatManager combatManager;
    private UUID player1;
    private UUID player2;
    private UUID player3;

    @BeforeEach
    void setUp() {
        combatManager = new CombatManager();
        player1 = UUID.randomUUID();
        player2 = UUID.randomUUID();
        player3 = UUID.randomUUID();
    }

    @Test
    void recordDamage_withValidPlayers_storesRecord() {
        // Act
        combatManager.recordDamage(player1, player2, 5.0);

        // Assert
        assertEquals(5.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(5.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void recordDamage_multipleTimes_accumulatesDamage() {
        // Act
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player1, player2, 3.0);
        combatManager.recordDamage(player1, player2, 2.0);

        // Assert
        assertEquals(10.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(10.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void getKiller_withRecentDamage_returnsAttacker() {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);

        // Act
        UUID killer = combatManager.getKiller(player1);

        // Assert
        assertEquals(player2, killer);
    }

    @Test
    void getKiller_withNoDamage_returnsNull() {
        // Act
        UUID killer = combatManager.getKiller(player1);

        // Assert
        assertNull(killer);
    }

    @Test
    void getKiller_afterTimeout_returnsNull() throws InterruptedException {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);

        // Act - wait for timeout (10 seconds)
        Thread.sleep(11000);
        UUID killer = combatManager.getKiller(player1);

        // Assert
        assertNull(killer);
    }

    @Test
    void getAssisters_withMultipleAttackers_returnsAllExceptKiller() {
        // Arrange - multiple players attack player1
        combatManager.recordDamage(player1, player2, 3.0); // First attacker
        combatManager.recordDamage(player1, player3, 2.0); // Second attacker
        combatManager.recordDamage(player1, player2, 5.0); // Killer (last hit)

        // Act
        UUID[] assisters = combatManager.getAssisters(player1, player2);

        // Assert
        assertEquals(1, assisters.length);
        assertEquals(player3, assisters[0]);
    }

    @Test
    void getAssisters_withOnlyKiller_returnsEmpty() {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);

        // Act
        UUID[] assisters = combatManager.getAssisters(player1, player2);

        // Assert
        assertEquals(0, assisters.length);
    }

    @Test
    void getAssisters_withNoKiller_returnsAllAttackers() {
        // Arrange
        combatManager.recordDamage(player1, player2, 3.0);
        combatManager.recordDamage(player1, player3, 2.0);

        // Act
        UUID[] assisters = combatManager.getAssisters(player1, null);

        // Assert
        assertEquals(2, assisters.length);
        assertTrue(assisters[0].equals(player2) || assisters[0].equals(player3));
        assertTrue(assisters[1].equals(player2) || assisters[1].equals(player3));
    }

    @Test
    void isInCombat_withRecentDamageReceived_returnsTrue() {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);

        // Act
        boolean inCombat = combatManager.isInCombat(player1);

        // Assert
        assertTrue(inCombat);
    }

    @Test
    void isInCombat_withRecentDamageDealt_returnsTrue() {
        // Arrange
        combatManager.recordDamage(player2, player1, 5.0);

        // Act
        boolean inCombat = combatManager.isInCombat(player1);

        // Assert
        assertTrue(inCombat);
    }

    @Test
    void isInCombat_withNoCombat_returnsFalse() {
        // Act
        boolean inCombat = combatManager.isInCombat(player1);

        // Assert
        assertFalse(inCombat);
    }

    @Test
    void isInCombat_afterTimeout_returnsFalse() throws InterruptedException {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);

        // Act
        Thread.sleep(11000); // Wait for timeout
        boolean inCombat = combatManager.isInCombat(player1);

        // Assert
        assertFalse(inCombat);
    }

    @Test
    void getDamageDealt_withNoDamage_returnsZero() {
        // Act
        double damage = combatManager.getDamageDealt(player1);

        // Assert
        assertEquals(0.0, damage, 0.01);
    }

    @Test
    void getDamageTaken_withNoDamage_returnsZero() {
        // Act
        double damage = combatManager.getDamageTaken(player1);

        // Assert
        assertEquals(0.0, damage, 0.01);
    }

    @Test
    void clearPlayer_removesAllData() {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player2, player1, 3.0);

        // Act
        combatManager.clearPlayer(player1);

        // Assert
        assertEquals(0.0, combatManager.getDamageDealt(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player1), 0.01);
        assertNull(combatManager.getKiller(player1));
    }

    @Test
    void clearAll_removesAllData() {
        // Arrange
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player2, player3, 3.0);

        // Act
        combatManager.clearAll();

        // Assert
        assertEquals(0.0, combatManager.getDamageDealt(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player2), 0.01);
    }

    @Test
    void complexCombatScenario_tracksCorrectly() {
        // Arrange - Complex combat scenario:
        // Player3 attacks Player1 for 3 damage
        // Player2 attacks Player1 for 4 damage
        // Player3 attacks Player1 for 2 damage
        // Player2 deals killing blow for 5 damage

        combatManager.recordDamage(player1, player3, 3.0);
        combatManager.recordDamage(player1, player2, 4.0);
        combatManager.recordDamage(player1, player3, 2.0);
        combatManager.recordDamage(player1, player2, 5.0);

        // Act
        UUID killer = combatManager.getKiller(player1);
        UUID[] assisters = combatManager.getAssisters(player1, killer);

        // Assert
        assertEquals(player2, killer);
        assertEquals(1, assisters.length);
        assertEquals(player3, assisters[0]);

        assertEquals(9.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(5.0, combatManager.getDamageDealt(player3), 0.01);
        assertEquals(14.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void multipleVictims_trackedSeparately() {
        // Arrange
        combatManager.recordDamage(player1, player3, 5.0); // player3 damages player1
        combatManager.recordDamage(player2, player3, 3.0); // player3 damages player2

        // Act
        UUID killer1 = combatManager.getKiller(player1);
        UUID killer2 = combatManager.getKiller(player2);

        // Assert
        assertEquals(player3, killer1);
        assertEquals(player3, killer2);
        assertEquals(8.0, combatManager.getDamageDealt(player3), 0.01);
        assertEquals(5.0, combatManager.getDamageTaken(player1), 0.01);
        assertEquals(3.0, combatManager.getDamageTaken(player2), 0.01);
    }
}