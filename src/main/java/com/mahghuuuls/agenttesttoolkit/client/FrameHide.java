package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;

/**
 * What one captured frame leaves out, and the F1 state to put back after it.
 *
 * <p>Pure: it holds no game state, so what a frame leaves out and the F1 value given back are
 * unit-tested. The taker arms it at the start of the frame a capture falls in and disarms it at
 * that frame's end, after the capture, whatever the capture did; that order is in the taker.
 * The player's own F1 state is saved at the arm and given back at the disarm, so a picture
 * never changes how the player plays.
 */
final class FrameHide {

    private boolean armed;
    private boolean hud;
    private boolean savedHideGui;

    /**
     * Arms for one frame and returns the F1 value the frame is drawn with: hidden for
     * {@code hud}, the player's own otherwise.
     */
    boolean arm(String hide, boolean hideGuiNow) {
        armed = ScreenshotRequest.HIDE_CHAT.equals(hide) || ScreenshotRequest.HIDE_HUD.equals(hide);
        hud = armed && ScreenshotRequest.HIDE_HUD.equals(hide);
        savedHideGui = hideGuiNow;
        return hud || hideGuiNow;
    }

    /** Whether the chat lines are left out of this frame: for {@code chat}, and for {@code hud}, which includes them. */
    boolean cancelsChat() {
        return armed;
    }

    boolean isArmed() {
        return armed;
    }

    /** Disarms and returns the F1 value to put back: the player's own when this frame changed it. */
    boolean disarm(boolean hideGuiNow) {
        boolean restore = hud ? savedHideGui : hideGuiNow;
        armed = false;
        hud = false;
        return restore;
    }
}
