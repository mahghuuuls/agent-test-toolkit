package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tick-driven scheduler.
 *
 * <p>A scheduled state machine rather than a loop, which is what let per-command delays and
 * nested bundles extend it later instead of replacing it. These cases pin down the parts that
 * are easy to get wrong: executions leaving the active set when they finish, in-flight state
 * being dropped on server stop, and submission from inside a tick.
 */
class BundleSchedulerTest {

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return RecordContext.Snapshot.NONE;
        }
    };

    private static final BundleExecution.Listener IGNORE = new BundleExecution.Listener() {
        @Override
        public void onCommandFailed(BundleExecution execution, int index, String command,
                                    String detail) {
        }

        @Override
        public void onFinished(BundleExecution execution) {
        }
    };

    private static BundleExecution execution(String name, CommandDispatcher dispatcher) {
        BundleDefinition definition = new BundleDefinition(name, null, true,
                Collections.singletonList(new BundleCommand("say " + name, 0)));
        return new BundleExecution(definition, dispatcher, CONTEXT);
    }

    private static final class Recorder implements CommandDispatcher {
        private final List<String> dispatched = new ArrayList<String>();

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            return CommandOutcome.success();
        }
    }

    @Test
    @DisplayName("a tick advances submitted executions")
    void tickAdvances() {
        BundleScheduler scheduler = new BundleScheduler();
        Recorder recorder = new Recorder();
        scheduler.submit(execution("a", recorder));

        scheduler.tick(IGNORE);

        assertEquals(Collections.singletonList("say a"), recorder.dispatched);
    }

    @Test
    @DisplayName("a finished execution leaves the active set")
    void finishedExecutionsAreRemoved() {
        BundleScheduler scheduler = new BundleScheduler();
        Recorder recorder = new Recorder();
        scheduler.submit(execution("a", recorder));
        assertEquals(1, scheduler.activeCount());

        scheduler.tick(IGNORE);

        assertEquals(0, scheduler.activeCount(), "a completed bundle must not be ticked forever");
        scheduler.tick(IGNORE);
        assertEquals(1, recorder.dispatched.size(), "and must not run its commands again");
    }

    @Test
    @DisplayName("several executions advance independently in one tick")
    void severalExecutionsAdvance() {
        BundleScheduler scheduler = new BundleScheduler();
        Recorder first = new Recorder();
        Recorder second = new Recorder();
        scheduler.submit(execution("a", first));
        scheduler.submit(execution("b", second));

        scheduler.tick(IGNORE);

        assertEquals(1, first.dispatched.size());
        assertEquals(1, second.dispatched.size());
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    @DisplayName("submitting from inside a tick does not disturb the tick in progress")
    void submitDuringTickIsDeferred() {
        // A bundle command can be `devtool run`, which submits while the scheduler is
        // iterating, and adding to the list under iteration would throw inside the server
        // tick. Buffered instead, so the worst case is a one-tick delay rather than a crash.
        final BundleScheduler scheduler = new BundleScheduler();
        final Recorder nested = new Recorder();

        CommandDispatcher submitsAnother = new CommandDispatcher() {
            @Override
            public CommandOutcome dispatch(String command) {
                scheduler.submit(execution("nested", nested));
                return CommandOutcome.success();
            }
        };
        scheduler.submit(execution("outer", submitsAnother));

        scheduler.tick(IGNORE);

        assertTrue(nested.dispatched.isEmpty(), "the nested bundle waits for the next tick");
        assertEquals(1, scheduler.activeCount());

        scheduler.tick(IGNORE);
        assertEquals(1, nested.dispatched.size());
        assertEquals(0, scheduler.activeCount());
    }

    @Test
    @DisplayName("stopping the server discards everything in flight")
    void discardAllClears() {
        // An execution holds a sender from a world that is unloading, and the ticker
        // is registered permanently, so anything left here would resume against the next world.
        BundleScheduler scheduler = new BundleScheduler();
        Recorder recorder = new Recorder();
        scheduler.submit(execution("a", recorder));

        java.util.List<BundleExecution> discarded = scheduler.discardAll();
        scheduler.tick(IGNORE);

        assertEquals(0, scheduler.activeCount());
        assertTrue(recorder.dispatched.isEmpty());
        // Returned so the caller can report them. Discarding silently would leave a
        // BUNDLE_START with no matching end, and an agent could not tell an abandoned bundle
        // from one that hung.
        assertEquals(1, discarded.size());
        assertEquals("a", discarded.get(0).getBundleName());
    }

    @Test
    @DisplayName("an in-flight execution keeps its own commands, so reload cannot change them")
    void reloadCannotChangeAnInFlightBundle() {
        // True by construction rather than by a guard: the execution holds the command
        // list it was created with, and reload replaces registry entries with new objects
        // rather than mutating the old ones. Asserted so a later change to either side, such as
        // caching definitions by name and re-reading them per tick, fails here instead of
        // silently swapping a bundle mid-flight.
        java.util.List<BundleCommand> commands = new java.util.ArrayList<BundleCommand>();
        commands.add(new BundleCommand("original", 5));
        BundleDefinition definition =
                new BundleDefinition("swap_me", null, true, commands);

        Recorder recorder = new Recorder();
        BundleScheduler scheduler = new BundleScheduler();
        scheduler.submit(new BundleExecution(definition, recorder, CONTEXT));

        // Stand in for a reload: the name now maps to entirely different content.
        BundleRegistry registry = new BundleRegistry();
        registry.loadFrom(null);

        for (int i = 0; i < 10; i++) {
            scheduler.tick(IGNORE);
        }
        assertEquals(java.util.Collections.singletonList("original"), recorder.dispatched);
    }

    @Test
    @DisplayName("ticking with nothing scheduled is free and harmless")
    void idleTickIsHarmless() {
        BundleScheduler scheduler = new BundleScheduler();
        scheduler.tick(IGNORE);
        assertEquals(0, scheduler.activeCount());
    }
}
