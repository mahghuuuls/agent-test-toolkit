package com.mahghuuuls.agenttesttoolkit.wait;

/**
 * What a client's reports mean for a bundle step that waits on them: passed, not yet known, or
 * failed with a key and the client's own words.
 *
 * <p>Pure, so every result and release word is checked without a game. The wait condition reads
 * the reports and asks here; nothing else decides what a report means for a waiting step, so a
 * {@code client await} and a step's own {@code wait=} can never disagree.
 *
 * <p>A step that waits for {@code started} passes on the client's first answer when that answer is
 * not a refusal; one that waits for {@code done} needs the final answer. A step with an expected
 * target always waits for the final answer, and checks the target the client reported under the
 * crosshair: for a hold that is the target of its first answer, which is the only one that names it.
 */
public final class ClientWaitDecision {

    /** The client did not act: it refused, failed, or left. The client's reason is in the detail. */
    public static final String KEY_ACTION_FAILED = "devtool.client.actionFailed";
    /** A hold or turn ended for another reason than its ticks running out. */
    public static final String KEY_RELEASED_EARLY = "devtool.client.releasedEarly";
    /** The client reported another entity under the crosshair than the one expected, or none. */
    public static final String KEY_WRONG_TARGET = "devtool.client.wrongTarget";
    /** The expected entity was under the crosshair, but the client saw it dying or dead. */
    public static final String KEY_TARGET_DEAD = "devtool.client.targetDead";

    /** Pass as soon as the client says the action began. */
    public static final String MODE_STARTED = "started";
    /** Pass only once the client says the action finished. */
    public static final String MODE_DONE = "done";

    /** The release word of a hold or turn whose ticks ran out. */
    static final String RELEASED_DONE = "done";

    /** What a poll makes of the reports so far. */
    public enum Status {
        SATISFIED, PENDING, FAILED
    }

    /** A status, and for a failure its key and detail. */
    public static final class Decision {
        private static final Decision PENDING = new Decision(Status.PENDING, null, null);
        private static final Decision SATISFIED = new Decision(Status.SATISFIED, null, null);

        private final Status status;
        private final String key;
        private final String detail;

        private Decision(Status status, String key, String detail) {
            this.status = status;
            this.key = key;
            this.detail = detail;
        }

        static Decision failed(String key, String detail) {
            return new Decision(Status.FAILED, key, detail);
        }

        public Status getStatus() {
            return status;
        }

        /** The failure key; null unless failed. */
        public String getKey() {
            return key;
        }

        /** The report's words a reader needs beside the key, such as {@code reason=screenOpen}; null unless failed. */
        public String getDetail() {
            return detail;
        }
    }

    private ClientWaitDecision() {
    }

    /**
     * Decides from the reports seen so far.
     *
     * @param mode         {@link #MODE_STARTED} or {@link #MODE_DONE}; an expected target always counts as done
     * @param result       the client's result: for {@code started} its first answer, for {@code done} its
     *                     latest; null before any answer
     * @param reason       that answer's reason; null or empty when it gave none
     * @param released     that answer's release word, for a hold or turn; null for a step without one
     * @param heldTicks    that answer's ticks held, for a hold; null otherwise
     * @param expectedUuid the entity the step expects under the crosshair; null for no expectation
     * @param reportedUuid the entity the client reported under the crosshair; null when it reported none
     * @param alive        the client's word for that entity, {@code true} or {@code false}; null when it did
     *                     not say, as a client older than 1.10.0 does not
     */
    public static Decision decide(String mode, String result, String reason, String released, String heldTicks,
                                  String expectedUuid, String reportedUuid, String alive) {
        if (result == null) {
            return Decision.PENDING;
        }
        boolean expecting = expectedUuid != null && !expectedUuid.isEmpty();
        if ("refused".equals(result) || "failed".equals(result) || "left".equals(result)) {
            // The client did not act, so what the crosshair held does not matter.
            return Decision.failed(KEY_ACTION_FAILED, "result=" + result + words("reason", reason));
        }
        if ("started".equals(result)) {
            return MODE_STARTED.equals(mode) && !expecting ? Decision.SATISFIED : Decision.PENDING;
        }
        if (expecting) {
            // The click met the wrong thing before it could matter how long it lasted.
            if (reportedUuid == null || !expectedUuid.equalsIgnoreCase(reportedUuid)) {
                return Decision.failed(KEY_WRONG_TARGET, "expected=" + expectedUuid + " entityUuid="
                        + (reportedUuid == null ? "none" : reportedUuid));
            }
            if ("false".equals(alive)) {
                return Decision.failed(KEY_TARGET_DEAD, "entityUuid=" + reportedUuid + " alive=false");
            }
        }
        if (released != null && !released.isEmpty() && !RELEASED_DONE.equals(released)) {
            return Decision.failed(KEY_RELEASED_EARLY, "released=" + released + words("heldTicks", heldTicks));
        }
        return Decision.SATISFIED;
    }

    /** {@code " name=value"}, or nothing when there is no value. */
    private static String words(String name, String value) {
        return value == null || value.isEmpty() ? "" : " " + name + "=" + value;
    }
}
