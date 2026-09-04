package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arm, cue, ack, and what each measures.
 *
 * <p>The numbers a human-in-the-loop test depends on come from here: the latency between
 * asking and answering. Pinned: an armed checkpoint waits until acknowledged, one at a time;
 * an ack answers the arm first and a cue second; latency counts from whichever ask was later;
 * an operator who leaves cancels; and nothing answers twice.
 */
class OperatorCheckpointsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        OperatorCheckpoints.reset();
    }

    @Test
    @DisplayName("an armed checkpoint is pending until acknowledged, then satisfied with latency")
    void armThenAck() {
        OperatorCheckpoints.Armed armed = OperatorCheckpoints.arm("PRESS_H", 1000L, () -> true);
        assertNotNull(armed);
        assertEquals(WaitCondition.Status.PENDING, armed.poll(0).getStatus());
        assertFalse(armed.isAcknowledged());

        OperatorCheckpoints.Ack ack = OperatorCheckpoints.ack(1030L);
        assertNotNull(ack);
        assertEquals("PRESS_H", ack.label);
        assertTrue(ack.answeredArm);
        assertEquals(30L, ack.latencyTicks());
        assertEquals(WaitCondition.Status.SATISFIED, armed.poll(30).getStatus());

        LogRecord record = LogRecord.of(EventType.AWAIT);
        armed.describe(record);
        String text = record.render();
        assertTrue(text.contains("label=PRESS_H"), text);
        assertTrue(text.contains("armedTick=1000"), text);
        assertTrue(text.contains("ackTick=1030"), text);
        assertTrue(text.contains("latencyTicks=30"), text);
    }

    @Test
    @DisplayName("a second arm is refused while one is armed, and allowed once it is answered")
    void oneAtATime() {
        assertNotNull(OperatorCheckpoints.arm("A", 1L, null));
        assertNull(OperatorCheckpoints.arm("B", 2L, null));
        assertEquals("A", OperatorCheckpoints.getArmed().getLabel());
        OperatorCheckpoints.ack(5L);
        assertNull(OperatorCheckpoints.getArmed());
        assertNotNull(OperatorCheckpoints.arm("B", 6L, null));
    }

    @Test
    @DisplayName("a cue after arming moves the ask, so latency counts from the cue")
    void cueMovesTheAsk() {
        OperatorCheckpoints.arm("GO", 100L, null);
        OperatorCheckpoints.cued("GO", 160L);
        OperatorCheckpoints.Ack ack = OperatorCheckpoints.ack(170L);
        assertEquals(160L, ack.askedTick);
        assertEquals(10L, ack.latencyTicks());
        assertTrue(ack.answeredArm);
        // The cue was answered along with the arm; nothing is left to answer.
        assertNull(OperatorCheckpoints.ack(180L));
    }

    @Test
    @DisplayName("with nothing armed, an ack answers the last cue once and then nothing")
    void ackAnswersCue() {
        OperatorCheckpoints.cued("PRESS_H_NOW", 500L);
        OperatorCheckpoints.Ack ack = OperatorCheckpoints.ack(587L);
        assertNotNull(ack);
        assertFalse(ack.answeredArm);
        assertEquals("PRESS_H_NOW", ack.label);
        assertEquals(87L, ack.latencyTicks());
        assertNull(OperatorCheckpoints.ack(600L));
    }

    @Test
    @DisplayName("an ack with nothing armed or cued answers nothing")
    void nothingToAnswer() {
        assertNull(OperatorCheckpoints.ack(1L));
    }

    @Test
    @DisplayName("the operator leaving cancels an armed checkpoint")
    void operatorLeavingCancels() {
        final boolean[] present = {true};
        OperatorCheckpoints.Armed armed = OperatorCheckpoints.arm("X", 1L, () -> present[0]);
        assertEquals(WaitCondition.Status.PENDING, armed.poll(1).getStatus());
        present[0] = false;
        WaitCondition.Poll poll = armed.poll(2);
        assertEquals(WaitCondition.Status.CANCELLED, poll.getStatus());
        assertEquals("operatorLost", poll.getReason());
    }

    @Test
    @DisplayName("a session stop cancels the armed checkpoint instead of leaving it to time out")
    void sessionStopCancels() {
        OperatorCheckpoints.Armed armed = OperatorCheckpoints.arm("LEFTOVER", 1L, () -> true);
        assertEquals(WaitCondition.Status.PENDING, armed.poll(1).getStatus());
        OperatorCheckpoints.reset();
        WaitCondition.Poll poll = armed.poll(2);
        assertEquals(WaitCondition.Status.CANCELLED, poll.getStatus());
        assertEquals("sessionStopped", poll.getReason());
        assertNull(OperatorCheckpoints.getArmed());
    }

    @Test
    @DisplayName("disarming forgets only the checkpoint it is given")
    void disarmIsSpecific() {
        OperatorCheckpoints.Armed first = OperatorCheckpoints.arm("first", 1L, null);
        OperatorCheckpoints.ack(2L);
        OperatorCheckpoints.Armed second = OperatorCheckpoints.arm("second", 3L, null);
        OperatorCheckpoints.disarm(first);
        assertEquals(second, OperatorCheckpoints.getArmed());
        OperatorCheckpoints.disarm(second);
        assertNull(OperatorCheckpoints.getArmed());
    }
}
