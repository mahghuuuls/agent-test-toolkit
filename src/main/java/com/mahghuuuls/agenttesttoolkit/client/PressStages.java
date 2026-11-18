package com.mahghuuuls.agenttesttoolkit.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The life of one press, click or queued hold the toolkit makes, from plain values, so every
 * path through it, the cancelled ones included, is tested without a game.
 *
 * <p>A press by the binding or event route goes down when it is taken and is let go, and
 * recorded, at the end of the next tick. A press or click through an input queue goes into the
 * queue at the next tick start, is recorded at that tick's end, has its release queued at the
 * tick start after, and one tick later the binding is let go unless the real button or key is
 * down: a mod that cancels the release's event would otherwise leave the binding pressed.
 *
 * <p>A key held through a queue lives the same way with a longer {@link Stage#DOWN}: its key-down
 * goes into the queue at the next tick start, it stays down until the hold ends, and its key-up
 * then goes into the queue at the next tick start, the binding let go a tick later unless the
 * real key is down. A hold that ends before its key-down went in is refused, and nothing of it is
 * queued. At one tick start every key-up goes in before any key-down, so a key pressed again
 * right after it was let go goes up and then down, in that order. A hold or a key press of the
 * same key by the binding route takes over a key-up not yet queued, a queued hold's or a queued
 * press's, which is dropped: queued, it would let the new input's binding go for a tick.
 *
 * <p>Nothing goes into a queue while the player is gone, a screen is open, or the game has lost
 * its focus.
 */
final class PressStages {

    enum Stage {
        /** Queued: its key-down goes into the queue at the next tick start. */
        QUEUE_DUE,
        /** Down: a press is let go and recorded at the next tick end; a hold stays down until it ends. */
        DOWN,
        /** Queued and let go: its key-up goes into the queue at the next tick start. */
        QUEUE_UP_DUE,
        /** Its key-up was queued; the binding is let go at the next tick start unless the real key is down. */
        UNPRESS_DUE,
        /** Over: nothing more to do. */
        OVER
    }

    enum Act {
        NONE,
        /** Put the key-down into the queue. */
        ADD_PRESS,
        /** Never queued: recorded as refused, nothing pressed. */
        CANCEL,
        /** Never queued: the game would count the press on another binding; recorded as failed. */
        NOT_COUNTED,
        /** Let the key go and write the press's or the hold's record. */
        LET_GO,
        /** Put the key-up into the queue. */
        ADD_RELEASE,
        /** Let the binding go now, without the queue. */
        UNPRESS,
        /** Let the binding go unless the real key is down. */
        UNPRESS_IF_UP,
        /** A new hold of the key took it over: nothing more is queued or let go. */
        DROP
    }

    private PressStages() {
    }

    /** Why nothing may go into an input queue now; null when it may. */
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

    /**
     * Whether a press of a binding sets the binding down itself: a {@code key} press by the
     * binding route does, for its one tick. Through a queue the game sets it from the events, and
     * a click only counts its press, as a player action's does.
     */
    static boolean setsState(boolean keyStep, boolean queued) {
        return keyStep && !queued;
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

    /**
     * What a key-down due now does once the game's own lookup has said which binding it would
     * count the press on: {@code countedOn} names another binding, or {@code none}, and is null
     * when it is the binding asked or nothing can be said. A press counted elsewhere is never
     * queued, so no other binding acts on it; a hold goes down all the same, as a real key does,
     * and its {@code started} record names where the press went.
     */
    static Act beforeAdd(Act act, String countedOn, boolean hold) {
        return act == Act.ADD_PRESS && countedOn != null && !hold ? Act.NOT_COUNTED : act;
    }

    /** What to do with a press at a tick end, after the game has handled its input. */
    static Act atEnd(Stage stage) {
        return stage == Stage.DOWN ? Act.LET_GO : Act.NONE;
    }

    /** Whether a queued hold has a tick to count: only once its key-down went into the queue. */
    static boolean holdRuns(Stage stage) {
        return stage == Stage.DOWN;
    }

    /**
     * What ending a queued hold does, for any reason: refused when its key-down never went in,
     * and otherwise recorded, with its key-up due at the next tick start.
     */
    static Act onHoldEnd(Stage stage) {
        switch (stage) {
            case QUEUE_DUE:
                return Act.CANCEL;
            case DOWN:
                return Act.LET_GO;
            default:
                return Act.NONE;
        }
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

    /**
     * What a new hold or key press of the same key by the binding route does to a queued key-up,
     * an ended queued hold's or queued press's: one not yet queued is dropped, since the new input
     * keeps the binding down and lets it go itself.
     */
    static Act onKeyTaken(Stage stage) {
        return stage == Stage.QUEUE_UP_DUE ? Act.DROP : Act.NONE;
    }

    /**
     * Whether a new input takes over the queued key-ups of its key: one that is not queued and
     * sets the binding down itself, a hold or a key press by the binding route. A click by the
     * binding route only counts its press; with the key-up dropped, nothing would let the binding go.
     */
    static boolean takesKey(boolean hold, boolean queued, boolean setsState) {
        return !queued && (hold || setsState);
    }

    /** The stage after an act; {@code queued} for one through an input queue. */
    static Stage after(Stage stage, Act act, boolean queued) {
        switch (act) {
            case ADD_PRESS:
                return Stage.DOWN;
            case LET_GO:
                return queued ? Stage.QUEUE_UP_DUE : Stage.OVER;
            case ADD_RELEASE:
                return Stage.UNPRESS_DUE;
            case CANCEL:
            case NOT_COUNTED:
            case UNPRESS:
            case UNPRESS_IF_UP:
            case DROP:
                return Stage.OVER;
            default:
                return stage;
        }
    }

    /**
     * The order to act on these stages at one tick start, as places in the list: every key-up and
     * every letting go first, then the rest, each part in the list's order. A key-down queued
     * before the key-up of the same key would be undone by it at once.
     */
    static List<Integer> startOrder(List<Stage> stages) {
        List<Integer> order = new ArrayList<Integer>(stages.size());
        for (int i = 0; i < stages.size(); i++) {
            if (releases(stages.get(i))) {
                order.add(i);
            }
        }
        for (int i = 0; i < stages.size(); i++) {
            if (!releases(stages.get(i))) {
                order.add(i);
            }
        }
        return order;
    }

    private static boolean releases(Stage stage) {
        return stage == Stage.QUEUE_UP_DUE || stage == Stage.UNPRESS_DUE;
    }
}
