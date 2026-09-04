package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Something a bundle can park on until it holds, is impossible, or a deadline passes.
 *
 * <p>Polled once per server tick by the execution that owns it. A condition answers only
 * pending, satisfied, or cancelled; it never sleeps, never loops, and never holds a deadline.
 * The deadline belongs to the execution, which is what turns "still pending" into "timed
 * out" and records it as an ordinary command failure. Keeping the clock out of the condition
 * is what makes a condition testable without a game and a timeout impossible to forget.
 *
 * <p>Implementations live outside this package because they need a world or a player; this
 * interface stays free of Minecraft types so the execution state machine remains checkable
 * with a fake.
 */
public interface WaitCondition {

    /** What one poll found. */
    enum Status {
        PENDING, SATISFIED, CANCELLED
    }

    /** The answer to one poll: a status and, for a cancellation, why. */
    final class Poll {
        private static final Poll PENDING = new Poll(Status.PENDING, null);
        private static final Poll SATISFIED = new Poll(Status.SATISFIED, null);

        private final Status status;
        private final String reason;

        private Poll(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public static Poll pending() {
            return PENDING;
        }

        public static Poll satisfied() {
            return SATISFIED;
        }

        /** @param reason a short machine-readable word, such as {@code dimensionChanged} */
        public static Poll cancelled(String reason) {
            return new Poll(Status.CANCELLED, reason == null ? "cancelled" : reason);
        }

        public Status getStatus() {
            return status;
        }

        /** Null unless cancelled. */
        public String getReason() {
            return reason;
        }
    }

    /**
     * Checks the condition now.
     *
     * @param elapsedTicks ticks since the wait began, for a condition that reports progress
     */
    Poll poll(int elapsedTicks);

    /** A short name for the kind of condition, such as {@code chunks}. */
    String kind();

    /**
     * Adds the condition's own fields to a record: what was waited for, and where it stood.
     *
     * <p>Called for the record that ends a wait, whatever the outcome, so a timeout can say
     * which chunk was still missing and a success can say how large the region was.
     */
    void describe(LogRecord record);
}
