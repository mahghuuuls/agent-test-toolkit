package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundle execution state machine.
 *
 * <p>Ordering, failure classification, the stop rule and counter accuracy are the rules that
 * matter here, and all four are invisible from inside the game: an agent reads counters in a
 * log and has no way to tell an accurate one from a plausible one. Exercised directly against a
 * stubbed dispatcher instead.
 */
class BundleExecutionTest {

    /** Records what was dispatched, and fails whichever commands a test names. */
    private static final class StubDispatcher implements CommandDispatcher {

        private final List<String> dispatched = new ArrayList<String>();
        private final Map<String, CommandOutcome> scripted = new HashMap<String, CommandOutcome>();

        void fail(String command, String detail) {
            scripted.put(command, CommandOutcome.failure(detail));
        }

        void succeedWithNote(String command, String note) {
            scripted.put(command, CommandOutcome.success(note));
        }

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            CommandOutcome outcome = scripted.get(command);
            return outcome == null ? CommandOutcome.success() : outcome;
        }
    }

    private static final class StubListener implements BundleExecution.Listener {

        private final List<String> failures = new ArrayList<String>();
        private int finishedCount;

        @Override
        public void onCommandFailed(BundleExecution execution, int index, String command,
                                    String detail) {
            failures.add(execution.getBundleName() + "|" + index + "|" + command + "|" + detail);
        }

        @Override
        public void onFinished(BundleExecution execution) {
            finishedCount++;
        }
    }

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return RecordContext.Snapshot.NONE;
        }
    };

    private static BundleDefinition bundle(boolean stopOnFailure, String... commands) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String command : commands) {
            list.add(new BundleCommand(command, 0));
        }
        return new BundleDefinition("test_bundle", null, stopOnFailure, list);
    }

    private static BundleExecution execution(StubDispatcher dispatcher, BundleDefinition bundle) {
        return new BundleExecution(bundle, dispatcher, CONTEXT);
    }

    // --- Ordering -------------------------------------------------------------------

    @Test
    @DisplayName("commands run in file order, identically on every run")
    void commandsRunInFileOrder() {
        BundleDefinition definition = bundle(true, "one", "two", "three", "four", "five");

        StubDispatcher first = new StubDispatcher();
        execution(first, definition).advance(new StubListener());
        StubDispatcher second = new StubDispatcher();
        execution(second, definition).advance(new StubListener());

        assertEquals(Arrays.asList("one", "two", "three", "four", "five"), first.dispatched);
        assertEquals(first.dispatched, second.dispatched,
                "two runs of one bundle must dispatch the same sequence");
    }

    // --- Failure classification -----------------------------------------------------

    @Test
    @DisplayName("a command that succeeds without changing anything is not a failure")
    void successWithNoEffectIsNotFailure() {
        // The rule that keeps teardown bundles re-runnable: a kill matching
        // nothing must not halt the bundle behind it.
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.succeedWithNote("kill @e[name=absent]", "selector matched nothing");
        StubListener listener = new StubListener();

        BundleExecution execution =
                execution(dispatcher, bundle(true, "kill @e[name=absent]", "say after"));
        execution.advance(listener);

        assertEquals(0, execution.getFailed());
        assertTrue(listener.failures.isEmpty());
        assertTrue(dispatcher.dispatched.contains("say after"),
                "a no-effect command must not stop the bundle");
    }

    @Test
    @DisplayName("a failure names the bundle, the command, and its position")
    void failureIdentifiesPosition() {
        // A bundle may contain the same command twice, so the index is not redundant with text.
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("boom", "no such command");
        StubListener listener = new StubListener();

        execution(dispatcher, bundle(false, "ok", "boom", "ok")).advance(listener);

        assertEquals(1, listener.failures.size());
        assertEquals("test_bundle|1|boom|no such command", listener.failures.get(0));
    }

    @Test
    @DisplayName("a dispatcher returning null is treated as success rather than crashing")
    void nullOutcomeTolerated() {
        // Defensive: a dispatcher returning null must not take down the server tick.
        CommandDispatcher nullDispatcher = new CommandDispatcher() {
            @Override
            public CommandOutcome dispatch(String command) {
                return null;
            }
        };
        BundleExecution execution =
                new BundleExecution(bundle(true, "x", "y"), nullDispatcher, CONTEXT);
        StubListener listener = new StubListener();

        execution.advance(listener);

        assertEquals(2, execution.getExecuted());
        assertEquals(0, execution.getFailed());
        assertTrue(listener.failures.isEmpty());
    }

    // --- Stop on failure ------------------------------------------------------------

    @Test
    @DisplayName("stopOnFailure halts the bundle after a failing command")
    void stopOnFailureHalts() {
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("two", "bad syntax");
        StubListener listener = new StubListener();

        BundleExecution execution =
                execution(dispatcher, bundle(true, "one", "two", "three", "four"));
        execution.advance(listener);

        assertEquals(Arrays.asList("one", "two"), dispatcher.dispatched,
                "three and four must not run");
        assertTrue(execution.isStoppedEarly());
        assertEquals(2, execution.getExecuted());
        assertEquals(1, execution.getFailed());
        assertEquals(4, execution.getTotal());
    }

    @Test
    @DisplayName("stopOnFailure disabled runs the remaining commands")
    void stopOnFailureDisabledContinues() {
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("two", "bad syntax");
        StubListener listener = new StubListener();

        BundleExecution execution =
                execution(dispatcher, bundle(false, "one", "two", "three", "four"));
        execution.advance(listener);

        assertEquals(Arrays.asList("one", "two", "three", "four"), dispatcher.dispatched);
        assertFalse(execution.isStoppedEarly());
        assertEquals(4, execution.getExecuted());
        assertEquals(1, execution.getFailed());
    }

    @Test
    @DisplayName("with stopOnFailure disabled every failure is reported, not just the first")
    void everyFailureReported() {
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("bad1", "one");
        dispatcher.fail("bad2", "two");
        StubListener listener = new StubListener();

        BundleExecution execution =
                execution(dispatcher, bundle(false, "bad1", "ok", "bad2"));
        execution.advance(listener);

        assertEquals(2, listener.failures.size());
        assertEquals(2, execution.getFailed());
        assertEquals(3, execution.getExecuted());
    }

    // --- Counters and completion ----------------------------------------------------

    @Test
    @DisplayName("executed counts attempts including failures, so successes are derivable")
    void executedIncludesFailures() {
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("bad", "reason");
        BundleExecution execution = execution(dispatcher, bundle(false, "a", "bad", "b"));
        execution.advance(new StubListener());

        assertEquals(3, execution.getExecuted());
        assertEquals(1, execution.getFailed());
        assertEquals(2, execution.getExecuted() - execution.getFailed());
    }

    @Test
    @DisplayName("an empty bundle still completes and reports")
    void emptyBundleCompletes() {
        // The parser allows an empty command list. Finishing it in the constructor would emit
        // a BUNDLE_START with no matching BUNDLE_END, leaving unpaired boundaries in the log.
        StubListener listener = new StubListener();
        BundleExecution execution = execution(new StubDispatcher(), bundle(true));

        execution.advance(listener);

        assertTrue(execution.isFinished());
        assertEquals(1, listener.finishedCount, "an empty bundle must still report its end");
        assertEquals(0, execution.getExecuted());
        assertEquals(0, execution.getTotal());
    }

    @Test
    @DisplayName("completion is reported exactly once, even if advanced again")
    void finishedReportedOnce() {
        StubDispatcher dispatcher = new StubDispatcher();
        StubListener listener = new StubListener();
        BundleExecution execution = execution(dispatcher, bundle(true, "one"));

        execution.advance(listener);
        execution.advance(listener);
        execution.advance(listener);

        assertEquals(1, listener.finishedCount);
        assertEquals(1, dispatcher.dispatched.size(), "commands must not run twice");
    }

    @Test
    @DisplayName("a bundle whose caller has gone stops without running anything")
    void senderLostStopsExecution() {
        // A bundle with delays spans ticks, so the player who started it can leave
        // between them. Running the remainder against nobody would apply half a setup routine
        // with no one to see it.
        CommandDispatcher gone = new CommandDispatcher() {
            @Override
            public CommandOutcome dispatch(String command) {
                throw new AssertionError("must not dispatch when the caller has gone");
            }

            @Override
            public boolean isSenderAvailable() {
                return false;
            }
        };
        StubListener listener = new StubListener();
        BundleExecution execution =
                new BundleExecution(bundle(true, "one", "two"), gone, CONTEXT);

        execution.advance(listener);

        assertTrue(execution.isSenderLost());
        assertTrue(execution.isStoppedEarly());
        assertTrue(execution.isFinished());
        assertEquals(0, execution.getExecuted());
        assertEquals(1, listener.finishedCount, "it must still report an end");
    }

    @Test
    @DisplayName("a lost sender is distinguishable from a failed command")
    void senderLostIsNotAFailure() {
        // Both leave the bundle unfinished, but "a command failed" and "the player left" call
        // for different responses, and an agent should not have to infer which from the
        // absence of a failure record.
        StubDispatcher dispatcher = new StubDispatcher();
        dispatcher.fail("boom", "no such command");
        StubListener listener = new StubListener();
        BundleExecution execution = execution(dispatcher, bundle(true, "boom"));

        execution.advance(listener);

        assertTrue(execution.isStoppedEarly());
        assertFalse(execution.isSenderLost());
        assertEquals(1, execution.getFailed());
    }

    @Test
    @DisplayName("the constructor rejects missing collaborators rather than failing later")
    void constructorValidates() {
        assertThrows(IllegalArgumentException.class,
                () -> new BundleExecution(null, new StubDispatcher(), CONTEXT));
        assertThrows(IllegalArgumentException.class,
                () -> new BundleExecution(bundle(true, "a"), null, CONTEXT));
        assertThrows(IllegalArgumentException.class,
                () -> new BundleExecution(bundle(true, "a"), new StubDispatcher(), null));
    }
}
