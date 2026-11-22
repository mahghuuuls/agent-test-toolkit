package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Whether the bundle run being worked on right now keeps its chat to itself, and who counts the
 * lines it kept back.
 *
 * <p>A quiet run is one an agent started, where the chat lines its commands would print are
 * noise covering the screen the person is looking at. The run's own wish decides first; a
 * nested run otherwise follows the run that started it, and a top-level run is quiet exactly when
 * the command inbox started it, so a person who types a bundle sees it as before.
 *
 * <p>Entered around each advance of a run and around the reply that starts one, on the thread
 * that runs the commands, and left in a {@code finally}. The innermost entered run decides, so a
 * run started from inside another is judged by its own setting. Kept per thread, so a command
 * arriving on another thread, such as through RCON, is never silenced by a run it is not part of.
 *
 * <p>Free of game types, so the rule and the counting are tested without a game.
 */
public final class QuietScope {

    /** A run as the scope sees it: whether it is quiet, and where its kept-back lines are counted. */
    public interface Run {
        boolean isQuiet();

        /** Called with each batch of chat lines kept out of the chat on this run's behalf. */
        void noteSuppressed(int lines);
    }

    /**
     * A single request an agent sent through the command inbox, outside any bundle run: quiet
     * like an inbox-started run, since its result already goes to its answer file and the log.
     * Nobody counts what it kept back; the caller says its failure in one line of its own.
     */
    public static final Run REQUEST = new Run() {
        @Override
        public boolean isQuiet() {
            return true;
        }

        @Override
        public void noteSuppressed(int lines) {
        }
    };

    private static final ThreadLocal<Deque<Run>> RUNS = new ThreadLocal<Deque<Run>>() {
        @Override
        protected Deque<Run> initialValue() {
            return new ArrayDeque<Run>();
        }
    };

    private QuietScope() {
    }

    /**
     * Whether a run is quiet.
     *
     * @param own     the run's own wish, from its command or its bundle file; null when it said nothing
     * @param parent  the quietness of the run that started this one; null for a top-level run
     * @param channel the channel a top-level run arrived through, as {@link RunOrigin} names it
     */
    public static boolean decide(Boolean own, Boolean parent, String channel) {
        if (own != null) {
            return own.booleanValue();
        }
        if (parent != null) {
            return parent.booleanValue();
        }
        return RunOrigin.INBOX.equals(channel);
    }

    /** Makes this run the one being worked on; pair with {@link #exit()} in a {@code finally}. */
    public static void enter(Run run) {
        if (run == null) {
            throw new IllegalArgumentException("run must not be null");
        }
        RUNS.get().push(run);
    }

    /** Leaves the run entered last. Leaving with none entered does nothing. */
    public static void exit() {
        Deque<Run> runs = RUNS.get();
        if (!runs.isEmpty()) {
            runs.pop();
        }
    }

    /** How many runs are entered on this thread. */
    public static int depth() {
        return RUNS.get().size();
    }

    /** Whether the work being done now is a single inbox request, not a bundle run. */
    public static boolean isRequest() {
        return RUNS.get().peek() == REQUEST;
    }

    /** Whether the run being worked on keeps its chat to itself. */
    public static boolean isQuiet() {
        Run current = RUNS.get().peek();
        return current != null && current.isQuiet();
    }

    /**
     * Counts lines kept out of the chat against the run being worked on.
     *
     * @return true when the run is quiet and the lines were counted, so the caller must not send
     *         them; false when they are to be sent as usual
     */
    public static boolean suppress(int lines) {
        Run current = RUNS.get().peek();
        if (current == null || !current.isQuiet()) {
            return false;
        }
        if (lines > 0) {
            current.noteSuppressed(lines);
        }
        return true;
    }
}
