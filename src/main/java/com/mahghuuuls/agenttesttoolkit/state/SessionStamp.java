package com.mahghuuuls.agenttesttoolkit.state;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;

/**
 * Stamps the active session's identity onto a record.
 *
 * <h2>Why this lives in {@code state}</h2>
 *
 * <p>REQ-053 requires session identity on records emitted from everywhere, including event
 * observers. Dependency rule 3 forbids {@code observe} from depending on {@code session}, so
 * observers cannot call the session package directly.
 *
 * <p>The obvious alternative, putting this next to {@code RecordContext} in {@code logging},
 * does not work either: it would make {@code logging} read {@code state}, and rule 2 permits
 * that dependency only in the direction {@code state} to {@code logging}. Placing it the other
 * way would recreate the package cycle that IMP-002's review already had to remove once.
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
     * <p>REQ-053 and REQ-054: with no session active both fields are omitted entirely rather
     * than filled with a placeholder, and whatever is being recorded still works.
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
