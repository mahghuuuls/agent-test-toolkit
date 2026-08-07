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
 * A parent bundle waiting on a nested child.
 *
 * <p>The parent parks rather than firing the child and moving on. A child can contain its own
 * delays, so it cannot be run to completion inside the parent's dispatch; treating it as
 * fire-and-forget would let a parent report success while the routine it delegated to failed.
 *
 * <p><b>The fixture attaches the child from inside {@code dispatch}</b>, because that is when it
 * happens for real: a nested {@code devtool run} is an ordinary command, so the run subcommand
 * executes, and calls {@code awaitChild}, while the parent is mid-dispatch. An earlier version
 * of this test attached the child after a whole tick had elapsed, by which point a parent with
 * no delays had already run its entire list and finished. The test failed and the production
 * code was right, which is the useful direction for a test to be wrong in.
 */
class BundleNestingTest {

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return RecordContext.Snapshot.NONE;
        }
    };

    private static final BundleExecution.Listener IGNORE = new BundleExecution.Listener() {
        @Override
        public void onCommandFailed(BundleExecution e, int i, String c, String d) {
        }

        @Override
        public void onFinished(BundleExecution e) {
        }
    };

    /** Dispatcher that parks its parent on a child when it sees the nesting command. */
    private static final class NestingDispatcher implements CommandDispatcher {
        private final List<String> dispatched = new ArrayList<String>();
        private BundleExecution parent;
        private BundleExecution child;
        private String failing;

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            if ("run_child".equals(command) && child != null) {
                parent.awaitChild(child);
                child = null;
                return CommandOutcome.success();
            }
            return command.equals(failing)
                    ? CommandOutcome.failure("boom") : CommandOutcome.success();
        }
    }

    private static BundleDefinition bundle(String name, boolean stopOnFailure, String... cmds) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String c : cmds) {
            list.add(new BundleCommand(c, 0));
        }
        return new BundleDefinition(name, null, stopOnFailure, list);
    }

    /** Wires a parent that nests a child, ready to be ticked. */
    private static NestingDispatcher wire(BundleScheduler scheduler, boolean parentStops,
                                          BundleExecution childExec) {
        NestingDispatcher dispatcher = new NestingDispatcher();
        BundleExecution parent = new BundleExecution(
                bundle("parent", parentStops, "before", "run_child", "after"),
                dispatcher, CONTEXT);
        dispatcher.parent = parent;
        dispatcher.child = childExec;
        scheduler.submit(parent);
        return dispatcher;
    }

    @Test
    @DisplayName("a parent waits for its child, then finishes its own list")
    void parentWaitsThenResumes() {
        BundleScheduler scheduler = new BundleScheduler();
        NestingDispatcher childCommands = new NestingDispatcher();
        BundleExecution childExec = new BundleExecution(
                bundle("child", true, "c1", "c2"), childCommands, CONTEXT);
        NestingDispatcher parentCommands = wire(scheduler, true, childExec);

        scheduler.tick(IGNORE);
        assertEquals(Arrays.asList("before", "run_child"), parentCommands.dispatched,
                "the parent stops at the command that started the child");

        childExec.advance(IGNORE);
        scheduler.tick(IGNORE);

        assertEquals(Arrays.asList("before", "run_child", "after"), parentCommands.dispatched);
        assertEquals(Arrays.asList("c1", "c2"), childCommands.dispatched);
        assertEquals(0, parentCommands.parent.getFailed(), "a clean child adds no failures");
    }

    @Test
    @DisplayName("a parent does not advance while its child is unfinished")
    void parentBlockedWhileChildRuns() {
        BundleScheduler scheduler = new BundleScheduler();
        List<BundleCommand> slow = new ArrayList<BundleCommand>();
        slow.add(new BundleCommand("slow", 20));
        BundleExecution childExec = new BundleExecution(
                new BundleDefinition("child", null, true, slow), new NestingDispatcher(), CONTEXT);
        NestingDispatcher parentCommands = wire(scheduler, true, childExec);

        for (int i = 0; i < 6; i++) {
            scheduler.tick(IGNORE);
        }

        assertEquals(2, parentCommands.dispatched.size(),
                "the parent must not run past the command that started the child");
        assertFalse(parentCommands.parent.isFinished());
    }

    @Test
    @DisplayName("a failing child counts as one failed command in the parent")
    void childFailureCountsOnce() {
        // The whole point of parking. Fire-and-forget would let the parent report a clean run
        // while the routine it delegated to failed.
        BundleScheduler scheduler = new BundleScheduler();
        NestingDispatcher childCommands = new NestingDispatcher();
        childCommands.failing = "c1";
        BundleExecution childExec = new BundleExecution(
                bundle("child", false, "c1"), childCommands, CONTEXT);
        NestingDispatcher parentCommands = wire(scheduler, false, childExec);

        scheduler.tick(IGNORE);
        childExec.advance(IGNORE);
        scheduler.tick(IGNORE);

        assertEquals(1, parentCommands.parent.getFailed(),
                "the child contributes exactly one failure");
        assertTrue(parentCommands.dispatched.contains("after"),
                "stopOnFailure false means the parent carries on");
    }

    @Test
    @DisplayName("a failing child stops a parent that stops on failure")
    void childFailureStopsParent() {
        BundleScheduler scheduler = new BundleScheduler();
        NestingDispatcher childCommands = new NestingDispatcher();
        childCommands.failing = "c1";
        BundleExecution childExec = new BundleExecution(
                bundle("child", true, "c1"), childCommands, CONTEXT);
        NestingDispatcher parentCommands = wire(scheduler, true, childExec);

        scheduler.tick(IGNORE);
        childExec.advance(IGNORE);
        scheduler.tick(IGNORE);

        assertTrue(parentCommands.parent.isStoppedEarly());
        assertTrue(parentCommands.parent.isFinished());
        assertFalse(parentCommands.dispatched.contains("after"));
    }

    @Test
    @DisplayName("a child stopping on failure does not force the parent to stop")
    void childStopOnFailureIsIndependent() {
        // A parent with stopOnFailure false invoking a child with
        // stopOnFailure true means the child stops and the parent continues. Per bundle, never
        // inherited.
        BundleScheduler scheduler = new BundleScheduler();
        NestingDispatcher childCommands = new NestingDispatcher();
        childCommands.failing = "c1";
        BundleExecution childExec = new BundleExecution(
                bundle("child", true, "c1", "c2"), childCommands, CONTEXT);
        NestingDispatcher parentCommands = wire(scheduler, false, childExec);

        scheduler.tick(IGNORE);
        childExec.advance(IGNORE);

        assertTrue(childExec.isStoppedEarly(), "child honours its own stopOnFailure");
        assertFalse(childCommands.dispatched.contains("c2"));

        scheduler.tick(IGNORE);
        assertTrue(parentCommands.dispatched.contains("after"),
                "parent honours its own, and continues");
        assertFalse(parentCommands.parent.isStoppedEarly());
    }
}
