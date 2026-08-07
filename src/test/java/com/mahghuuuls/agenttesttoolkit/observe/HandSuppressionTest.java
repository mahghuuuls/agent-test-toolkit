package com.mahghuuuls.agenttesttoolkit.observe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suppressing the second hand of a single click on a block.
 *
 * <p>These exist because of a defect found by running the mod, not by reading it. Vanilla tries
 * the main hand and then the off hand when the first does not consume the action, so one
 * right-click fires its event twice in the same tick. The suppression for that was written for
 * entities and never applied to blocks, so every right-click on a block produced two records
 * that differed only by a {@code hand} field.
 *
 * <p>It went unnoticed because the entity half was correct and well commented, and the two
 * paths sat in the same class stating opposite policies: one said collapsing the pair was the
 * fix and that recording the hand instead would be "an excuse rather than a fix", while the
 * other recorded the hand and said so plainly. Nothing compared them.
 *
 * <p>The rule takes a packed position rather than a {@code BlockPos} precisely so it can be
 * checked here, without a world.
 */
class HandSuppressionTest {

    private static final long POS_A = 1234567L;
    private static final long POS_B = 7654321L;
    private static final int PLAYER = 42;
    private static final int OTHER_PLAYER = 43;

    private DiscreteActionObserver observer() {
        return new DiscreteActionObserver();
    }

    @Test
    @DisplayName("the second hand of one click on one block in one tick is suppressed")
    void secondHandSuppressed() {
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L), "main hand records");
        assertTrue(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L), "off hand suppressed");
    }

    @Test
    @DisplayName("the same block clicked again on a later tick is a new action")
    void laterTickIsNotARepeat() {
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        assertTrue(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        // Holding the button repeats every few ticks. Each repeat is a real interaction.
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 104L));
    }

    @Test
    @DisplayName("a different block in the same tick is not a repeat")
    void differentBlockSameTick() {
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_B, "RIGHT", 100L));
    }

    @Test
    @DisplayName("a different player in the same tick is not a repeat")
    void differentPlayerSameTick() {
        // Two players clicking the same block on one tick is rare but entirely possible, and
        // dropping one of them would lose a real interaction rather than a duplicate.
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        assertFalse(o.isRepeatBlockHand(OTHER_PLAYER, POS_A, "RIGHT", 100L));
    }

    @Test
    @DisplayName("left and right on the same block in the same tick are two actions")
    void buttonIsPartOfTheKey() {
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "LEFT", 100L));
    }

    @Test
    @DisplayName("block and entity suppression do not evict each other")
    void separateSlots() {
        // Sharing one slot would let an entity interaction in the same tick displace the block
        // key, so the block's off hand would then come through as a duplicate.
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L));
        assertFalse(o.isRepeatEntityHand(PLAYER, 7, 100L));
        assertTrue(o.isRepeatBlockHand(PLAYER, POS_A, "RIGHT", 100L),
                "the entity interaction must not have cleared the block slot");
    }

    @Test
    @DisplayName("the entity half still works")
    void entityHalfUnchanged() {
        DiscreteActionObserver o = observer();
        assertFalse(o.isRepeatEntityHand(PLAYER, 7, 100L));
        assertTrue(o.isRepeatEntityHand(PLAYER, 7, 100L));
        assertFalse(o.isRepeatEntityHand(PLAYER, 8, 100L));
    }
}
