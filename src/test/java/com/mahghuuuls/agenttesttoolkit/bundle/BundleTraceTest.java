package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step records for a traced bundle.
 *
 * <p>The delay semantics have been misread once by a careful reader: the delay runs before
 * its own command, and nothing in the log showed that. These cases pin two things: that a
 * traced bundle reports, per command, the tick it became due and the tick it ran on, and that
 * an untraced bundle reports nothing new, so every bundle written before tracing existed keeps
 * exactly its old footprint in the log.
 */
class BundleTraceTest {

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
            return new RecordContext.Snapshot(RecordContext.SERVER, 100L);
        }
    };

    private static final CommandDispatcher ALWAYS_SUCCEEDS = new CommandDispatcher() {
        @Override
        public CommandOutcome dispatch(String command) {
            return CommandOutcome.success();
        }
    };

    private static BundleExecution execution(boolean traced) {
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("first", 0));
        commands.add(new BundleCommand("second", 20));
        commands.add(new BundleCommand("third", 0));
        BundleDefinition definition =
                new BundleDefinition("traced_bundle", null, true, traced, commands);
        return new BundleExecution(definition, ALWAYS_SUCCEEDS, CONTEXT);
    }

    private static void runToCompletion(BundleExecution execution, BundleRecorder recorder) {
        int safety = 0;
        while (!execution.isFinished() && safety++ < 100) {
            execution.onTick();
            execution.advance(recorder);
        }
        assertTrue(execution.isFinished(), "execution did not finish");
    }

    private static List<String> steps() {
        List<String> steps = new ArrayList<String>();
        for (String line : ToolkitLog.capturedForTesting()) {
            if (line.contains("[BUNDLE_STEP]")) {
                steps.add(line);
            }
        }
        return steps;
    }

    @Test
    @DisplayName("a traced bundle writes one step per command, in order, with due and elapsed ticks")
    void tracedBundleWritesSteps() {
        runToCompletion(execution(true), new BundleRecorder());
        List<String> steps = steps();
        assertEquals(3, steps.size(), steps.toString());

        assertTrue(steps.get(0).contains("index=0"), steps.get(0));
        assertTrue(steps.get(0).contains("command=first"), steps.get(0));
        assertTrue(steps.get(1).contains("index=1"), steps.get(1));
        assertTrue(steps.get(1).contains("command=second"), steps.get(1));
        assertTrue(steps.get(2).contains("index=2"), steps.get(2));

        // The first command runs on the first tick after submission. The second became due
        // twenty ticks after the first completed and ran on that tick. Both are visible.
        assertTrue(steps.get(0).contains("dueTick=0"), steps.get(0));
        assertTrue(steps.get(1).contains("dueTick=21"), steps.get(1));
        assertTrue(steps.get(1).contains("elapsedTicks=21"), steps.get(1));
        assertTrue(steps.get(2).contains("dueTick=21"), steps.get(2));

        for (String step : steps) {
            assertTrue(step.contains("bundle=traced_bundle"), step);
            assertTrue(step.contains("side=SERVER"), step);
            assertTrue(step.contains("worldTick=100"), step);
        }
    }

    @Test
    @DisplayName("an untraced bundle writes no step record at all")
    void untracedBundleIsSilent() {
        runToCompletion(execution(false), new BundleRecorder());
        assertTrue(steps().isEmpty(), steps().toString());
        // The boundaries are still there; only the steps are absent.
        assertFalse(ToolkitLog.capturedForTesting().isEmpty());
    }

    @Test
    @DisplayName("the parser accepts trace as a boolean and rejects anything else")
    void parserHandlesTrace() throws BundleParseException {
        Map<String, BundleDefinition> on = BundleParser.parse(
                "{\"t\": {\"trace\": true, \"commands\": [\"a\"]}}");
        assertTrue(on.get("t").isTraced());

        Map<String, BundleDefinition> off = BundleParser.parse(
                "{\"t\": {\"commands\": [\"a\"]}}");
        assertFalse(off.get("t").isTraced());

        BundleParseException e = assertThrows(BundleParseException.class,
                () -> BundleParser.parse("{\"t\": {\"trace\": \"yes\", \"commands\": [\"a\"]}}"));
        assertTrue(e.getMessage().contains("trace"), e.getMessage());
    }
}
