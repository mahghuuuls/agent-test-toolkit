package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.command.CommandOutcomes;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tolerated failure must leave a trace in the log.
 *
 * <p>Defect D1 of CYCLE-004: the documentation promised that a tolerated failure "is recorded
 * as successful, with a note naming the tolerated key", but the note was attached to the
 * outcome and then dropped, because the execution only reported failures. A bundle step whose
 * selector matched nothing therefore looked exactly like a step that ran and printed nothing.
 *
 * <p>Written against the rendered records rather than a listener method, so the same test ran
 * against the unchanged code first and failed there, which is the evidence that it reaches the
 * defect.
 */
class BundleToleratedTest {

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return new RecordContext.Snapshot(RecordContext.SERVER, 100L);
        }
    };

    /** Commands starting "nobody" fail as vanilla does when a selector matches no one. */
    private static final class VanillaLike implements CommandDispatcher {
        @Override
        public CommandOutcome dispatch(String command) {
            return dispatch(command, Collections.<String>emptySet());
        }

        @Override
        public CommandOutcome dispatch(String command, Collection<String> tolerated) {
            if (command.startsWith("nobody")) {
                return CommandOutcomes.classify(0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND, tolerated);
            }
            if (command.startsWith("effect clear")) {
                return CommandOutcomes.classify(0, "commands.effect.failure.notActive.all", tolerated);
            }
            if (command.startsWith("handled")) {
                return CommandOutcome.success("handled by another mod");
            }
            if (command.startsWith("broken")) {
                return CommandOutcomes.classify(0, "commands.generic.syntax", tolerated);
            }
            if (command.startsWith("mark")) {
                ToolkitLog.write(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                        com.mahghuuuls.agenttesttoolkit.logging.EventType.MARK).add("label", "after"));
            }
            return CommandOutcome.success();
        }
    }

    @BeforeEach
    void startCapture() {
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.stopCaptureForTesting();
    }

    private static void run(BundleCommand... commands) {
        BundleDefinition definition =
                new BundleDefinition("tolerance", null, true, Arrays.asList(commands));
        BundleExecution execution = new BundleExecution(definition, new VanillaLike(), CONTEXT);
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());
    }

    private static List<String> recordsOfType(String type) {
        List<String> found = new ArrayList<String>();
        for (String record : ToolkitLog.capturedForTesting()) {
            if (record.contains("[" + type + "]")) {
                found.add(record);
            }
        }
        return found;
    }

    private static String endRecord() {
        List<String> ends = recordsOfType("BUNDLE_END");
        assertEquals(1, ends.size(), ToolkitLog.capturedForTesting().toString());
        return ends.get(0);
    }

    @Test
    @DisplayName("A selector matching nobody writes TOLERATED with the key, and BUNDLE_END counts it")
    void builtInToleranceIsRecorded() {
        run(new BundleCommand("nobody kill", 0), new BundleCommand("mark", 0));

        List<String> tolerated = recordsOfType("TOLERATED");
        assertEquals(1, tolerated.size(), ToolkitLog.capturedForTesting().toString());
        assertTrue(tolerated.get(0).contains("key=" + CommandOutcomes.KEY_SELECTOR_NOT_FOUND),
                tolerated.get(0));
        assertTrue(tolerated.get(0).contains("index=0"), tolerated.get(0));
        assertTrue(tolerated.get(0).contains("bundle=tolerance"), tolerated.get(0));
        String end = endRecord();
        assertTrue(end.contains("executed=2"), end);
        assertTrue(end.contains("failed=0"), end);
        assertTrue(end.contains("tolerated=1"), end);

        // The tolerated step is visible before the mark that follows it.
        List<String> all = ToolkitLog.capturedForTesting();
        int toleratedAt = all.indexOf(tolerated.get(0));
        int markAt = all.indexOf(recordsOfType("MARK").get(0));
        assertTrue(toleratedAt >= 0 && toleratedAt < markAt, all.toString());
    }

    @Test
    @DisplayName("A genuine failure is a failure, not a tolerated step")
    void genuineFailureIsNotTolerated() {
        run(new BundleCommand("broken", 0), new BundleCommand("mark", 0));

        assertTrue(recordsOfType("TOLERATED").isEmpty(), ToolkitLog.capturedForTesting().toString());
        String end = endRecord();
        assertTrue(end.contains("failed=1"), end);
        assertTrue(end.contains("tolerated=0"), end);
    }

    @Test
    @DisplayName("A tolerated outcome without a key is refused")
    void toleratedNeedsKey() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> CommandOutcome.tolerated(null, "note"));
    }

    @Test
    @DisplayName("A declared tolerated key is named on its record")
    void declaredToleranceIsRecorded() {
        run(new BundleCommand("effect clear", 0,
                Collections.singletonList("commands.effect.failure.notActive.all")));

        List<String> tolerated = recordsOfType("TOLERATED");
        assertEquals(1, tolerated.size(), ToolkitLog.capturedForTesting().toString());
        assertTrue(tolerated.get(0).contains("key=commands.effect.failure.notActive.all"),
                tolerated.get(0));
    }

    @Test
    @DisplayName("A bundle with nothing tolerated writes no TOLERATED and counts zero")
    void nothingToleratedNothingRecorded() {
        run(new BundleCommand("mark", 0));

        assertTrue(recordsOfType("TOLERATED").isEmpty(), ToolkitLog.capturedForTesting().toString());
        assertTrue(endRecord().contains("tolerated=0"), endRecord());
    }

    @Test
    @DisplayName("A tolerated placeholder failure is recorded with its key")
    void toleratedPlaceholderIsRecorded() {
        CommandDispatcher unbound = new CommandDispatcher() {
            @Override
            public CommandOutcome dispatch(String command) {
                return CommandOutcome.success();
            }

            @Override
            public String resolve(String command) throws PlaceholderException {
                if (command.contains("${operator.name}")) {
                    throw new PlaceholderException(PlaceholderException.KEY_UNBOUND,
                            "${operator.name}", "no operator bound");
                }
                return command;
            }
        };
        BundleDefinition definition = new BundleDefinition("tolerance", null, true,
                Arrays.asList(new BundleCommand("gamemode creative ${operator.name}", 0,
                        Collections.singletonList(PlaceholderException.KEY_UNBOUND))));
        BundleExecution execution = new BundleExecution(definition, unbound, CONTEXT);
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());

        List<String> tolerated = recordsOfType("TOLERATED");
        assertEquals(1, tolerated.size(), ToolkitLog.capturedForTesting().toString());
        assertTrue(tolerated.get(0).contains("key=" + PlaceholderException.KEY_UNBOUND), tolerated.get(0));
        assertTrue(endRecord().contains("tolerated=1"), endRecord());
    }

    @Test
    @DisplayName("The classifier marks exactly the tolerated outcomes")
    void classifierMarksTolerance() {
        assertEquals(CommandOutcomes.KEY_SELECTOR_NOT_FOUND,
                CommandOutcomes.classify(0, CommandOutcomes.KEY_SELECTOR_NOT_FOUND).getToleratedKey());
        assertEquals(CommandOutcomes.KEY_CLEAR_NOTHING,
                CommandOutcomes.classify(0, CommandOutcomes.KEY_CLEAR_NOTHING).getToleratedKey());
        assertEquals("x.y", CommandOutcomes.classify(0, "x.y",
                Collections.singletonList("x.y")).getToleratedKey());
        assertEquals(null, CommandOutcomes.classify(1, null).getToleratedKey());
        assertEquals(null, CommandOutcomes.classify(0, "x.y").getToleratedKey());
        assertEquals(null, CommandOutcome.success("handled by another mod").getToleratedKey());
    }

    @Test
    @DisplayName("A command another mod handled is a success, not a tolerated failure")
    void handledIsNotTolerated() {
        run(new BundleCommand("handled", 0));

        assertTrue(recordsOfType("TOLERATED").isEmpty(), ToolkitLog.capturedForTesting().toString());
        assertTrue(endRecord().contains("tolerated=0"), endRecord());
    }
}
