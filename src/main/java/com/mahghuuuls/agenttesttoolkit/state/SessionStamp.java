package com.mahghuuuls.agenttesttoolkit.state;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Stamps the active session's identity onto a record.
 *
 * <h2>Why this lives in {@code state}</h2>
 *
 * <p>Records emitted from anywhere carry the session identity, including those from event
 * observers. But {@code observe} is not allowed to depend on {@code session}, so observers
 * cannot call the session package directly.
 *
 * <p>The obvious alternative, putting this next to {@code RecordContext} in {@code logging},
 * does not work either: it would make {@code logging} read {@code state}, and that dependency
 * is only allowed in the direction {@code state} to {@code logging}. Placing it the other way
 * would recreate a package cycle that an earlier review already had to remove once.
 *
 * <p>{@code state} is the one package that can legally do this. It already depends on
 * {@code logging}, and every subsystem may depend on it. Reading the current session and
 * writing two fields is a read of state, so the responsibility sits naturally here.
 *
 * <p>{@code session} keeps the lifecycle rules, which is where the interesting behavior is.
 */
public final class SessionStamp {

    private SessionStamp() {
    }

    /**
     * Adds {@code session} and {@code sessionTick} when a session is active.
     *
     * <p>With no session active both fields are omitted entirely rather than filled with a
     * placeholder, and whatever is being recorded still works.
     *
     * @return the same record, for chaining
     */
    public static LogRecord apply(LogRecord record) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session != null) {
            record.add("session", session.getName());
            record.add("sessionTick", session.getTick());
        }
        return record;
    }
}
