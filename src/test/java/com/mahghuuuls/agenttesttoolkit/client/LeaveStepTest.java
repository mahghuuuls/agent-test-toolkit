package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.client.LeaveStep.Next;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stages of the single-player logout leave, and both ways it is given up. */
class LeaveStepTest {

    private static final long DEADLINE = LeaveStep.DEADLINE_MS;
    private static final boolean PAUSING = false;
    private static final boolean PROVING = true;

    @Test
    @DisplayName("pausing waits for the game to read as paused, then starts proving")
    void pausing() {
        assertEquals(Next.WAIT, LeaveStep.next(PAUSING, true, true, false, 0, 0, 0L));
        assertEquals(Next.WAIT, LeaveStep.next(PAUSING, true, true, false, 0, 0, DEADLINE));
        assertEquals(Next.PROVE, LeaveStep.next(PAUSING, true, true, true, 0, 0, 16L));
        assertEquals(Next.PROVE, LeaveStep.next(PAUSING, true, true, true, 0, 0, DEADLINE));
    }

    @Test
    @DisplayName("proving queues the second marker only after the first ran, and leaves only after both")
    void proving() {
        assertEquals(Next.WAIT, LeaveStep.next(PROVING, true, true, true, 1, 0, 50L), "the first is pending");
        assertEquals(Next.QUEUE_SECOND, LeaveStep.next(PROVING, true, true, true, 1, 1, 60L));
        assertEquals(Next.WAIT, LeaveStep.next(PROVING, true, true, true, 2, 1, 70L), "the second is pending");
        assertEquals(Next.LEAVE, LeaveStep.next(PROVING, true, true, true, 2, 2, 80L));
        assertEquals(Next.LEAVE, LeaveStep.next(PROVING, true, true, true, 2, 2, DEADLINE), "proven at the deadline");
    }

    @Test
    @DisplayName("nothing is sent while the game does not read as paused")
    void notPaused() {
        assertEquals(Next.WAIT, LeaveStep.next(PROVING, true, true, false, 2, 2, 100L));
        assertEquals(Next.WAIT, LeaveStep.next(PROVING, true, true, false, 1, 1, 100L));
        assertEquals(Next.TIMEOUT, LeaveStep.next(PROVING, true, true, false, 2, 2, DEADLINE + 1L));
    }

    @Test
    @DisplayName("past ten seconds the leave times out in either stage, even when the pause or the proof arrives just then")
    void timeout() {
        assertEquals(Next.TIMEOUT, LeaveStep.next(PAUSING, true, true, false, 0, 0, DEADLINE + 1L));
        assertEquals(Next.TIMEOUT, LeaveStep.next(PAUSING, true, true, true, 0, 0, DEADLINE + 1L),
                "a pause first read after the deadline");
        assertEquals(Next.TIMEOUT, LeaveStep.next(PROVING, true, true, true, 1, 0, DEADLINE + 1L));
        assertEquals(Next.TIMEOUT, LeaveStep.next(PROVING, true, true, true, 1, 1, DEADLINE + 1L));
        assertEquals(Next.TIMEOUT, LeaveStep.next(PROVING, true, true, true, 2, 2, DEADLINE + 1L),
                "a proof first read after the deadline");
        assertEquals(Next.WAIT, LeaveStep.next(PROVING, true, true, true, 2, 1, DEADLINE));
        assertEquals(10000L, DEADLINE);
    }

    @Test
    @DisplayName("another screen in the pause screen's place, or the world gone, is an interruption in every stage")
    void interrupted() {
        for (boolean proving : new boolean[] {PAUSING, PROVING}) {
            for (int done = 0; done <= 2; done++) {
                assertEquals(Next.INTERRUPTED, LeaveStep.next(proving, true, false, true, 2, done, 10L),
                        "screen replaced: " + proving + " " + done);
                assertEquals(Next.INTERRUPTED, LeaveStep.next(proving, false, true, true, 2, done, 10L),
                        "world gone: " + proving + " " + done);
                assertEquals(Next.INTERRUPTED, LeaveStep.next(proving, false, false, false, 2, done, DEADLINE + 1L),
                        "an interruption wins over the deadline: " + proving);
            }
        }
    }

    @Test
    @DisplayName("a whole leave walks pausing, proving, the second marker, and the leave, in that order")
    void wholeLeave() {
        boolean proving = PAUSING;
        int queued = 0;
        int done = 0;
        StringBuilder path = new StringBuilder();
        boolean paused = false;
        for (int tick = 0; tick < 20; tick++) {
            if (tick == 2) {
                paused = true;
            }
            Next next = LeaveStep.next(proving, true, true, paused, queued, done, tick * 50L);
            if (next == Next.WAIT) {
                // Each queued marker runs on the server two ticks later.
                if (queued > done && tick % 2 == 0) {
                    done++;
                }
                continue;
            }
            path.append(next).append(' ');
            if (next == Next.PROVE) {
                proving = PROVING;
                queued++;
            } else if (next == Next.QUEUE_SECOND) {
                queued++;
            } else {
                break;
            }
        }
        assertEquals("PROVE QUEUE_SECOND LEAVE ", path.toString());
        assertTrue(queued == 2 && done == 2);
    }
}
