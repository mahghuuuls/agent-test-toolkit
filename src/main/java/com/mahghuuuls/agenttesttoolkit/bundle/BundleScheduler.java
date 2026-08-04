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
                execution.advance(listener);
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
    public void discardAll() {
        active.clear();
        pending.clear();
    }

    public int activeCount() {
        return active.size() + pending.size();
    }
}
