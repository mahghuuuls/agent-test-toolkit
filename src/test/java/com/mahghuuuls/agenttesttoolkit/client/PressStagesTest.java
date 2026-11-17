package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.client.PressStages.Act;
import com.mahghuuuls.agenttesttoolkit.client.PressStages.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The life of a press and of a queued click, the cancelled paths included. */
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
}
