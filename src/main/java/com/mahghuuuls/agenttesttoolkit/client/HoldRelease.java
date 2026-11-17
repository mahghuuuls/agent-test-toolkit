package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;

/**
 * Whether a held input must be released now, and why, from plain values, so every reason and
 * their order are tested without a game. Null means it stays pressed for this tick.
 *
 * <p>Decided per held input: each key binding held, and a turn of the view spread over ticks,
 * ends on its own with its own reason. Which running inputs a newly taken step ends, and why, is
 * decided here too, by {@link #ending}.
 *
 * <p>Focus here is the game's: the real window's, unless {@code client focus lost} took it away
 * until the game takes focus again.
 */
final class HoldRelease {

    /** A press that keeps the running holds asked for a binding that is itself being held. */
    static final String REASON_HELD = "held";

    /** The player left the world, or the connection ended. */
    static final String RELEASED_LEFT = "left";
    /** The player died. */
    static final String RELEASED_DIED = "died";
    /** A screen opened; the game unpresses every key then anyway. */
    static final String RELEASED_SCREEN_OPEN = "screenOpen";
    /** The game had focus when the hold started and lost it, really or by {@code client focus lost}. */
    static final String RELEASED_FOCUS_LOST = "focusLost";
    /** {@code client key <binding> release} ended the hold of that binding. */
    static final String RELEASED_RELEASED = "released";
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
     * @param focusedAtStart  the game had focus when the hold started
     * @param focusedNow      the game has focus now
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

    /**
     * The refusal of an action that keeps the running holds but presses a key already held by
     * one of them: pressing it again could not be told from the hold, and releasing it would
     * end the hold. Null when the action may go ahead.
     */
    static String heldRefusal(boolean keepHold, int pressedKeyCode, java.util.Collection<Integer> heldKeyCodes) {
        return keepHold && pressedKeyCode != 0 && heldKeyCodes.contains(pressedKeyCode) ? REASON_HELD : null;
    }

    /** What a step taken now does to the running holds and turn. */
    enum Ending {
        /** Nothing ends: the step presses nothing, or keeps the running inputs. */
        NONE,
        /** Every hold and the turn end as the next action, before the step runs. */
        ALL_NEXT_ACTION,
        /** Every hold and the turn end as a loss of focus; the step does it once it may act. */
        ALL_FOCUS_LOST,
        /** Only the hold of the binding the step names ends, as released; the step does it once it may act. */
        NAMED_RELEASED
    }

    /**
     * Which running inputs a step ends. Listing the bindings, setting the camera view and
     * describing the open screen press nothing; {@code focus lost} ends everything as a loss of focus; {@code key ... release} ends
     * only its own binding's hold; any other step ends everything as the next action, unless it
     * asks to keep the holds. A step whose words did not parse ends nothing.
     *
     * @param step     the step's name, a player action's or an input step's
     * @param mode     the input step's word after its name ({@code release}, {@code lost}, ...); empty otherwise
     * @param keepHold the step asked to keep the running holds
     * @param parsed   the step's words parsed on this client
     */
    static Ending ending(String step, String mode, boolean keepHold, boolean parsed) {
        if (!parsed || InputOptions.KEYS.equals(step) || InputOptions.VIEW.equals(step)
                || ScreenOptions.SCREEN.equals(step)) {
            return Ending.NONE;
        }
        if (InputOptions.FOCUS.equals(step)) {
            return InputOptions.FOCUS_LOST.equals(mode) ? Ending.ALL_FOCUS_LOST : Ending.NONE;
        }
        if (InputOptions.KEY.equals(step) && InputOptions.RELEASE.equals(mode)) {
            return Ending.NAMED_RELEASED;
        }
        return keepHold ? Ending.NONE : Ending.ALL_NEXT_ACTION;
    }
}
