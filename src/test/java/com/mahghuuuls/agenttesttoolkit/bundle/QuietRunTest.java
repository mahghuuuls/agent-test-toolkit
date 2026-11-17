package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A quiet run as the execution drives it: its commands run inside its quiet scope, a failure
 * still shows one line, and the boundary records say what was kept back.
 */
class QuietRunTest {

    /** Fails the commands it is told to, and keeps what it announced and reported. */
    private static final class Dispatcher implements CommandDispatcher {
        final List<String> failing = new ArrayList<String>();
        final List<String> announced = new ArrayList<String>();
        final List<Boolean> quietWhileRunning = new ArrayList<Boolean>();
        RuntimeException toThrow;

        @Override
        public CommandOutcome dispatch(String command) {
            quietWhileRunning.add(QuietScope.isQuiet());
            if (toThrow != null) {
                throw toThrow;
            }
            // A command that replies, as every toolkit subcommand does.
            QuietScope.suppress(1);
            return failing.contains(command) ? CommandOutcome.failure("commands.generic.notFound")
                    : CommandOutcome.success();
        }

        @Override
        public void announce(String line) {
            announced.add(line);
        }
    }

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return RecordContext.Snapshot.NONE;
        }
    };

    private static BundleExecution run(Dispatcher dispatcher, boolean quiet, boolean stopOnFailure,
                                       String... commands) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String command : commands) {
            list.add(new BundleCommand(command, 0));
        }
        BundleExecution execution = new BundleExecution(
                new BundleDefinition("teleports", null, stopOnFailure, list), dispatcher, CONTEXT);
        execution.setQuiet(quiet);
        return execution;
    }

    @AfterEach
    void cleanUp() {
        ToolkitLog.stopCaptureForTesting();
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    @Test
    @DisplayName("a quiet run's commands run inside its quiet scope, and the scope is left after")
    void commandsRunQuiet() {
        Dispatcher dispatcher = new Dispatcher();
        BundleExecution execution = run(dispatcher, true, true, "tp a", "tp b", "tp c");
        execution.advance(new BundleRecorder());
        assertEquals(Arrays.asList(true, true, true), dispatcher.quietWhileRunning);
        assertEquals(3, execution.getChatSuppressed());
        assertEquals(0, QuietScope.depth());
        assertEquals(new ArrayList<String>(), dispatcher.announced, "nothing failed, nothing shown");
    }

    @Test
    @DisplayName("a loud run counts nothing and shows no extra line for a failure")
    void loudRunUnchanged() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.failing.add("tp b");
        BundleExecution execution = run(dispatcher, false, true, "tp a", "tp b");
        execution.advance(new BundleRecorder());
        assertEquals(Arrays.asList(false, false), dispatcher.quietWhileRunning);
        assertEquals(0, execution.getChatSuppressed());
        assertEquals(new ArrayList<String>(), dispatcher.announced);
    }

    @Test
    @DisplayName("a quiet run's failure still shows one line naming the bundle, the index and the key")
    void failureLineKept() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.failing.add("tp c");
        BundleExecution execution = run(dispatcher, true, true, "tp a", "tp b", "tp c", "tp d");
        execution.advance(new BundleRecorder());
        assertEquals(Arrays.asList("[DevToolkit] Bundle 'teleports' command 2 failed: commands.generic.notFound"),
                dispatcher.announced);
        assertTrue(execution.isStoppedEarly());
    }

    /** Parks its parent on a child at {@code run_child}, as a nested run does; failures and lines as above. */
    private static final class NestingDispatcher implements CommandDispatcher {
        final List<String> failing = new ArrayList<String>();
        /** Shared by a parent and its child, so it holds every line the caller would see. */
        final List<String> announced;
        BundleExecution parent;
        BundleExecution child;

        NestingDispatcher(List<String> announced) {
            this.announced = announced;
        }

        @Override
        public CommandOutcome dispatch(String command) {
            if ("run_child".equals(command) && child != null) {
                parent.awaitChild(child);
                child = null;
                return CommandOutcome.success();
            }
            return failing.contains(command) ? CommandOutcome.failure("commands.generic.notFound")
                    : CommandOutcome.success();
        }

        @Override
        public void announce(String line) {
            announced.add(line);
        }
    }

    /** A quiet parent nesting a failing child of the given quietness; the lines the caller saw. */
    private static List<String> nestedFailure(boolean childQuiet) {
        List<String> announced = new ArrayList<String>();
        NestingDispatcher childCommands = new NestingDispatcher(announced);
        childCommands.failing.add("c1");
        BundleExecution child = new BundleExecution(new BundleDefinition("child", null, true,
                Arrays.asList(new BundleCommand("c1", 0))), childCommands, CONTEXT);
        child.setQuiet(childQuiet);

        NestingDispatcher parentCommands = new NestingDispatcher(announced);
        BundleExecution parent = new BundleExecution(new BundleDefinition("parent", null, true,
                Arrays.asList(new BundleCommand("before", 0), new BundleCommand("run_child", 0),
                        new BundleCommand("after", 0))), parentCommands, CONTEXT);
        parent.setQuiet(true);
        parentCommands.parent = parent;
        parentCommands.child = child;

        BundleScheduler scheduler = new BundleScheduler();
        scheduler.submit(parent);
        BundleExecution.Listener ignore = new BundleExecution.Listener() {
            @Override
            public void onCommandFailed(BundleExecution e, int i, String c, String d) {
            }

            @Override
            public void onFinished(BundleExecution e) {
            }
        };
        scheduler.tick(ignore);
        child.advance(ignore);
        scheduler.tick(ignore);
        assertTrue(parent.isStoppedEarly(), "the child's failure stopped the parent");
        return announced;
    }

    @Test
    @DisplayName("a quiet child's failure is shown once: by the child, not again by its quiet parent")
    void quietChildFailureOnce() {
        assertEquals(Arrays.asList("[DevToolkit] Bundle 'child' command 0 failed: commands.generic.notFound"),
                nestedFailure(true));
    }

    @Test
    @DisplayName("a loud child under a quiet parent: the parent shows the failure, since the child's red line was its own")
    void loudChildFailureShownByParent() {
        assertEquals(Arrays.asList("[DevToolkit] Bundle 'parent' command 1 failed: nested bundle reported failures"),
                nestedFailure(false));
    }

    @Test
    @DisplayName("a long failure detail is cut so the line stays short")
    void failureLineShort() {
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sentence.append("words ");
        }
        String line = BundleExecution.failureLine("b", 0, sentence.toString());
        assertTrue(line.length() < 200, line);
        assertTrue(line.endsWith("..."), line);
    }

    @Test
    @DisplayName("the scope is left even when a command escapes the dispatcher")
    void scopeLeftAfterThrow() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.toThrow = new IllegalStateException("escaped");
        BundleExecution execution = run(dispatcher, true, true, "tp a");
        assertThrows(IllegalStateException.class, () -> execution.advance(new BundleRecorder()));
        assertEquals(0, QuietScope.depth());
    }

    @Test
    @DisplayName("the boundary records carry quiet, and the end record the lines kept back")
    void recordsCarryQuiet() {
        ToolkitLog.startCaptureForTesting();
        Dispatcher dispatcher = new Dispatcher();
        BundleExecution execution = run(dispatcher, true, true, "tp a", "tp b");
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());
        List<String> records = ToolkitLog.capturedForTesting();
        String start = find(records, "BUNDLE_START");
        String end = find(records, "BUNDLE_END");
        assertTrue(start.contains(" quiet=true"), start);
        assertTrue(end.contains(" quiet=true") && end.contains(" chatSuppressed=2"), end);
    }

    @Test
    @DisplayName("a loud run's records say quiet=false and chatSuppressed=0")
    void loudRecords() {
        ToolkitLog.startCaptureForTesting();
        BundleExecution execution = run(new Dispatcher(), false, true, "tp a");
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());
        String end = find(ToolkitLog.capturedForTesting(), "BUNDLE_END");
        assertTrue(end.contains(" quiet=false") && end.contains(" chatSuppressed=0"), end);
    }

    @Test
    @DisplayName("a timeout record asks its condition for the timeout's own fields; other outcomes do not")
    void timeoutFields() {
        ToolkitLog.startCaptureForTesting();
        WaitCondition condition = new WaitCondition() {
            @Override
            public Poll poll(int elapsedTicks) {
                return Poll.pending();
            }

            @Override
            public String kind() {
                return "chunks";
            }

            @Override
            public void describe(com.mahghuuuls.agenttesttoolkit.logging.LogRecord record) {
            }

            @Override
            public void describeTimeout(com.mahghuuuls.agenttesttoolkit.logging.LogRecord record) {
                record.add("viewDistance", 10);
            }
        };
        BundleRecorder.recordAwait(RecordContext.Snapshot.NONE, null, null, -1, "devtool await chunks 0 0 1 1",
                condition, "timedOut", null, 200, 200);
        BundleRecorder.recordAwait(RecordContext.Snapshot.NONE, null, null, -1, "devtool await chunks 0 0 1 1",
                condition, "satisfied", null, 5, 200);
        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(2, records.size());
        assertTrue(records.get(0).contains(" viewDistance=10"), records.get(0));
        assertTrue(!records.get(1).contains("viewDistance"), records.get(1));
    }

    private static String find(List<String> records, String type) {
        for (String record : records) {
            if (record.contains(type)) {
                return record;
            }
        }
        return fail("no " + type + " in " + records);
    }
}
