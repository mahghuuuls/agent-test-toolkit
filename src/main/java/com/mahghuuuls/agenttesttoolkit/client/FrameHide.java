package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.HideSet;

/**
 * What one captured frame leaves out, and the F1 state to put back after it.
 *
 * <p>Pure: it holds no game state, so what a frame leaves out and the F1 value given back are
 * unit-tested. The taker arms it at the start of the frame a capture falls in and disarms it at
 * that frame's end, after the capture, whatever the capture did; that order is in the taker.
 * The player's own F1 state is saved at the arm and given back at the disarm, so a picture
 * never changes how the player plays. What to leave out is a set: {@code chat}, {@code hud},
 * {@code toasts}, alone or together.
 */
final class FrameHide {

    private boolean armed;
    private boolean chat;
    private boolean hud;
    private boolean toasts;
    private boolean savedHideGui;

    /**
     * Arms for one frame and returns the F1 value the frame is drawn with: hidden for
     * {@code hud}, the player's own otherwise.
     */
    boolean arm(String hide, boolean hideGuiNow) {
        chat = HideSet.has(hide, HideSet.CHAT);
        hud = HideSet.has(hide, HideSet.HUD);
        toasts = HideSet.has(hide, HideSet.TOASTS);
        armed = chat || hud || toasts;
        savedHideGui = hideGuiNow;
        return hud || hideGuiNow;
    }

    /** Whether the chat lines are left out of this frame: for {@code chat}, and for {@code hud}, which includes them. */
    boolean cancelsChat() {
        return armed && (chat || hud);
    }

    /** Whether the toasts are left out of this frame. */
    boolean hidesToasts() {
        return armed && toasts;
    }

    boolean isArmed() {
        return armed;
    }

    /** Disarms and returns the F1 value to put back: the player's own when this frame changed it. */
    boolean disarm(boolean hideGuiNow) {
        boolean restore = hud ? savedHideGui : hideGuiNow;
        armed = false;
        chat = false;
        hud = false;
        toasts = false;
        return restore;
    }
}
