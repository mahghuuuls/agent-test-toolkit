package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

/**
 * Session lifecycle: start, stop, and the tick counter.
 *
 * <p>Owns the REQ-051 replace rule. Starting a session while one is active stops the previous
 * one first, emitting its stop record, rather than erroring. An agent re-running a setup
 * bundle should not have to know whether a session is already open, and a silently nested or
 * silently discarded session would make the {@code session} field on later records ambiguous.
 */
public final class SessionManager {

    private SessionManager() {
    }

    /**
     * Starts a named session, stopping any active one first.
     *
     * @return the previous session's name when one was replaced, otherwise null
     */
    public static String start(String name, RecordContext.Snapshot context) {
        String replaced = null;
        if (ToolkitState.hasActiveSession()) {
            replaced = ToolkitState.getActiveSession().getName();
            stop(context);
        }
        DiagnosticSession session = new DiagnosticSession(name);
        ToolkitState.setActiveSession(session);
        ToolkitLog.write(RecordContext.stamp(LogRecord.of(EventType.SESSION_START), context)
                .add("session", name));
        return replaced;
    }

    /**
     * Stops the active session.
     *
     * @return the stopped session's name, or null when none was active
     */
    public static String stop(RecordContext.Snapshot context) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session == null) {
            return null;
        }
        ToolkitLog.write(RecordContext.stamp(LogRecord.of(EventType.SESSION_STOP), context)
                .add("session", session.getName())
                .add("sessionTick", session.getTick()));
        ToolkitState.setActiveSession(null);
        return session.getName();
    }

    /**
     * Advances the active session's tick counter. Called once per server tick.
     *
     * <p>No-op when no session is active, which is the normal resting state.
     */
    public static void onServerTick() {
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session != null) {
            session.advanceTick();
        }
    }

}
