package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Something a bundle can park on until it holds, is impossible, or a deadline passes.
 *
 * <p>Polled once per server tick by the execution that owns it. A condition answers only
 * pending, satisfied, cancelled, or failed; it never sleeps, never loops, and never holds a
 * deadline. Failed is for a condition that resolved with an answer the step must not pass, such
 * as a client that refused the action a step waited for; it carries its own failure key.
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
        PENDING, SATISFIED, CANCELLED, FAILED
    }

    /** The answer to one poll: a status and, for a cancellation, why; for a failure, its key and detail. */
    final class Poll {
        private static final Poll PENDING = new Poll(Status.PENDING, null, null, null);
        private static final Poll SATISFIED = new Poll(Status.SATISFIED, null, null, null);

        private final Status status;
        private final String reason;
        private final String key;
        private final String detail;

        private Poll(Status status, String reason, String key, String detail) {
            this.status = status;
            this.reason = reason;
            this.key = key;
            this.detail = detail;
        }

        public static Poll pending() {
            return PENDING;
        }

        public static Poll satisfied() {
            return SATISFIED;
        }

        /** @param reason a short machine-readable word, such as {@code dimensionChanged} */
        public static Poll cancelled(String reason) {
            return new Poll(Status.CANCELLED, reason == null ? "cancelled" : reason, null, null);
        }

        /**
         * The condition resolved, and the step fails with this key, which the step may tolerate
         * by declaration like any other.
         *
         * @param key    the failure key, such as {@code devtool.client.actionFailed}
         * @param detail what a reader needs beside the key, such as {@code reason=screenOpen}; may be null
         */
        public static Poll failed(String key, String detail) {
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("a failed poll needs a key");
            }
            return new Poll(Status.FAILED, null, key, detail);
        }

        public Status getStatus() {
            return status;
        }

        /** Null unless cancelled. */
        public String getReason() {
            return reason;
        }

        /** The failure key; null unless failed. */
        public String getKey() {
            return key;
        }

        /** What the failure adds to its key; null unless failed, and may be null then. */
        public String getDetail() {
            return detail;
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
     * Told once when the wait is over, whatever ended it: satisfied, failed, cancelled, timed out,
     * the caller gone, or the server stopping. A condition that holds state elsewhere, such
     * as an armed checkpoint, releases it here. The default does nothing.
     */
    default void ended() {
    }

    /**
     * Adds the condition's own fields to a record: what was waited for, and where it stood.
     *
     * <p>Called for the record that ends a wait, whatever the outcome, so a timeout can say
     * which chunk was still missing and a success can say how large the region was.
     */
    void describe(LogRecord record);

    /**
     * Adds what only a timeout record needs: what may explain why the condition never held. Called
     * after {@link #describe} on that record only. The default adds nothing.
     */
    default void describeTimeout(LogRecord record) {
    }
}
