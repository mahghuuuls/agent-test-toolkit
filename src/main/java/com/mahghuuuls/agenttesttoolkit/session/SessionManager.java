package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

import java.util.UUID;

/**
 * Session lifecycle: start, stop, the tick counter, and the operator binding.
 *
 * <p>Owns the replace rule. Starting a session while one is active stops the previous
 * one first, emitting its stop record, rather than erroring. An agent re-running a setup
 * bundle should not have to know whether a session is already open, and a silently nested or
 * silently discarded session would make the {@code session} field on later records ambiguous.
 *
 * <p>The operator binding belongs to the session: it can only exist while a session is active,
 * and it ends when the session ends or the player leaves. Every change to it is recorded, so
 * the log can always say who the operator was at any moment.
 */
public final class SessionManager {

    /**
     * Run after every stop, whatever caused it. Session-owned state kept in packages this one
     * may not depend on, such as operator checkpoints in {@code wait}, is forgotten through it.
     */
    private static volatile Runnable stopHook;

    private SessionManager() {
    }

    /** Installed once at mod initialisation; null removes it. */
    public static void setStopHook(Runnable hook) {
        stopHook = hook;
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
        // The session belongs to the world it starts in (ARC-019).
        DiagnosticSession session = new DiagnosticSession(name, ToolkitState.getCurrentWorldKey());
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
        return stop(context, null);
    }

    /**
     * As {@link #stop(RecordContext.Snapshot)}, with a {@code reason} on the stop record, such
     * as {@code worldChanged}; null omits it.
     */
    public static String stop(RecordContext.Snapshot context, String reason) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session == null) {
            return null;
        }
        // The binding goes first, so its record still carries the session it belonged to.
        unbindOperator("sessionStop", context);
        // Fixtures belong to the session too; the entities stay, the handles do not.
        ToolkitState.clearFixtures();
        ToolkitLog.write(RecordContext.stamp(LogRecord.of(EventType.SESSION_STOP), context)
                .add("session", session.getName())
                .add("sessionTick", session.getTick())
                .add("reason", reason));
        ToolkitState.setActiveSession(null);
        Runnable hook = stopHook;
        if (hook != null) {
            hook.run();
        }
        return session.getName();
    }

    /**
     * Binds the session's operator, replacing any previous binding.
     *
     * @return false when no session is active, in which case nothing is bound or recorded
     */
    public static boolean bindOperator(UUID uuid, String name, RecordContext.Snapshot context) {
        if (!ToolkitState.hasActiveSession()) {
            return false;
        }
        OperatorBinding previous = ToolkitState.getOperator();
        OperatorBinding binding = new OperatorBinding(uuid, name);
        ToolkitState.setOperator(binding);
        LogRecord record = operatorRecord(context, "bound", binding);
        if (previous != null) {
            record.add("replaced", previous.getName());
        }
        ToolkitLog.write(record);
        return true;
    }

    /**
     * Ends the operator binding, if any, with a record saying why.
     *
     * @param reason a short machine-readable word: {@code disconnect}, {@code sessionStop},
     *               or {@code command}
     * @return the binding that was cleared, or null when there was none
     */
    public static OperatorBinding unbindOperator(String reason, RecordContext.Snapshot context) {
        OperatorBinding binding = ToolkitState.getOperator();
        if (binding == null) {
            return null;
        }
        ToolkitState.setOperator(null);
        ToolkitLog.write(operatorRecord(context, "unbound", binding).add("reason", reason));
        return binding;
    }

    private static LogRecord operatorRecord(RecordContext.Snapshot context, String action,
                                            OperatorBinding binding) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.OPERATOR), context);
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session != null) {
            record.add("session", session.getName());
            record.add("sessionTick", session.getTick());
        }
        return record.add("action", action)
                .add("name", binding.getName())
                .add("uuid", binding.getUuid().toString());
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
