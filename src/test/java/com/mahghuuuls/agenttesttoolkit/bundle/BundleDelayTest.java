package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Per-command tick delays.
 *
 * <p>A delay is measured from the <b>completion of the preceding command</b>, not from the
 * bundle's start. The difference only shows up with two delays in a row, and getting it wrong
 * produces a bundle that runs faster than written, which in a setup routine means acting before
 * the world has settled. That failure looks like a flaky test rather than a scheduling bug.
 *
 * <p>Driven through a scheduler so the tick accounting is exercised the way it runs, rather
 * than by calling advance directly.
 */
class BundleDelayTest {

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return RecordContext.Snapshot.NONE;
        }
    };

    /** Records the elapsed tick at which each command was dispatched. */
    private static final class TickRecorder implements CommandDispatcher {
        private final List<String> dispatched = new ArrayList<String>();
        private final List<Integer> atTick = new ArrayList<Integer>();
        private int tick;

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            atTick.add(tick);
            return CommandOutcome.success();
        }
    }

    private static final BundleExecution.Listener IGNORE = new BundleExecution.Listener() {
        @Override
        public void onCommandFailed(BundleExecution e, int i, String c, String d) {
        }

        @Override
        public void onFinished(BundleExecution e) {
        }
    };

    /** Builds a bundle from alternating command name and delay. */
    private static BundleDefinition bundle(Object... commandThenDelay) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (int i = 0; i < commandThenDelay.length; i += 2) {
            list.add(new BundleCommand((String) commandThenDelay[i],
                    ((Integer) commandThenDelay[i + 1]).intValue()));
        }
        return new BundleDefinition("delayed", null, true, list);
    }

    /** Runs the scheduler for a number of ticks, tracking elapsed time for the recorder. */
    private static TickRecorder run(BundleDefinition definition, int ticks) {
        TickRecorder recorder = new TickRecorder();
        BundleScheduler scheduler = new BundleScheduler();
        scheduler.submit(new BundleExecution(definition, recorder, CONTEXT));
        for (int t = 1; t <= ticks; t++) {
            recorder.tick = t;
            scheduler.tick(IGNORE);
        }
        return recorder;
    }

    @Test
    @DisplayName("undelayed commands all run on the first tick")
    void undelayedRunTogether() {
        TickRecorder r = run(bundle("a", 0, "b", 0, "c", 0), 3);
        assertEquals(Arrays.asList("a", "b", "c"), r.dispatched);
        assertEquals(Arrays.asList(1, 1, 1), r.atTick);
    }

    @Test
    @DisplayName("a delayed command waits, and the ones before it do not")
    void delayedCommandWaits() {
        TickRecorder r = run(bundle("a", 0, "b", 20), 30);
        assertEquals(Arrays.asList("a", "b"), r.dispatched);
        assertEquals(Integer.valueOf(1), r.atTick.get(0));
        assertEquals(Integer.valueOf(21), r.atTick.get(1), "20 ticks after its predecessor");
    }

    @Test
    @DisplayName("two consecutive delays accumulate rather than overlapping")
    void consecutiveDelaysAccumulate() {
        // Two commands each with delayTicks 20 put the second
        // roughly 40 ticks after the first command. Measuring both from the bundle's start
        // would run them together at tick 20 and silently halve the intended pacing.
        TickRecorder r = run(bundle("a", 0, "b", 20, "c", 20), 60);

        assertEquals(Arrays.asList("a", "b", "c"), r.dispatched);
        assertEquals(Integer.valueOf(1), r.atTick.get(0));
        assertEquals(Integer.valueOf(21), r.atTick.get(1));
        assertEquals(Integer.valueOf(41), r.atTick.get(2), "40 ticks after the first command");
    }

    @Test
    @DisplayName("a delay on the first command delays the whole bundle")
    void leadingDelayApplies() {
        TickRecorder r = run(bundle("a", 10), 20);
        assertEquals(Integer.valueOf(10), r.atTick.get(0));
    }

    @Test
    @DisplayName("the bundle stays active while waiting and does not report an end early")
    void doesNotFinishWhileWaiting() {
        // BUNDLE_END must follow the last delayed command. Falling through to
        // completion while a command is still pending would close the boundary early and make
        // the delayed command look like it happened outside the bundle that caused it.
        final int[] finished = {0};
        BundleExecution.Listener counting = new BundleExecution.Listener() {
            @Override
            public void onCommandFailed(BundleExecution e, int i, String c, String d) {
            }

            @Override
            public void onFinished(BundleExecution e) {
                finished[0]++;
            }
        };

        TickRecorder recorder = new TickRecorder();
        BundleScheduler scheduler = new BundleScheduler();
        scheduler.submit(new BundleExecution(bundle("a", 0, "b", 20), recorder, CONTEXT));

        for (int t = 1; t <= 10; t++) {
            recorder.tick = t;
            scheduler.tick(counting);
        }
        assertEquals(0, finished[0], "must not finish while a command is still pending");
        assertEquals(1, scheduler.activeCount(), "must remain scheduled");

        for (int t = 11; t <= 25; t++) {
            recorder.tick = t;
            scheduler.tick(counting);
        }
        assertEquals(1, finished[0], "and must finish exactly once afterwards");
        assertEquals(0, scheduler.activeCount());
        assertEquals(2, recorder.dispatched.size());
    }

    @Test
    @DisplayName("stopping on failure abandons pending delayed commands")
    void stopOnFailureCancelsPending() {
        // A failure part-way through must not leave a delayed command to fire later against a
        // world the bundle has already given up on.
        CommandDispatcher failing = new CommandDispatcher() {
            private int calls;

            @Override
            public CommandOutcome dispatch(String command) {
                calls++;
                return calls == 1
                        ? CommandOutcome.failure("boom") : CommandOutcome.success();
            }
        };
        BundleScheduler scheduler = new BundleScheduler();
        BundleExecution execution =
                new BundleExecution(bundle("a", 0, "b", 20), failing, CONTEXT);
        scheduler.submit(execution);

        for (int t = 1; t <= 40; t++) {
            scheduler.tick(IGNORE);
        }

        assertTrue(execution.isFinished());
        assertTrue(execution.isStoppedEarly());
        assertEquals(1, execution.getExecuted(), "the delayed command must never run");
        assertFalse(execution.isSenderLost());
    }

    @Test
    @DisplayName("duration covers the whole span, not just the last tick of work")
    void durationSpansDelays() {
        TickRecorder recorder = new TickRecorder();
        BundleScheduler scheduler = new BundleScheduler();
        BundleExecution execution =
                new BundleExecution(bundle("a", 0, "b", 20), recorder, CONTEXT);
        scheduler.submit(execution);
        for (int t = 1; t <= 30; t++) {
            recorder.tick = t;
            scheduler.tick(IGNORE);
        }
        assertEquals(21, execution.getDurationTicks());
    }
}
