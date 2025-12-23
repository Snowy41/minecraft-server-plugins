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
        combatManager.recordDamage(player1, player2, 5.0);

        assertEquals(5.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(5.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void recordDamage_multipleTimes_accumulatesDamage() {
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player1, player2, 3.0);
        combatManager.recordDamage(player1, player2, 2.0);

        assertEquals(10.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(10.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void getKiller_withRecentDamage_returnsAttacker() {
        combatManager.recordDamage(player1, player2, 5.0);

        UUID killer = combatManager.getKiller(player1);

        assertEquals(player2, killer);
    }

    @Test
    void getKiller_withNoDamage_returnsNull() {
        UUID killer = combatManager.getKiller(player1);

        assertNull(killer);
    }

    @Test
    void getKiller_afterTimeout_returnsNull() throws InterruptedException {
        combatManager.recordDamage(player1, player2, 5.0);

        Thread.sleep(11000);
        UUID killer = combatManager.getKiller(player1);

        assertNull(killer);
    }

    @Test
    void getAssisters_withMultipleAttackers_returnsAllExceptKiller() {
        combatManager.recordDamage(player1, player2, 3.0); // First attacker
        combatManager.recordDamage(player1, player3, 2.0); // Second attacker
        combatManager.recordDamage(player1, player2, 5.0); // Killer (last hit)

        UUID[] assisters = combatManager.getAssisters(player1, player2);

        assertEquals(1, assisters.length);
        assertEquals(player3, assisters[0]);
    }

    @Test
    void getAssisters_withOnlyKiller_returnsEmpty() {
        combatManager.recordDamage(player1, player2, 5.0);

        UUID[] assisters = combatManager.getAssisters(player1, player2);

        assertEquals(0, assisters.length);
    }

    @Test
    void getAssisters_withNoKiller_returnsAllAttackers() {
        combatManager.recordDamage(player1, player2, 3.0);
        combatManager.recordDamage(player1, player3, 2.0);

        UUID[] assisters = combatManager.getAssisters(player1, null);

        assertEquals(2, assisters.length);
        assertTrue(assisters[0].equals(player2) || assisters[0].equals(player3));
        assertTrue(assisters[1].equals(player2) || assisters[1].equals(player3));
    }

    @Test
    void isInCombat_withRecentDamageReceived_returnsTrue() {
        combatManager.recordDamage(player1, player2, 5.0);

        boolean inCombat = combatManager.isInCombat(player1);

        assertTrue(inCombat);
    }

    @Test
    void isInCombat_withRecentDamageDealt_returnsTrue() {
        combatManager.recordDamage(player2, player1, 5.0);

        boolean inCombat = combatManager.isInCombat(player1);

        assertTrue(inCombat);
    }

    @Test
    void isInCombat_withNoCombat_returnsFalse() {
        boolean inCombat = combatManager.isInCombat(player1);

        assertFalse(inCombat);
    }

    @Test
    void isInCombat_afterTimeout_returnsFalse() throws InterruptedException {
        combatManager.recordDamage(player1, player2, 5.0);

        Thread.sleep(11000); // Wait for timeout
        boolean inCombat = combatManager.isInCombat(player1);

        assertFalse(inCombat);
    }

    @Test
    void getDamageDealt_withNoDamage_returnsZero() {
        double damage = combatManager.getDamageDealt(player1);

        assertEquals(0.0, damage, 0.01);
    }

    @Test
    void getDamageTaken_withNoDamage_returnsZero() {
        double damage = combatManager.getDamageTaken(player1);

        assertEquals(0.0, damage, 0.01);
    }

    @Test
    void clearPlayer_removesAllData() {
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player2, player1, 3.0);

        combatManager.clearPlayer(player1);

        assertEquals(0.0, combatManager.getDamageDealt(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player1), 0.01);
        assertNull(combatManager.getKiller(player1));
    }

    @Test
    void clearAll_removesAllData() {
        combatManager.recordDamage(player1, player2, 5.0);
        combatManager.recordDamage(player2, player3, 3.0);

        combatManager.clearAll();

        assertEquals(0.0, combatManager.getDamageDealt(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player1), 0.01);
        assertEquals(0.0, combatManager.getDamageTaken(player2), 0.01);
    }

    @Test
    void complexCombatScenario_tracksCorrectly() {
        combatManager.recordDamage(player1, player3, 3.0);
        combatManager.recordDamage(player1, player2, 4.0);
        combatManager.recordDamage(player1, player3, 2.0);
        combatManager.recordDamage(player1, player2, 5.0);

        UUID killer = combatManager.getKiller(player1);
        UUID[] assisters = combatManager.getAssisters(player1, killer);

        assertEquals(player2, killer);
        assertEquals(1, assisters.length);
        assertEquals(player3, assisters[0]);

        assertEquals(9.0, combatManager.getDamageDealt(player2), 0.01);
        assertEquals(5.0, combatManager.getDamageDealt(player3), 0.01);
        assertEquals(14.0, combatManager.getDamageTaken(player1), 0.01);
    }

    @Test
    void multipleVictims_trackedSeparately() {
        combatManager.recordDamage(player1, player3, 5.0); // player3 damages player1
        combatManager.recordDamage(player2, player3, 3.0); // player3 damages player2

        UUID killer1 = combatManager.getKiller(player1);
        UUID killer2 = combatManager.getKiller(player2);

        assertEquals(player3, killer1);
        assertEquals(player3, killer2);
        assertEquals(8.0, combatManager.getDamageDealt(player3), 0.01);
        assertEquals(5.0, combatManager.getDamageTaken(player1), 0.01);
        assertEquals(3.0, combatManager.getDamageTaken(player2), 0.01);
    }
}