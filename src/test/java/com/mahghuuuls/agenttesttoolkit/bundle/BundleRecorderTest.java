package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The records bundle execution emits.
 *
 * <p>Record content is the toolkit's actual product: an agent parses these lines and cannot
 * tell a missing field from an event that did not happen. This project has already had a
 * regression where {@code side} went absent from two event types while the suite stayed green,
 * because nothing could observe a rendered record. These cases exist so that cannot recur for
 * the bundle boundaries.
 */
class BundleRecorderTest {

    @BeforeEach
    void startCapture() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.stopCaptureForTesting();
    }

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return new RecordContext.Snapshot(RecordContext.SERVER, 4321L);
        }
    };

    private static final class StubDispatcher implements CommandDispatcher {
        private final String failing;

        StubDispatcher(String failing) {
            this.failing = failing;
        }

        @Override
        public CommandOutcome dispatch(String command) {
            return command.equals(failing)
                    ? CommandOutcome.failure("no such command") : CommandOutcome.success();
        }
    }

    private static BundleExecution execution(boolean stopOnFailure, String failing,
                                             String... commands) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String command : commands) {
            list.add(new BundleCommand(command, 0));
        }
        BundleDefinition definition =
                new BundleDefinition("spell_setup", null, stopOnFailure, list);
        return new BundleExecution(definition, new StubDispatcher(failing), CONTEXT);
    }

    private static String recordOfType(String type) {
        for (String record : ToolkitLog.capturedForTesting()) {
            if (record.contains("[" + type + "]")) {
                return record;
            }
        }
        throw new AssertionError("no " + type + " record in "
                + ToolkitLog.capturedForTesting());
    }

    @Test
    @DisplayName("BUNDLE_START carries context, the bundle, and its length")
    void startRecordShape() {
        BundleRecorder.recordStart(execution(true, null, "one", "two"));

        String record = recordOfType("BUNDLE_START");
        assertTrue(record.contains("side=SERVER"), record);
        assertTrue(record.contains("worldTick=4321"), record);
        assertTrue(record.contains("bundle=spell_setup"), record);
        assertTrue(record.contains("commands=2"), record);
    }

    @Test
    @DisplayName("BUNDLE_END reports executed, failed, total, duration and the stop flag")
    void endRecordShape() {
        BundleExecution execution = execution(false, "bad", "one", "bad", "three");
        execution.advance(new BundleRecorder());

        String record = recordOfType("BUNDLE_END");
        assertTrue(record.contains("side=SERVER"), record);
        assertTrue(record.contains("bundle=spell_setup"), record);
        assertTrue(record.contains("executed=3"), record);
        assertTrue(record.contains("failed=1"), record);
        assertTrue(record.contains("total=3"), record);
        assertTrue(record.contains("stoppedEarly=false"), record);
        assertTrue(record.contains("durationTicks=0"), record);
    }

    @Test
    @DisplayName("an early stop is visible in the end record, not only in the counts")
    void endRecordShowsEarlyStop() {
        // executed=2 total=4 already implies it, but an agent should not have to infer the
        // reason two commands went unrun.
        BundleExecution execution = execution(true, "bad", "one", "bad", "three", "four");
        execution.advance(new BundleRecorder());

        String record = recordOfType("BUNDLE_END");
        assertTrue(record.contains("stoppedEarly=true"), record);
        assertTrue(record.contains("executed=2"), record);
        assertTrue(record.contains("total=4"), record);
    }

    @Test
    @DisplayName("a failure record identifies the bundle, the position, and the command")
    void failureRecordShape() {
        BundleExecution execution = execution(false, "bad", "one", "bad", "three");
        execution.advance(new BundleRecorder());

        String record = recordOfType("ERROR");
        assertTrue(record.contains("bundle=spell_setup"), record);
        assertTrue(record.contains("index=1"), record);
        assertTrue(record.contains("command=bad"), record);
        assertTrue(record.contains("detail=\"no such command\""), record);
    }

    @Test
    @DisplayName("a clean run emits exactly one start and one end, and no error")
    void cleanRunEmitsPairedBoundaries() {
        BundleExecution execution = execution(true, null, "one", "two");
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());

        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(2, records.size(), records.toString());
        assertTrue(records.get(0).contains("[BUNDLE_START]"), records.get(0));
        assertTrue(records.get(1).contains("[BUNDLE_END]"), records.get(1));
    }

    @Test
    @DisplayName("an empty bundle still produces a matched pair of boundaries")
    void emptyBundleStillPairs() {
        // Unpaired boundaries would break any agent that reads the log by scanning for the
        // region a bundle was responsible for.
        BundleExecution execution = execution(true, null);
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());

        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(2, records.size(), records.toString());
        assertTrue(records.get(1).contains("[BUNDLE_END]"), records.get(1));
        assertTrue(records.get(1).contains("executed=0"), records.get(1));
    }

    @Test
    @DisplayName("a command that succeeded without effect produces no error record")
    void noEffectProducesNoError() {
        BundleExecution execution = execution(true, null, "kill @e[name=absent]");
        execution.advance(new BundleRecorder());

        for (String record : ToolkitLog.capturedForTesting()) {
            assertFalse(record.contains("[ERROR]"), record);
        }
    }
}
