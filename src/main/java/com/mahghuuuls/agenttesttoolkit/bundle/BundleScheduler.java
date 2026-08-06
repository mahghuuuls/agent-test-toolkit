package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Advances active bundle executions once per server tick.
 *
 * <p><b>ARC-004.</b> Today every execution finishes in its first advance, because per-command
 * delays are IMP-014's. The scheduler exists anyway so that adding delays extends this rather
 * than replacing a loop written inside the command handler.
 *
 * <p>Server thread only. Both submission, which happens while a command runs, and ticking
 * occur there, so no synchronisation is needed and none is implied.
 */
public final class BundleScheduler {

    private final List<BundleExecution> active = new ArrayList<BundleExecution>();

    /**
     * Executions submitted while a tick is in progress.
     *
     * <p>A bundle command can be {@code devtool run}, which submits during {@link #tick}.
     * Nesting proper is IMP-014's, but nothing stops an owner writing that line today, and
     * adding to the list being iterated would throw inside the server tick. Buffered instead,
     * so the worst case is an ordinary one-tick delay rather than a crash.
     */
    private final List<BundleExecution> pending = new ArrayList<BundleExecution>();

    private boolean ticking;

    /**
     * The execution currently being advanced, or null outside a tick.
     *
     * <p>This is how a nested {@code devtool run} is recognised. The command travels the
     * ordinary path, through the command manager and the run subcommand, and by the time it
     * arrives nothing in the arguments says it came from a bundle. The scheduler is the only
     * component that knows, because it is the one that called dispatch.
     *
     * <p>Server thread only, and cleared in a finally block, so a command that throws cannot
     * leave a stale parent behind for the next unrelated invocation to inherit.
     */
    private BundleExecution current;

    /** @return the execution being advanced, or null if not inside a bundle's dispatch. */
    public BundleExecution getCurrentExecution() {
        return current;
    }

    public void submit(BundleExecution execution) {
        if (execution == null) {
            throw new IllegalArgumentException("execution must not be null");
        }
        (ticking ? pending : active).add(execution);
    }

    public void tick(BundleExecution.Listener listener) {
        if (active.isEmpty() && pending.isEmpty()) {
            return;
        }

        ticking = true;
        try {
            for (Iterator<BundleExecution> it = active.iterator(); it.hasNext(); ) {
                BundleExecution execution = it.next();
                execution.onTick();
                current = execution;
                try {
                    execution.advance(listener);
                } finally {
                    current = null;
                }
                if (execution.isFinished()) {
                    it.remove();
                }
            }
        } finally {
            ticking = false;
            active.addAll(pending);
            pending.clear();
        }
    }

    /**
     * Drops everything in flight.
     *
     * <p>ARC-002: server-bound transient state is discarded when the server stops. An execution
     * holds a sender from a world that is unloading, and the scheduler is registered
     * permanently, so keeping them would leak stale executions into the next world's first tick.
     */
    public List<BundleExecution> discardAll() {
        List<BundleExecution> discarded = new ArrayList<BundleExecution>(active);
        discarded.addAll(pending);
        active.clear();
        pending.clear();
        // Returned rather than silently dropped. A bundle that stops because the world is
        // shutting down looks identical in the log to one that finished, unless something
        // says otherwise, and REQ-110 forbids a failure with no report.
        return discarded;
    }

    public int activeCount() {
        return active.size() + pending.size();
    }
}
