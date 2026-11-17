package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** When a held input is released, and which reason wins when several hold at once. */
class HoldReleaseTest {

    @Test
    @DisplayName("each reason on its own")
    void eachReason() {
        assertNull(HoldRelease.reason(true, true, false, true, true, false, 5, 20), "still held");
        assertEquals("done", HoldRelease.reason(true, true, false, true, true, false, 20, 20));
        assertEquals("nextAction", HoldRelease.reason(true, true, false, true, true, true, 5, 20));
        assertEquals("focusLost", HoldRelease.reason(true, true, false, true, false, false, 5, 20));
        assertEquals("screenOpen", HoldRelease.reason(true, true, true, true, true, false, 5, 20));
        assertEquals("died", HoldRelease.reason(true, false, false, true, true, false, 5, 20));
        assertEquals("left", HoldRelease.reason(false, false, false, true, true, false, 5, 20));
    }

    @Test
    @DisplayName("the order: left, died, screenOpen, focusLost, nextAction, done")
    void order() {
        assertEquals("left", HoldRelease.reason(false, false, true, true, false, true, 20, 20));
        assertEquals("died", HoldRelease.reason(true, false, true, true, false, true, 20, 20));
        assertEquals("screenOpen", HoldRelease.reason(true, true, true, true, false, true, 20, 20));
        assertEquals("focusLost", HoldRelease.reason(true, true, false, true, false, true, 20, 20));
        assertEquals("nextAction", HoldRelease.reason(true, true, false, true, true, true, 20, 20));
    }

    @Test
    @DisplayName("a hold started without focus is not released for focus, since the game's use path has no focus check")
    void startedWithoutFocus() {
        assertNull(HoldRelease.reason(true, true, false, false, false, false, 5, 20));
        assertNull(HoldRelease.reason(true, true, false, false, true, false, 5, 20), "gaining focus is no loss");
        assertEquals("done", HoldRelease.reason(true, true, false, false, false, false, 20, 20));
    }

    @Test
    @DisplayName("per hold: a keepHold action leaves holds of other bindings running; a keepHold press of a held key is refused")
    void keepHoldPerHold() {
        java.util.List<Integer> held = java.util.Arrays.asList(-99, 17);
        assertNull(HoldRelease.heldRefusal(true, -100, held), "attack while use and forward are held");
        assertEquals("held", HoldRelease.heldRefusal(true, -99, held), "use again while use is held");
        assertEquals("held", HoldRelease.heldRefusal(true, 17, held));
        assertNull(HoldRelease.heldRefusal(false, -99, held), "without keepHold the hold ends first, then the press");
        assertNull(HoldRelease.heldRefusal(true, 0, java.util.Collections.singletonList(0)), "no key, no hold of it");
        // Each running hold then decides its own release: forward ends at its own time, sneak at its own.
        assertNull(HoldRelease.reason(true, true, false, true, true, false, 30, 60), "forward, 30 of 60");
        assertEquals("done", HoldRelease.reason(true, true, false, true, true, false, 30, 30), "sneak, 30 of 30");
        assertEquals("done", HoldRelease.reason(true, true, false, true, true, false, 60, 60), "forward, later");
    }

    @Test
    @DisplayName("which running inputs a step ends: one owner for every step, keepHold and focus included")
    void endings() {
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, HoldRelease.ending("attack", "", false, true));
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("attack", "", true, true), "keepHold keeps them");
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("hold", "use", true, true));
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, HoldRelease.ending("hold", "use", false, true));
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("key", "hold", true, true));
        assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, HoldRelease.ending("key", "press", false, true));
        assertEquals(HoldRelease.Ending.NAMED_RELEASED, HoldRelease.ending("key", "release", false, true),
                "a release ends only its own binding's hold");
        assertEquals(HoldRelease.Ending.ALL_FOCUS_LOST, HoldRelease.ending("focus", "lost", false, true),
                "focus lost ends them as a loss of focus, never as the next action");
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("focus", "gained", false, true));
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("keys", "", false, true));
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("view", "back", false, true));
        assertEquals(HoldRelease.Ending.NONE, HoldRelease.ending("look", "", false, false), "unparsed words end nothing");
        for (String action : new String[] {"select", "use", "drop", "inventory", "tooltip", "look", "face"}) {
            assertEquals(HoldRelease.Ending.ALL_NEXT_ACTION, HoldRelease.ending(action, "", false, true), action);
        }
    }

    @Test
    @DisplayName("a loss of focus by focus lost releases a hold started with focus, and not one started without")
    void emulatedFocusIsFocus() {
        assertEquals("focusLost", HoldRelease.reason(true, true, false, true, false, false, 5, 20));
        assertNull(HoldRelease.reason(true, true, false, false, false, false, 5, 20),
                "a hold started while focus was taken runs, as one started without focus");
    }
}
