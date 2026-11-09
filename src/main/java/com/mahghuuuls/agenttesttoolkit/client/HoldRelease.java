package com.mahghuuuls.agenttesttoolkit.client;

/**
 * Whether a held input must be released now, and why, from plain values, so every reason and
 * their order are tested without a game. Null means it stays pressed for this tick.
 */
final class HoldRelease {

    /** The player left the world, or the connection ended. */
    static final String RELEASED_LEFT = "left";
    /** The player died. */
    static final String RELEASED_DIED = "died";
    /** A screen opened; the game unpresses every key then anyway. */
    static final String RELEASED_SCREEN_OPEN = "screenOpen";
    /** The game window had focus when the hold started and lost it. */
    static final String RELEASED_FOCUS_LOST = "focusLost";
    /** Another player action arrived; it runs after the release. */
    static final String RELEASED_NEXT_ACTION = "nextAction";
    /** The hold lasted the ticks asked. */
    static final String RELEASED_DONE = "done";

    private HoldRelease() {
    }

    /**
     * @param worldOpen       a world and its player exist
     * @param alive           the player is alive
     * @param screenOpen      a screen is open
     * @param focusedAtStart  the window had focus when the hold started
     * @param focusedNow      the window has focus now
     * @param nextAction      another player action is waiting
     * @param heldTicks       ticks the input has been held so far
     * @param askedTicks      ticks the hold was asked for
     */
    static String reason(boolean worldOpen, boolean alive, boolean screenOpen, boolean focusedAtStart,
                         boolean focusedNow, boolean nextAction, int heldTicks, int askedTicks) {
        if (!worldOpen) {
            return RELEASED_LEFT;
        }
        if (!alive) {
            return RELEASED_DIED;
        }
        if (screenOpen) {
            return RELEASED_SCREEN_OPEN;
        }
        // Only a loss: a hold started without focus keeps going, since the game's use path has
        // no focus check (a held attack does need it to keep breaking a block).
        if (focusedAtStart && !focusedNow) {
            return RELEASED_FOCUS_LOST;
        }
        if (nextAction) {
            return RELEASED_NEXT_ACTION;
        }
        if (heldTicks >= askedTicks) {
            return RELEASED_DONE;
        }
        return null;
    }
}
