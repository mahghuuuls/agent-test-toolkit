package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.client.PressStages.Act;
import com.mahghuuuls.agenttesttoolkit.client.PressStages.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The life of a press, of a queued click or key, and of a queued hold, the cancelled paths included. */
class PressStagesTest {

    /** Runs one tick start or end and returns what was done. */
    private static Act start(Stage[] stage, String blocked, boolean queued) {
        Act act = PressStages.atStart(stage[0], blocked);
        stage[0] = PressStages.after(stage[0], act, queued);
        return act;
    }

    private static Act end(Stage[] stage, boolean queued) {
        Act act = PressStages.atEnd(stage[0]);
        stage[0] = PressStages.after(stage[0], act, queued);
        return act;
    }

    @Test
    @DisplayName("nothing goes into the queue without a world, with a screen open, or with focus taken")
    void blocked() {
        assertNull(PressStages.blocked(true, false, false));
        assertEquals("noWorld", PressStages.blocked(false, true, true));
        assertEquals("screenOpen", PressStages.blocked(true, true, true));
        assertEquals("noFocus", PressStages.blocked(true, false, true));
    }

    @Test
    @DisplayName("a binding or event press: down when taken, let go and recorded at the next tick end, then over")
    void plainPress() {
        Stage[] stage = {Stage.DOWN};
        assertEquals(Act.NONE, start(stage, "screenOpen", false), "no queue, nothing to block");
        assertEquals(Act.LET_GO, end(stage, false));
        assertEquals(Stage.OVER, stage[0]);
    }

    @Test
    @DisplayName("a queued click: queued, recorded, its release queued, then let go a tick later unless the button is down")
    void queuedClick() {
        Stage[] stage = {Stage.QUEUE_DUE};
        List<Act> acts = new ArrayList<Act>();
        acts.add(end(stage, true));
        acts.add(start(stage, null, true));
        acts.add(end(stage, true));
        acts.add(start(stage, null, true));
        acts.add(end(stage, true));
        acts.add(start(stage, null, true));
        assertEquals(Arrays.asList(Act.NONE, Act.ADD_PRESS, Act.LET_GO, Act.ADD_RELEASE, Act.NONE, Act.UNPRESS_IF_UP),
                acts);
        assertEquals(Stage.OVER, stage[0]);
    }

    @Test
    @DisplayName("a queued click blocked at its tick start is cancelled, never queued; a blocked release lets go directly")
    void blockedQueue() {
        Stage[] press = {Stage.QUEUE_DUE};
        assertEquals(Act.CANCEL, start(press, "screenOpen", true));
        assertEquals(Stage.OVER, press[0]);
        Stage[] release = {Stage.QUEUE_UP_DUE};
        assertEquals(Act.UNPRESS, start(release, "focusLost", true));
        assertEquals(Stage.OVER, release[0]);
    }

    @Test
    @DisplayName("focus lost clears every press at once: cancelled before the queue, let go after it")
    void focusLostClears() {
        for (Stage first : new Stage[] {Stage.QUEUE_DUE, Stage.DOWN, Stage.QUEUE_UP_DUE, Stage.UNPRESS_DUE}) {
            for (boolean queued : new boolean[] {true, false}) {
                Stage stage = first;
                List<Act> acts = new ArrayList<Act>();
                Act act;
                while ((act = PressStages.onFocusLost(stage)) != Act.NONE) {
                    acts.add(act);
                    stage = PressStages.after(stage, act, queued);
                }
                assertEquals(Stage.OVER, stage, first + " queued=" + queued);
                if (first == Stage.QUEUE_DUE) {
                    assertEquals(Arrays.asList(Act.CANCEL), acts, "never queued");
                }
                if (first == Stage.DOWN && queued) {
                    assertEquals(Arrays.asList(Act.LET_GO, Act.UNPRESS), acts, "recorded, its release not queued");
                }
            }
        }
    }

    /** A queued hold's life, tick by tick: what each tick start, each counted tick and its end did. */
    private static List<Act> holdLife(Stage[] stage, int ticks) {
        List<Act> acts = new ArrayList<Act>();
        acts.add(start(stage, null, true));
        for (int i = 0; i < ticks && PressStages.holdRuns(stage[0]); i++) {
            acts.add(start(stage, null, true));
        }
        Act end = PressStages.onHoldEnd(stage[0]);
        acts.add(end);
        stage[0] = PressStages.after(stage[0], end, true);
        while (stage[0] != Stage.OVER) {
            acts.add(start(stage, null, true));
        }
        return acts;
    }

    @Test
    @DisplayName("a queued hold: key-down at a tick start, down while it lasts, then its key-up queued and the binding let go unless the key is down")
    void queuedHoldLife() {
        Stage[] stage = {Stage.QUEUE_DUE};
        assertEquals(false, PressStages.holdRuns(stage[0]), "no tick counts before its key-down went in");
        List<Act> acts = holdLife(stage, 3);
        assertEquals(Arrays.asList(Act.ADD_PRESS, Act.NONE, Act.NONE, Act.NONE, Act.LET_GO, Act.ADD_RELEASE,
                Act.UNPRESS_IF_UP), acts);
        assertEquals(Stage.OVER, stage[0]);
    }

    @Test
    @DisplayName("an ended queued hold always queues its key-up, or lets the binding go when blocked: no key stays down")
    void queuedHoldKeyUpNeverLost() {
        Stage after = PressStages.after(Stage.DOWN, PressStages.onHoldEnd(Stage.DOWN), true);
        assertEquals(Stage.QUEUE_UP_DUE, after, "the hold's end leaves its key-up due");
        assertEquals(Act.ADD_RELEASE, PressStages.atStart(after, null));
        assertEquals(Act.UNPRESS, PressStages.atStart(after, "noFocus"), "blocked: let go without the queue");
        assertEquals(Act.UNPRESS, PressStages.onFocusLost(after));
    }

    @Test
    @DisplayName("a queued hold ended before its key-down is refused and nothing of it is queued")
    void queuedHoldEndedBeforeKeyDown() {
        Stage[] stage = {Stage.QUEUE_DUE};
        Act end = PressStages.onHoldEnd(stage[0]);
        assertEquals(Act.CANCEL, end, "refused with the reason it ended, never done heldTicks=0");
        stage[0] = PressStages.after(stage[0], end, true);
        assertEquals(Stage.OVER, stage[0]);
        assertEquals(Act.NONE, PressStages.atStart(stage[0], null), "no key-down and no key-up afterwards");
        assertEquals(Act.CANCEL, PressStages.onFocusLost(Stage.QUEUE_DUE));
    }

    @Test
    @DisplayName("at one tick start every key-up goes in before any key-down, so a key held again goes up, then down")
    void keyUpBeforeKeyDown() {
        // The new hold was asked before the old hold's key-up came due, so it is listed first.
        List<Stage> stages = Arrays.asList(Stage.QUEUE_DUE, Stage.DOWN, Stage.QUEUE_UP_DUE, Stage.UNPRESS_DUE,
                Stage.QUEUE_DUE);
        assertEquals(Arrays.asList(2, 3, 0, 1, 4), PressStages.startOrder(stages));
        assertEquals(Arrays.asList(0, 1), PressStages.startOrder(Arrays.asList(Stage.QUEUE_UP_DUE, Stage.QUEUE_DUE)));
    }

    @Test
    @DisplayName("a hold by the binding route drops a queued key-up not yet queued; one already queued runs on")
    void newHoldTakesTheKey() {
        Act taken = PressStages.onKeyTaken(Stage.QUEUE_UP_DUE);
        assertEquals(Act.DROP, taken);
        Stage after = PressStages.after(Stage.QUEUE_UP_DUE, taken, true);
        assertEquals(Stage.OVER, after);
        assertEquals(Act.NONE, PressStages.atStart(after, null), "no key-up goes in under the new hold");
        assertEquals(Act.NONE, PressStages.onKeyTaken(Stage.UNPRESS_DUE),
                "already read by the game; its letting go is skipped while a hold keeps the key");
        assertEquals(Act.NONE, PressStages.onKeyTaken(Stage.DOWN));
        assertEquals(Act.NONE, PressStages.onKeyTaken(Stage.QUEUE_DUE));
    }

    @Test
    @DisplayName("an ended queued press's key-up, still due, is taken over by a new hold or key press of the key by the binding route")
    void newPressOrHoldTakesAQueuedPressKeyUp() {
        Stage[] stage = {Stage.QUEUE_DUE};
        assertEquals(Act.ADD_PRESS, start(stage, null, true), "its key-down went in");
        assertEquals(Act.LET_GO, end(stage, true));
        assertEquals(Stage.QUEUE_UP_DUE, stage[0], "recorded, its key-up still to go in");

        assertTrue(PressStages.takesKey(true, false, false), "a hold by the binding route");
        assertTrue(PressStages.takesKey(false, false, PressStages.setsState(true, false)), "a key press by the binding route");
        assertFalse(PressStages.takesKey(false, false, PressStages.setsState(false, false)),
                "a click by the binding route only counts its press: nothing would let the binding go");
        assertFalse(PressStages.takesKey(false, true, false), "a queued press waits its turn at the tick start");
        assertFalse(PressStages.takesKey(true, true, false), "so does a queued hold");

        Act taken = PressStages.onKeyTaken(stage[0]);
        assertEquals(Act.DROP, taken);
        stage[0] = PressStages.after(stage[0], taken, true);
        assertEquals(Stage.OVER, stage[0]);
        assertEquals(Act.NONE, PressStages.atStart(stage[0], null), "no key-up goes in under the new input");
    }

    @Test
    @DisplayName("only a key press by the binding route sets the binding down itself; queued presses and clicks leave it to the game")
    void setsState() {
        assertTrue(PressStages.setsState(true, false));
        assertFalse(PressStages.setsState(true, true), "a queued key press: the game sets it from the events");
        assertFalse(PressStages.setsState(false, false), "a click only counts its press");
        assertFalse(PressStages.setsState(false, true));
    }
}
