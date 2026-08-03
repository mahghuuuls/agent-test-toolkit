package com.mahghuuuls.agenttesttoolkit.state;

/**
 * Process-scoped mutable toolkit state: the active diagnostic session, and later the enabled
 * logging categories and their filters.
 *
 * <h2>Why this is a static that is never cleared on server stop</h2>
 *
 * <p>ARC-001. REQ-052 requires the active session to survive leaving a single player world and
 * returning to the title screen, so that a disconnect and reconnect cycle can be tested inside
 * one session. In single player, leaving a world stops the integrated server and fires
 * {@code FMLServerStoppedEvent}, which Forge documents as the place to reset static state.
 *
 * <p>Following that convention here would break REQ-052. <b>Do not add a handler that clears
 * this class on server stop.</b> The state is meant to die with the JVM and nothing sooner,
 * which is also the boundary {@code latest.log} itself uses, so a session and a log file cover
 * the same span.
 *
 * <p>Bundle executions are the opposite case and genuinely must be discarded on server stop,
 * because they hold a sender and dispatch through a server that no longer exists. See ARC-002.
 * The two look alike and behave oppositely; that is why both are written down.
 *
 * <h2>Why the field is volatile</h2>
 *
 * <p>All access happens on the logical server thread, so there is no contention. But the
 * server thread is not one thread for the life of the process: stopping an integrated server
 * terminates its thread, and loading the next world starts a <i>new</i> {@code Thread}. A
 * value written by the old server thread must therefore become visible to a different thread
 * that starts later.
 *
 * <p>A plain static field gives no such guarantee. Whether an ordering edge happens to exist
 * depends on vanilla's own shutdown and restart sequence, which is not this mod's code and is
 * not something to rely on silently. {@code volatile} makes the handoff correct by
 * construction, and with no contention it costs nothing worth measuring. Given that ARC-001
 * rests entirely on this field surviving exactly that transition, the guarantee should come
 * from the declaration rather than from an assumption about another codebase.
 */
public final class ToolkitState {

    private static volatile DiagnosticSession activeSession;

    private ToolkitState() {
    }

    /** @return the active session, or null when none is active. */
    public static DiagnosticSession getActiveSession() {
        return activeSession;
    }

    public static boolean hasActiveSession() {
        return activeSession != null;
    }

    /**
     * Replaces the active session, or clears it when given null.
     *
     * <p>Intended for {@code SessionManager}, which owns the lifecycle rules. Public only
     * because that class lives in a different package; treat it as internal.
     */
    public static void setActiveSession(DiagnosticSession session) {
        activeSession = session;
    }

    /**
     * Clears every piece of process-scoped state.
     *
     * <p>Exists for tests only. Production code must never call this: doing so on server stop
     * is exactly the mistake ARC-001 exists to prevent.
     */
    public static void resetForTesting() {
        activeSession = null;
    }
}
