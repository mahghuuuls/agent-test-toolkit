package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * The result of dispatching one command.
 *
 * <p>Failure is defined narrowly, as a raised command error and nothing else. A
 * command that runs and affects nothing has <b>succeeded</b>. That distinction is the whole
 * reason this type exists rather than a bare boolean with an ad hoc convention: a bundle that
 * halts because {@code kill} found nothing to kill would make teardown bundles unusable on a
 * second run, which is precisely the repetitive manual work the toolkit exists to remove.
 *
 * <p>The detail is carried so a failure record can say what went wrong. A failure is never
 * reported without enough context to identify it.
 */
public final class CommandOutcome {

    /** A failure tolerated by the toolkit's own table of removal commands, without a declaration. */
    public static final String SOURCE_BUILT_IN = "builtIn";
    /** A failure tolerated because the step declared its key. */
    public static final String SOURCE_DECLARED = "declared";

    private static final CommandOutcome SUCCESS = new CommandOutcome(true, null, null, null);

    private final boolean succeeded;
    private final String detail;
    private final String toleratedKey;
    private final String toleratedSource;

    private CommandOutcome(boolean succeeded, String detail, String toleratedKey, String toleratedSource) {
        this.succeeded = succeeded;
        this.detail = detail;
        this.toleratedKey = toleratedKey;
        this.toleratedSource = toleratedSource;
    }

    public static CommandOutcome success() {
        return SUCCESS;
    }

    /** Succeeded, with a note worth recording. Used where vanilla raised an error the toolkit
     * deliberately does not treat as failure, so the log still shows what happened. */
    public static CommandOutcome success(String detail) {
        return detail == null || detail.isEmpty() ? SUCCESS : new CommandOutcome(true, detail, null, null);
    }

    /**
     * A failure the toolkit chose not to treat as one, because the command is one of the built-in
     * removals that may match nothing ({@link #SOURCE_BUILT_IN}) or it declared the key
     * ({@link #SOURCE_DECLARED}).
     *
     * <p>Distinct from {@link #success(String)}, which is also used for "handled by another
     * mod": only an outcome made here is a tolerated failure, and only it is recorded as one.
     * Callers ask {@link #getToleratedKey()} rather than reading the note, so the decision is
     * made once, by the classifier.
     */
    public static CommandOutcome tolerated(String key, String note, String source) {
        if (key == null || key.isEmpty()) {
            // A tolerated outcome without its key would record nothing a reader could use,
            // and would quietly become an ordinary success with a note.
            throw new IllegalArgumentException("a tolerated outcome must name its key");
        }
        if (!SOURCE_BUILT_IN.equals(source) && !SOURCE_DECLARED.equals(source)) {
            // The records count the two apart; an outcome that is neither would be counted as neither.
            throw new IllegalArgumentException("a tolerated outcome is built in or declared: " + source);
        }
        return new CommandOutcome(true, note, key, source);
    }

    public static CommandOutcome failure(String detail) {
        return new CommandOutcome(false, detail, null, null);
    }

    public boolean succeeded() {
        return succeeded;
    }

    /** @return the key of the failure that was tolerated, or null when this was not one. */
    public String getToleratedKey() {
        return toleratedKey;
    }

    /** @return {@link #SOURCE_BUILT_IN} or {@link #SOURCE_DECLARED} for a tolerated failure; null otherwise. */
    public String getToleratedSource() {
        return toleratedSource;
    }

    /** @return why it failed, or a note about a tolerated error; null when there is nothing to say. */
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return (succeeded ? "success" : "failure") + (detail == null ? "" : ": " + detail);
    }
}
