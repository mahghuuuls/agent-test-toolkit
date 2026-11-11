package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The frame a hidden capture falls in: what it leaves out, and that F1 comes back as it was. */
class FrameHideTest {

    @Test
    @DisplayName("chat: the chat lines are left out, F1 is not touched")
    void chat() {
        FrameHide frame = new FrameHide();
        assertFalse(frame.arm("chat", false), "F1 as the player has it");
        assertTrue(frame.cancelsChat());
        assertFalse(frame.disarm(false));
        assertFalse(frame.cancelsChat(), "disarmed after the frame");
    }

    @Test
    @DisplayName("hud: F1 is on for the frame and the player's own state comes back after")
    void hud() {
        FrameHide frame = new FrameHide();
        assertTrue(frame.arm("hud", false));
        assertTrue(frame.cancelsChat(), "hud includes chat, also when a screen keeps the HUD drawn");
        assertFalse(frame.disarm(true), "the player had F1 off");

        FrameHide already = new FrameHide();
        assertTrue(already.arm("hud", true));
        assertTrue(already.disarm(true), "the player had F1 on: it stays on");
    }

    @Test
    @DisplayName("nothing to hide: not armed, and the disarm changes nothing")
    void none() {
        FrameHide frame = new FrameHide();
        assertTrue(frame.arm("", true));
        assertFalse(frame.isArmed());
        assertFalse(frame.cancelsChat());
        assertTrue(frame.disarm(true));
    }
}
