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
}
