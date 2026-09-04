package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

/**
 * Something that receives every record written while one execution is advancing, and is
 * told when that execution ends.
 *
 * <p>This is how a result file stays a rendering of the log rather than a second account of
 * events: the sink sees the same {@code LogRecord} objects the single writer received, in the
 * same order, and nothing else. The scheduler makes the owning execution's sink the writer's
 * tap for exactly the span of that execution's advance, so a nested bundle's records reach the
 * nested bundle's sink and not its parent's.
 */
public interface ExecutionSink extends ToolkitLog.Tap {

    /** Called once, after the execution's end record has been written. */
    void finished(BundleExecution execution);
}
