package com.mahghuuuls.agenttesttoolkit.client;

/**
 * The life of one press or click the toolkit makes, from plain values, so every path through it,
 * the cancelled ones included, is tested without a game.
 *
 * <p>A press by the binding or event route goes down when it is taken and is let go, and
 * recorded, at the end of the next tick. A click by the mouse queue goes into the queue at the
 * next tick start, is recorded at that tick's end, has its release queued at the tick start
 * after, and one tick later the binding is let go unless the real button is down: a mod that
 * cancels the release's mouse event would otherwise leave the binding pressed. Nothing goes into
 * the queue while the player is gone, a screen is open, or the game has lost its focus.
 */
final class PressStages {

    enum Stage {
        /** A queued click whose button goes into the queue at the next tick start. */
        QUEUE_DUE,
        /** Down; let go and recorded at the next tick end. */
        DOWN,
        /** A queued click recorded; its release goes into the queue at the next tick start. */
        QUEUE_UP_DUE,
        /** Its release was queued; the binding is let go at the next tick start unless the real button is down. */
        UNPRESS_DUE,
        /** Over: nothing more to do. */
        OVER
    }

    enum Act {
        NONE,
        /** Put the button going down into the queue. */
        ADD_PRESS,
        /** Never queued: recorded as refused, nothing pressed. */
        CANCEL,
        /** Let the key go and write the press's record. */
        LET_GO,
        /** Put the button going up into the queue. */
        ADD_RELEASE,
        /** Let the binding go now, without the queue. */
        UNPRESS,
        /** Let the binding go unless the real button is down. */
        UNPRESS_IF_UP
    }

    private PressStages() {
    }

    /** Why nothing may go into the mouse queue now; null when it may. */
    static String blocked(boolean worldOpen, boolean screenOpen, boolean focusLost) {
        if (!worldOpen) {
            return PlayerActionGate.REASON_NO_WORLD;
        }
        if (screenOpen) {
            // A click would go to the screen instead.
            return PlayerActionGate.REASON_SCREEN_OPEN;
        }
        if (focusLost) {
            // The game would take a queued click as a click into the window and take focus back.
            return PlayerActionGate.REASON_NO_FOCUS;
        }
        return null;
    }

    /** What to do at a tick start, before the game reads its input; {@code blocked} from {@link #blocked}. */
    static Act atStart(Stage stage, String blocked) {
        switch (stage) {
            case QUEUE_DUE:
                return blocked == null ? Act.ADD_PRESS : Act.CANCEL;
            case QUEUE_UP_DUE:
                return blocked == null ? Act.ADD_RELEASE : Act.UNPRESS;
            case UNPRESS_DUE:
                return Act.UNPRESS_IF_UP;
            default:
                return Act.NONE;
        }
    }

    /** What to do at a tick end, after the game has handled its input. */
    static Act atEnd(Stage stage) {
        return stage == Stage.DOWN ? Act.LET_GO : Act.NONE;
    }

    /** What {@code client focus lost} does at once: nothing waits for a later tick. */
    static Act onFocusLost(Stage stage) {
        switch (stage) {
            case QUEUE_DUE:
                return Act.CANCEL;
            case DOWN:
                return Act.LET_GO;
            case QUEUE_UP_DUE:
            case UNPRESS_DUE:
                return Act.UNPRESS;
            default:
                return Act.NONE;
        }
    }

    /** The stage after an act; {@code queued} for a click through the mouse queue. */
    static Stage after(Stage stage, Act act, boolean queued) {
        switch (act) {
            case ADD_PRESS:
                return Stage.DOWN;
            case LET_GO:
                return queued ? Stage.QUEUE_UP_DUE : Stage.OVER;
            case ADD_RELEASE:
                return Stage.UNPRESS_DUE;
            case CANCEL:
            case UNPRESS:
            case UNPRESS_IF_UP:
                return Stage.OVER;
            default:
                return stage;
        }
    }
}
