package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bundle parked on a condition.
 *
 * <p>The rules that matter: a satisfied wait continues the bundle; a wait that never
 * resolves ends as a timeout, which is a failure the stop rule sees; a declared key
 * tolerates it; a cancellation is a different key; and a delay after a wait counts from the
 * end of the wait. All of these are invisible from inside a game except as the absence of
 * something, which is why they are pinned here with a fake condition and a fake clock.
 */
class BundleWaitTest {

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
            return new RecordContext.Snapshot(RecordContext.SERVER, 500L);
        }
    };

    /** Satisfied after a fixed number of polls, or never, or cancelled on a given poll. */
    private static final class FakeCondition implements WaitCondition {
        private final int satisfiedOnPoll;
        private final int cancelledOnPoll;
        private int polls;

        FakeCondition(int satisfiedOnPoll, int cancelledOnPoll) {
            this.satisfiedOnPoll = satisfiedOnPoll;
            this.cancelledOnPoll = cancelledOnPoll;
        }

        static FakeCondition satisfiedAfter(int polls) {
            return new FakeCondition(polls, -1);
        }

        static FakeCondition never() {
            return new FakeCondition(-1, -1);
        }

        static FakeCondition cancelledOn(int poll) {
            return new FakeCondition(-1, poll);
        }

        @Override
        public Poll poll(int elapsedTicks) {
            polls++;
            if (polls == cancelledOnPoll) {
                return Poll.cancelled("dimensionChanged");
            }
            if (polls == satisfiedOnPoll) {
                return Poll.satisfied();
            }
            return Poll.pending();
        }

        @Override
        public String kind() {
            return "fake";
        }

        @Override
        public void describe(LogRecord record) {
            record.add("polls", polls);
        }
    }

    /**
     * Dispatches everything as a success, and when it sees the wait command, registers the
     * condition with the execution the way the real subcommand does.
     */
    private static class WaitingDispatcher implements CommandDispatcher {
        private final WaitCondition condition;
        private final int timeout;
        private final List<String> dispatched = new ArrayList<String>();
        private final List<String> reports = new ArrayList<String>();
        BundleExecution execution;

        WaitingDispatcher(WaitCondition condition, int timeout) {
            this.condition = condition;
            this.timeout = timeout;
        }

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            if (command.equals("wait")) {
                execution.await(condition, timeout);
            }
            return CommandOutcome.success();
        }

        @Override
        public void report(String message, boolean failure) {
            reports.add((failure ? "RED " : "") + message);
        }
    }

    private static BundleExecution run(WaitingDispatcher dispatcher, boolean stopOnFailure,
                                       List<String> toleratedOnWait, int delayAfterWait,
                                       BundleRecorder recorder) {
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("before", 0));
        commands.add(new BundleCommand("wait", 0, toleratedOnWait));
        commands.add(new BundleCommand("after", delayAfterWait));
        BundleDefinition definition =
                new BundleDefinition("waiting_bundle", null, stopOnFailure, commands);
        BundleExecution execution = new BundleExecution(definition, dispatcher, CONTEXT);
        dispatcher.execution = execution;
        int safety = 0;
        while (!execution.isFinished() && safety++ < 500) {
            execution.onTick();
            execution.advance(recorder);
        }
        assertTrue(execution.isFinished(), "execution did not finish");
        return execution;
    }

    private static List<String> records(String eventType) {
        List<String> out = new ArrayList<String>();
        for (String line : ToolkitLog.capturedForTesting()) {
            if (line.contains("[" + eventType + "]")) {
                out.add(line);
            }
        }
        return out;
    }

    @Test
    @DisplayName("a satisfied wait continues the bundle and records satisfied")
    void satisfiedWaitContinues() {
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.satisfiedAfter(3), 100);
        BundleExecution execution =
                run(dispatcher, true, Collections.<String>emptyList(), 0, new BundleRecorder());

        assertEquals(Arrays.asList("before", "wait", "after"), dispatcher.dispatched);
        assertEquals(0, execution.getFailed());
        assertFalse(execution.isStoppedEarly());
        List<String> awaits = records("AWAIT");
        assertEquals(1, awaits.size(), awaits.toString());
        assertTrue(awaits.get(0).contains("outcome=satisfied"), awaits.get(0));
        assertTrue(awaits.get(0).contains("condition=fake"), awaits.get(0));
        assertTrue(awaits.get(0).contains("index=1"), awaits.get(0));
        assertTrue(awaits.get(0).contains("polls=3"), awaits.get(0));
        assertTrue(awaits.get(0).contains("bundle=waiting_bundle"), awaits.get(0));
        assertEquals(1, dispatcher.reports.size(), dispatcher.reports.toString());
        assertTrue(dispatcher.reports.get(0).contains("satisfied after"), dispatcher.reports.get(0));
        assertFalse(dispatcher.reports.get(0).startsWith("RED "), dispatcher.reports.get(0));
    }

    @Test
    @DisplayName("a wait that never resolves times out, fails the bundle, and stops it")
    void timeoutIsAFailureThatStops() {
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.never(), 20);
        BundleExecution execution =
                run(dispatcher, true, Collections.<String>emptyList(), 0, new BundleRecorder());

        assertEquals(Arrays.asList("before", "wait"), dispatcher.dispatched);
        assertEquals(1, execution.getFailed());
        assertTrue(execution.isStoppedEarly());
        assertTrue(records("AWAIT").get(0).contains("outcome=timedOut"), records("AWAIT").toString());
        assertTrue(records("AWAIT").get(0).contains("waitedTicks=20"), records("AWAIT").toString());
        List<String> errors = records("ERROR");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("detail=" + BundleExecution.KEY_WAIT_TIMEOUT), errors.get(0));
        assertTrue(errors.get(0).contains("index=1"), errors.get(0));
        assertTrue(records("BUNDLE_END").get(0).contains("stoppedEarly=true"));
        // The caller is told, in red, because no command was running when the wait ended.
        assertEquals(1, dispatcher.reports.size(), dispatcher.reports.toString());
        assertTrue(dispatcher.reports.get(0).startsWith("RED "), dispatcher.reports.get(0));
        assertTrue(dispatcher.reports.get(0).contains("timed out after 20 ticks"), dispatcher.reports.get(0));
    }

    @Test
    @DisplayName("a declared timeout key tolerates the timeout and the bundle continues")
    void toleratedTimeoutContinues() {
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.never(), 20);
        BundleExecution execution = run(dispatcher, true,
                Collections.singletonList(BundleExecution.KEY_WAIT_TIMEOUT), 0, new BundleRecorder());

        assertEquals(Arrays.asList("before", "wait", "after"), dispatcher.dispatched);
        assertEquals(0, execution.getFailed());
        assertTrue(records("AWAIT").get(0).contains("outcome=timedOut"));
        assertTrue(records("ERROR").isEmpty(), records("ERROR").toString());
    }

    @Test
    @DisplayName("a declared timeout key does not tolerate a cancellation")
    void cancellationIsADifferentKey() {
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.cancelledOn(2), 100);
        BundleExecution execution = run(dispatcher, true,
                Collections.singletonList(BundleExecution.KEY_WAIT_TIMEOUT), 0, new BundleRecorder());

        assertEquals(1, execution.getFailed());
        assertTrue(execution.isStoppedEarly());
        String await = records("AWAIT").get(0);
        assertTrue(await.contains("outcome=cancelled"), await);
        assertTrue(await.contains("reason=dimensionChanged"), await);
        assertTrue(records("ERROR").get(0).contains("detail=" + BundleExecution.KEY_WAIT_CANCELLED));
    }

    @Test
    @DisplayName("without stopOnFailure a timed-out wait is counted and the bundle goes on")
    void timeoutWithoutStopContinues() {
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.never(), 5);
        BundleExecution execution =
                run(dispatcher, false, Collections.<String>emptyList(), 0, new BundleRecorder());

        assertEquals(Arrays.asList("before", "wait", "after"), dispatcher.dispatched);
        assertEquals(1, execution.getFailed());
        assertFalse(execution.isStoppedEarly());
    }

    @Test
    @DisplayName("a delay after a wait is measured from the end of the wait")
    void delayAfterWaitCountsFromWaitEnd() {
        // Satisfied on the third poll, which is the third tick after the wait command ran on
        // tick 1: ticks 2, 3, 4. Then a delay of 10 puts the next command at tick 14, not at
        // tick 11 as it would if the delay had been measured from the wait command's dispatch.
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.satisfiedAfter(3), 100);
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("wait", 0));
        commands.add(new BundleCommand("after", 10));
        BundleDefinition definition =
                new BundleDefinition("delayed_after_wait", null, true, true, commands);
        BundleExecution execution = new BundleExecution(definition, dispatcher, CONTEXT);
        dispatcher.execution = execution;
        BundleRecorder recorder = new BundleRecorder();
        while (!execution.isFinished()) {
            execution.onTick();
            execution.advance(recorder);
        }
        List<String> steps = records("BUNDLE_STEP");
        assertEquals(2, steps.size(), steps.toString());
        assertTrue(steps.get(1).contains("command=after"), steps.get(1));
        assertTrue(steps.get(1).contains("elapsedTicks=14"), steps.get(1));
    }

    @Test
    @DisplayName("registering a wait needs a positive timeout and refuses a second wait")
    void awaitGuards() {
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("wait", 0));
        BundleExecution execution = new BundleExecution(
                new BundleDefinition("guarded", null, true, commands),
                new CommandDispatcher() {
                    @Override
                    public CommandOutcome dispatch(String command) {
                        return CommandOutcome.success();
                    }
                }, CONTEXT);
        assertThrows(IllegalArgumentException.class,
                () -> execution.await(FakeCondition.never(), 0));
        execution.await(FakeCondition.never(), 5);
        assertTrue(execution.isWaiting());
        assertThrows(IllegalStateException.class,
                () -> execution.await(FakeCondition.never(), 5));
    }

    @Test
    @DisplayName("a sender lost during a wait ends the wait as cancelled and the bundle as senderLost")
    void senderLostDuringWait() {
        final boolean[] available = {true};
        // Registers the wait as the real command would, then reports the sender gone.
        WaitingDispatcher dispatcher = new WaitingDispatcher(FakeCondition.never(), 100) {
            @Override
            public boolean isSenderAvailable() {
                return available[0];
            }
        };
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("wait", 0));
        commands.add(new BundleCommand("after", 0));
        BundleExecution execution = new BundleExecution(
                new BundleDefinition("lost", null, true, commands), dispatcher, CONTEXT);
        dispatcher.execution = execution;
        BundleRecorder recorder = new BundleRecorder();
        execution.onTick();
        execution.advance(recorder);
        assertTrue(execution.isWaiting());
        execution.onTick();
        execution.advance(recorder);
        assertTrue(execution.isWaiting());
        available[0] = false;
        execution.onTick();
        execution.advance(recorder);
        assertTrue(execution.isFinished());
        assertTrue(execution.isSenderLost());
        String await = records("AWAIT").get(0);
        assertTrue(await.contains("outcome=cancelled"), await);
        assertTrue(await.contains("reason=senderLost"), await);
        assertTrue(records("BUNDLE_END").get(0).contains("senderLost=true"));
    }
}
