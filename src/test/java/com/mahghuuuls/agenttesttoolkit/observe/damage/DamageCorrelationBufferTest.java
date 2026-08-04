package com.mahghuuuls.agenttesttoolkit.observe.damage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage correlation rule.
 *
 * <p>This is the most intricate logic in the project and the least amenable to in-game
 * verification: reproducing a cancelled attack, an invulnerable target, or two hits landing on
 * one entity within a single tick is awkward at a keyboard and impossible to repeat reliably.
 * The buffer is generic over the target type precisely so those cases can be driven directly.
 *
 * <p>Targets here are strings standing in for entities. Identity is what matters, not type.
 */
class DamageCorrelationBufferTest {

    private static final String ZOMBIE = "zombie";
    private static final String SKELETON = "skeleton";

    private final DamageCorrelationBuffer<String> buffer = new DamageCorrelationBuffer<String>();

    private DamageCorrelation attack(String target, double raw, double health) {
        return buffer.onAttack(target, 1000L, 0, "SERVER", null, -1L, "magic", raw, health);
    }

    // --- The normal path ------------------------------------------------------------

    @Test
    @DisplayName("a full three-stage hit produces one applied correlation carrying all amounts")
    void fullHitCapturesAllThreeStages() {
        attack(ZOMBIE, 8.0, 20.0);
        buffer.onHurt(ZOMBIE, 8.0);
        buffer.onDamage(ZOMBIE, 6.4);

        List<DamageCorrelationBuffer.Entry<String>> drained = buffer.drain();
        assertEquals(1, drained.size());

        DamageCorrelation c = drained.get(0).getCorrelation();
        assertEquals(8.0, c.getAmountRaw());
        assertEquals(8.0, c.getAmountPreMitigation());
        assertEquals(6.4, c.getAmountFinal());
        assertEquals(20.0, c.getHealthBefore());
        assertEquals(DamageCorrelation.OUTCOME_APPLIED, c.getOutcome());
        assertNull(c.getStoppedAt());
    }

    @Test
    @DisplayName("the mitigation gap is visible, which is the whole point of three stages")
    void mitigationGapIsVisible() {
        // A mod intending 8.0 while health drops by 6.4 is not a mod bug; armour took the
        // difference. One event alone could not distinguish those two explanations.
        attack(ZOMBIE, 8.0, 20.0);
        buffer.onHurt(ZOMBIE, 8.0);
        buffer.onDamage(ZOMBIE, 6.4);

        DamageCorrelation c = buffer.drain().get(0).getCorrelation();
        assertEquals(1.6, c.getAmountPreMitigation() - c.getAmountFinal(), 0.0001);
    }

    // --- Attempts that go nowhere ---------------------------------------------------

    @Test
    @DisplayName("an attack cancelled outright still produces a record, stopped at ATTACK")
    void cancelledAtAttackStillRecords() {
        // The case emitting at the final stage would silently drop, and the one an agent most
        // needs: a mod believes it dealt damage and nothing happened.
        attack(ZOMBIE, 8.0, 20.0);

        DamageCorrelation c = buffer.drain().get(0).getCorrelation();
        assertEquals(DamageCorrelation.OUTCOME_NOT_APPLIED, c.getOutcome());
        assertEquals("ATTACK", c.getStoppedAt());
        assertFalse(c.reachedHurt());
        assertFalse(c.reachedDamage());
        assertEquals(8.0, c.getAmountRaw());
    }

    @Test
    @DisplayName("an attempt stopped after hurt is distinguishable from one stopped at attack")
    void stoppedAfterHurtIsDistinguishable() {
        // Different causes, different fixes. A generic "did not apply" would conflate them.
        attack(ZOMBIE, 8.0, 20.0);
        buffer.onHurt(ZOMBIE, 8.0);

        DamageCorrelation c = buffer.drain().get(0).getCorrelation();
        assertEquals(DamageCorrelation.OUTCOME_NOT_APPLIED, c.getOutcome());
        assertEquals("HURT", c.getStoppedAt());
        assertTrue(c.reachedHurt());
        assertFalse(c.reachedDamage());
    }

    @Test
    @DisplayName("outcome defaults to not-applied rather than being inferred from an absence")
    void outcomeDefaultsToNotApplied() {
        DamageCorrelation c = attack(ZOMBIE, 8.0, 20.0);
        assertEquals(DamageCorrelation.OUTCOME_NOT_APPLIED, c.getOutcome());
        c.recordDamage(6.0);
        assertEquals(DamageCorrelation.OUTCOME_APPLIED, c.getOutcome());
    }

    // --- Several attempts in one tick -----------------------------------------------

    @Test
    @DisplayName("two hits on one target in a tick produce two records, not one merged")
    void twoHitsOnOneTargetProduceTwoRecords() {
        // A single slot per target would keep only the last, silently losing a hit.
        attack(ZOMBIE, 8.0, 20.0);
        buffer.onHurt(ZOMBIE, 8.0);
        buffer.onDamage(ZOMBIE, 6.0);

        attack(ZOMBIE, 3.0, 14.0);
        buffer.onHurt(ZOMBIE, 3.0);
        buffer.onDamage(ZOMBIE, 2.0);

        List<DamageCorrelationBuffer.Entry<String>> drained = buffer.drain();
        assertEquals(2, drained.size());
        assertEquals(6.0, drained.get(0).getCorrelation().getAmountFinal());
        assertEquals(2.0, drained.get(1).getCorrelation().getAmountFinal());
        assertEquals(20.0, drained.get(0).getCorrelation().getHealthBefore());
        assertEquals(14.0, drained.get(1).getCorrelation().getHealthBefore());
    }

    @Test
    @DisplayName("stages attach to the right target when several are damaged in one tick")
    void stagesAttachToTheCorrectTarget() {
        attack(ZOMBIE, 8.0, 20.0);
        attack(SKELETON, 5.0, 12.0);
        buffer.onDamage(SKELETON, 4.0);
        buffer.onDamage(ZOMBIE, 7.0);

        List<DamageCorrelationBuffer.Entry<String>> drained = buffer.drain();
        assertEquals(2, drained.size());
        assertSame(ZOMBIE, drained.get(0).getTarget());
        assertEquals(7.0, drained.get(0).getCorrelation().getAmountFinal());
        assertSame(SKELETON, drained.get(1).getTarget());
        assertEquals(4.0, drained.get(1).getCorrelation().getAmountFinal());
    }

    @Test
    @DisplayName("a later stage attaches to the most recent open attempt for that target")
    void laterStageAttachesToMostRecentOpen() {
        DamageCorrelation first = attack(ZOMBIE, 8.0, 20.0);
        DamageCorrelation second = attack(ZOMBIE, 3.0, 20.0);

        buffer.onHurt(ZOMBIE, 3.0);

        assertTrue(second.reachedHurt(), "the newer attempt should have been enriched");
        assertFalse(first.reachedHurt(), "the older attempt should be untouched");
        assertEquals(3.0, second.getAmountPreMitigation());
    }

    // --- Robustness -----------------------------------------------------------------

    @Test
    @DisplayName("a stage arriving with no open attempt is ignored rather than inventing one")
    void orphanStageIsIgnored() {
        // Happens legitimately when the category is enabled partway through a tick. A partial
        // correlation would be worse than none: its missing raw amount would be
        // indistinguishable from a genuine zero.
        buffer.onHurt(ZOMBIE, 8.0);
        buffer.onDamage(ZOMBIE, 6.0);
        assertTrue(buffer.isEmpty());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test
    @DisplayName("draining empties the buffer so the next tick starts clean")
    void drainEmptiesTheBuffer() {
        attack(ZOMBIE, 8.0, 20.0);
        assertEquals(1, buffer.size());
        assertEquals(1, buffer.drain().size());
        assertTrue(buffer.isEmpty());
        assertTrue(buffer.drain().isEmpty());
    }

    @Test
    @DisplayName("the observed tick is captured at attack time, not at flush time")
    void worldTickIsCapturedAtAttackTime() {
        // Records flush at end of tick. Reading the tick when writing would make every record
        // claim the flush tick and destroy the ordering the field exists to provide.
        DamageCorrelation c = buffer.onAttack(ZOMBIE, 84031L, 0, "SERVER", null, -1L, "magic", 8.0, 20.0);
        assertEquals(84031L, c.getWorldTick());
    }

    @Test
    @DisplayName("session identity is captured at attack time, not read at flush time")
    void sessionIsCapturedAtAttackTime() {
        // Found in review: the session tick counter is advanced by its own handler, which is
        // registered before this observer and runs first in the same END phase. Reading it at
        // flush would report the following tick's value, so a damage record and a block
        // placement record from the same tick would disagree.
        DamageCorrelation c = buffer.onAttack(ZOMBIE, 1000L, 0, "SERVER", "spell_test", 47L, "magic", 8.0, 20.0);
        assertEquals("spell_test", c.getSessionName());
        assertEquals(47L, c.getSessionTick());
    }

    @Test
    @DisplayName("no active session leaves the session fields unset")
    void noSessionLeavesFieldsUnset() {
        DamageCorrelation c = attack(ZOMBIE, 8.0, 20.0);
        assertNull(c.getSessionName());
    }

    @Test
    @DisplayName("the logical side is captured at attack time rather than assumed at emit")
    void sideIsCapturedAtAttackTime() {
        DamageCorrelation c = attack(ZOMBIE, 8.0, 20.0);
        assertEquals("SERVER", c.getSide());
    }

    @Test
    @DisplayName("a null damage source is tolerated and the field simply goes unset")
    void nullSourceIsTolerated() {
        DamageCorrelation c = buffer.onAttack(ZOMBIE, 1000L, 0, "SERVER", null, -1L, null, 8.0, 20.0);
        assertNull(c.getSource());
    }

    @Test
    @DisplayName("a repeated stage for an already-enriched attempt is ignored")
    void repeatedStageForSameAttemptIsIgnored() {
        // Gap noted in review. Two hurt callbacks with only one open attempt must not corrupt
        // the recorded amount by overwriting it with the second value.
        attack(ZOMBIE, 8.0, 20.0);
        buffer.onHurt(ZOMBIE, 8.0);
        buffer.onHurt(ZOMBIE, 99.0);

        DamageCorrelation c = buffer.drain().get(0).getCorrelation();
        assertEquals(8.0, c.getAmountPreMitigation());
    }

    @Test
    @DisplayName("discarding pending entries empties the buffer without emitting")
    void discardPendingEmptiesBuffer() {
        // ARC-002 precedent. Entries hold references into a world that is unloading, so a
        // crash between the attack stage and the tick-end flush must not leak them into the
        // next world's first tick.
        attack(ZOMBIE, 8.0, 20.0);
        attack(SKELETON, 5.0, 12.0);
        assertEquals(2, buffer.discardPending());
        assertTrue(buffer.isEmpty());
        assertTrue(buffer.drain().isEmpty());
        assertEquals(0, buffer.discardPending());
    }
}
