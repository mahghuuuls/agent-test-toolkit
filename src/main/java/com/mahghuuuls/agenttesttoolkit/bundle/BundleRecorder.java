package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;

/**
 * Writes the execution boundary and failure records.
 *
 * <p>Separated from {@link BundleExecution} so the state machine holds no opinion about
 * logging, and so every record shape this feature emits sits in one readable place.
 *
 * <p>The boundaries are the reason bundles are worth logging at all. An agent reading
 * {@code latest.log} needs to know which observed events happened because a bundle ran and
 * which came from the manual test that followed; without a start and an end, setup effects and
 * test effects are indistinguishable.
 */
public final class BundleRecorder implements BundleExecution.Listener {

    /** Written when the run is accepted, before the first command dispatches. */
    public static void recordStart(BundleExecution execution) {
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BUNDLE_START), execution.getContext());
        SessionStamp.apply(record);
        record.add("bundle", execution.getBundleName());
        record.add("commands", execution.getTotal());
        ToolkitLog.write(record);
    }

    @Override
    public void onCommandFailed(BundleExecution execution, int index, String command,
                                String detail) {
        // REQ-110 and the acceptance criteria: the bundle, the command text, and the position
        // must all be identifiable. A bundle can contain the same command twice, so the index
        // is not redundant with the text.
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ERROR), execution.getContext());
        SessionStamp.apply(record);
        record.add("message", "Bundle command failed");
        record.add("bundle", execution.getBundleName());
        record.add("index", index);
        record.add("command", command);
        record.add("detail", detail);
        ToolkitLog.write(record);
    }

    @Override
    public void onFinished(BundleExecution execution) {
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BUNDLE_END), execution.getContext());
        SessionStamp.apply(record);
        record.add("bundle", execution.getBundleName());
        // executed counts dispatches including failures; total is the bundle's full length, so
        // an agent can derive both successes and the number skipped by an early stop without
        // having to open the bundle file.
        record.add("executed", execution.getExecuted());
        record.add("failed", execution.getFailed());
        record.add("total", execution.getTotal());
        record.add("stoppedEarly", execution.isStoppedEarly());
        record.add("durationTicks", execution.getDurationTicks());
        ToolkitLog.write(record);
    }
}
