package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;

/**
 * Hands a completion listener to the next top-level bundle run started on this thread.
 *
 * <p>The inbox runs {@code devtool run <bundle>} through the ordinary command path, so it does
 * not see the execution that command creates. It offers a listener here for the span of the
 * request; {@code RunSubCommand} takes it when it submits a top-level run. Server thread only,
 * like {@link RunChannel}.
 */
public final class RunCompletion {

    private static BundleExecution.CompletionListener pending;

    private RunCompletion() {
    }

    /** Offers a listener to the next top-level run. Replaces any offer not yet taken. */
    static void offer(BundleExecution.CompletionListener listener) {
        pending = listener;
    }

    /** Takes the offered listener, leaving none; null when nothing was offered. */
    public static BundleExecution.CompletionListener take() {
        BundleExecution.CompletionListener taken = pending;
        pending = null;
        return taken;
    }

    /** True when an offer is still waiting, that is, no run took it. */
    static boolean isPending() {
        return pending != null;
    }
}
