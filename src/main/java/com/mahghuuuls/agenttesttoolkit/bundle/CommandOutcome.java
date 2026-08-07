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

    private static final CommandOutcome SUCCESS = new CommandOutcome(true, null);

    private final boolean succeeded;
    private final String detail;

    private CommandOutcome(boolean succeeded, String detail) {
        this.succeeded = succeeded;
        this.detail = detail;
    }

    public static CommandOutcome success() {
        return SUCCESS;
    }

    /** Succeeded, with a note worth recording. Used where vanilla raised an error the toolkit
     * deliberately does not treat as failure, so the log still shows what happened. */
    public static CommandOutcome success(String detail) {
        return detail == null || detail.isEmpty() ? SUCCESS : new CommandOutcome(true, detail);
    }

    public static CommandOutcome failure(String detail) {
        return new CommandOutcome(false, detail);
    }

    public boolean succeeded() {
        return succeeded;
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
