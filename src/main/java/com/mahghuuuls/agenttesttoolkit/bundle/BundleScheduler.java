package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Advances active bundle executions once per server tick.
 *
 * <p>A scheduled state machine rather than a loop. An execution can span ticks: a command
 * carrying a delay parks its execution until the delay elapses, and a nested bundle parks its
 * parent until the child finishes. Both were added after this class existed, and both extended
 * it rather than replacing it, which is the reason it was written this way before either was
 * needed.
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
     * Adding to the list being iterated would throw inside the server tick, so submissions are
     * buffered here and picked up on the next pass. The worst case is an ordinary one-tick
     * delay rather than a crash.
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
     * <p>Server-bound transient state is discarded when the server stops. An execution
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
        // says otherwise, and this project does not let a failure go unreported.
        return discarded;
    }

    public int activeCount() {
        return active.size() + pending.size();
    }
}
