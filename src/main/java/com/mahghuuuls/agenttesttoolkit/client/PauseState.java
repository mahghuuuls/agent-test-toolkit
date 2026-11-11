package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Whether a single-player game is paused by an open screen, and when that changes.
 *
 * <p>Pure, so the change rule is unit-tested: the note beside the command inbox is written or
 * removed only when the pause starts, ends, or another screen pauses it, never every tick.
 */
final class PauseState {

    /** What the note must do this tick. */
    enum Change {
        NONE, WRITE, REMOVE
    }

    private boolean paused;
    private String screen;

    /**
     * @param pausedNow whether the game is paused now
     * @param screenNow the open screen's class name, or null
     */
    Change update(boolean pausedNow, String screenNow) {
        if (!pausedNow) {
            boolean was = paused;
            paused = false;
            screen = null;
            return was ? Change.REMOVE : Change.NONE;
        }
        boolean same = paused && (screenNow == null ? screen == null : screenNow.equals(screen));
        paused = true;
        screen = screenNow;
        return same ? Change.NONE : Change.WRITE;
    }

    boolean isPaused() {
        return paused;
    }

    String getScreen() {
        return screen;
    }
}
