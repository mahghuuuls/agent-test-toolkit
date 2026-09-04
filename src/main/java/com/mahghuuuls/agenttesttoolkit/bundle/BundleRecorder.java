package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;

/**
 * Writes the execution boundary, step, wait and failure records.
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
        record.add("hash", execution.getHash());
        record.add("commands", execution.getTotal());
        ToolkitLog.write(record);
    }

    @Override
    public void onCommandStarting(BundleExecution execution, int index, String command,
                                  int dueTick, int elapsedTicks) {
        if (!execution.isTraced()) {
            return;
        }
        // Both ticks are counted from submission, the same clock durationTicks uses, so a
        // reader can subtract them. The stamped worldTick gives the absolute moment. When
        // elapsedTicks exceeds dueTick the scheduler ran late, which is itself worth seeing.
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BUNDLE_STEP), execution.getContext());
        SessionStamp.apply(record);
        record.add("bundle", execution.getBundleName());
        record.add("hash", execution.getHash());
        record.add("index", index);
        record.add("command", command);
        record.add("dueTick", dueTick);
        record.add("elapsedTicks", elapsedTicks);
        ToolkitLog.write(record);
    }

    @Override
    public void onWaitEnded(BundleExecution execution, int index, String command,
                            WaitCondition condition, BundleExecution.WaitOutcome outcome,
                            String reason, int waitedTicks, int timeoutTicks) {
        recordAwait(execution.getContext(), execution.getBundleName(), execution.getHash(),
                index, command, condition, outcomeName(outcome), reason, waitedTicks,
                timeoutTicks);
    }

    /**
     * Writes the record that ends a wait, inside or outside a bundle.
     *
     * <p>Shared with the wait commands so a wait typed by the operator, which has no
     * execution to park, produces the same record shape as one inside a bundle. The bundle
     * fields are simply absent in that case rather than filled with a placeholder.
     *
     * @param outcome  {@code satisfied}, {@code timedOut}, {@code cancelled}, or
     *                 {@code pending} for a single check outside a bundle that found nothing
     *                 ready
     * @param reason   null unless cancelled
     */
    public static void recordAwait(RecordContext.Snapshot context, String bundle, String hash,
                                   int index, String command, WaitCondition condition,
                                   String outcome, String reason, int waitedTicks,
                                   int timeoutTicks) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.AWAIT), context);
        SessionStamp.apply(record);
        if (bundle != null) {
            record.add("bundle", bundle);
            record.add("hash", hash);
            record.add("index", index);
        }
        record.add("command", command);
        record.add("condition", condition.kind());
        record.add("outcome", outcome);
        if (reason != null) {
            record.add("reason", reason);
        }
        record.add("waitedTicks", waitedTicks);
        record.add("timeoutTicks", timeoutTicks);
        condition.describe(record);
        ToolkitLog.write(record);
    }

    private static String outcomeName(BundleExecution.WaitOutcome outcome) {
        switch (outcome) {
            case SATISFIED:
                return "satisfied";
            case TIMED_OUT:
                return "timedOut";
            default:
                return "cancelled";
        }
    }

    @Override
    public void onCommandFailed(BundleExecution execution, int index, String command,
                                String detail) {
        // The bundle, the command text, and the position must all be identifiable from the
        // record alone. A bundle can contain the same command twice, so the index is not
        // redundant with the text.
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.ERROR), execution.getContext());
        SessionStamp.apply(record);
        record.add("message", "Bundle command failed");
        record.add("bundle", execution.getBundleName());
        record.add("hash", execution.getHash());
        record.add("index", index);
        record.add("command", command);
        record.add("detail", detail);
        ToolkitLog.write(record);
    }

    /**
     * Reports a bundle abandoned because the server stopped.
     *
     * <p>In-flight executions are discarded on shutdown. Doing so silently would leave a
     * {@code BUNDLE_START} with no matching end, which is exactly the unpaired boundary the
     * empty-bundle case was fixed to avoid, and an agent reading the log would be left
     * wondering whether the bundle hung.
     */
    public static void recordDiscarded(BundleExecution execution) {
        execution.abandonWait(new BundleRecorder());
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BUNDLE_END), execution.getContext());
        SessionStamp.apply(record);
        record.add("bundle", execution.getBundleName());
        record.add("hash", execution.getHash());
        record.add("executed", execution.getExecuted());
        record.add("failed", execution.getFailed());
        record.add("total", execution.getTotal());
        record.add("stoppedEarly", true);
        record.add("discardedOnServerStop", true);
        record.add("durationTicks", execution.getDurationTicks());
        ToolkitLog.write(record);
        ToolkitLog.error("Bundle abandoned, server stopping", execution.getBundleName());
    }

    @Override
    public void onFinished(BundleExecution execution) {
        LogRecord record = RecordContext.stamp(
                LogRecord.of(EventType.BUNDLE_END), execution.getContext());
        SessionStamp.apply(record);
        record.add("bundle", execution.getBundleName());
        record.add("hash", execution.getHash());
        // executed counts dispatches including failures; total is the bundle's full length, so
        // an agent can derive both successes and the number skipped by an early stop without
        // having to open the bundle file.
        record.add("executed", execution.getExecuted());
        record.add("failed", execution.getFailed());
        record.add("total", execution.getTotal());
        record.add("stoppedEarly", execution.isStoppedEarly());
        record.add("durationTicks", execution.getDurationTicks());
        if (execution.isSenderLost()) {
            // Stated, not inferred. A bundle that stops with no
            // failure record and no remaining commands would otherwise look like a clean run
            // that happened to be short.
            record.add("senderLost", true);
        }
        ToolkitLog.write(record);
        if (execution.isSenderLost()) {
            ToolkitLog.error("Bundle terminated, caller no longer available",
                    execution.getBundleName());
        }
    }
}
