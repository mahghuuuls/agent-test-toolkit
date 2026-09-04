package com.mahghuuuls.agenttesttoolkit.bundle;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Placeholders as bundle execution sees them.
 *
 * <p>The dispatcher fills them in; the execution has to do the right thing when it cannot.
 * Pinned here: the text that ran is what the step record shows, an unfillable placeholder is
 * a failure with its key and nothing is dispatched, the key is tolerable, and the caller is
 * told.
 */
class BundlePlaceholderTest {

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
            return new RecordContext.Snapshot(RecordContext.SERVER, 7L);
        }
    };

    /** Substitutes through a source, records what was dispatched and reported. */
    private static final class SubstitutingDispatcher implements CommandDispatcher {
        private final PlaceholderSource source;
        final List<String> dispatched = new ArrayList<String>();
        final List<String> reports = new ArrayList<String>();

        SubstitutingDispatcher(PlaceholderSource source) {
            this.source = source;
        }

        @Override
        public String resolve(String command) throws PlaceholderException {
            return Placeholders.substitute(command, source);
        }

        @Override
        public CommandOutcome dispatch(String command) {
            dispatched.add(command);
            return CommandOutcome.success();
        }

        @Override
        public void report(String message, boolean failure) {
            reports.add(message);
        }
    }

    private static final PlaceholderSource BOUND = new PlaceholderSource() {
        @Override
        public String operatorName() {
            return "Developer";
        }

        @Override
        public String operatorUuid() {
            return "a01a21f5-c117-3c1f-b5d0-e055b927f733";
        }

        @Override
        public String operatorSelector() {
            return "@p[name=Developer]";
        }

        @Override
        public String fixtureUuid(String handle) {
            return null;
        }
    };

    private static BundleExecution run(SubstitutingDispatcher dispatcher, boolean stopOnFailure,
                                       List<String> toleratedOnSecond, boolean traced) {
        List<BundleCommand> commands = new ArrayList<BundleCommand>();
        commands.add(new BundleCommand("say first", 0));
        commands.add(new BundleCommand("gamemode creative ${operator.name}", 0, toleratedOnSecond));
        commands.add(new BundleCommand("say last", 0));
        BundleDefinition definition =
                new BundleDefinition("ph_bundle", null, stopOnFailure, traced, commands);
        BundleExecution execution = new BundleExecution(definition, dispatcher, CONTEXT);
        int safety = 0;
        while (!execution.isFinished() && safety++ < 50) {
            execution.onTick();
            execution.advance(new BundleRecorder());
        }
        assertTrue(execution.isFinished());
        return execution;
    }

    private static List<String> records(String type) {
        List<String> out = new ArrayList<String>();
        for (String line : ToolkitLog.capturedForTesting()) {
            if (line.contains("[" + type + "]")) {
                out.add(line);
            }
        }
        return out;
    }

    @Test
    @DisplayName("a bound placeholder is filled in, dispatched, and shown in the step record")
    void boundIsSubstituted() {
        SubstitutingDispatcher dispatcher = new SubstitutingDispatcher(BOUND);
        BundleExecution execution = run(dispatcher, true, Collections.<String>emptyList(), true);

        assertEquals(Arrays.asList("say first", "gamemode creative Developer", "say last"),
                dispatcher.dispatched);
        assertEquals(0, execution.getFailed());
        String step = records("BUNDLE_STEP").get(1);
        assertTrue(step.contains("command=\"gamemode creative Developer\""), step);
        assertFalse(step.contains("${"), step);
    }

    @Test
    @DisplayName("an unbound placeholder fails the command with its key, dispatches nothing, and stops")
    void unboundFailsAndStops() {
        SubstitutingDispatcher dispatcher = new SubstitutingDispatcher(PlaceholderSource.EMPTY);
        BundleExecution execution = run(dispatcher, true, Collections.<String>emptyList(), false);

        assertEquals(Collections.singletonList("say first"), dispatcher.dispatched);
        assertEquals(1, execution.getFailed());
        assertTrue(execution.isStoppedEarly());
        List<String> errors = records("ERROR");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("detail=" + PlaceholderException.KEY_UNBOUND), errors.get(0));
        assertTrue(errors.get(0).contains("index=1"), errors.get(0));
        assertEquals(1, dispatcher.reports.size(), dispatcher.reports.toString());
        assertTrue(dispatcher.reports.get(0).contains("${operator.name}"), dispatcher.reports.get(0));
    }

    @Test
    @DisplayName("the unbound key can be tolerated, and the bundle continues past the command")
    void unboundCanBeTolerated() {
        SubstitutingDispatcher dispatcher = new SubstitutingDispatcher(PlaceholderSource.EMPTY);
        BundleExecution execution = run(dispatcher, true,
                Collections.singletonList(PlaceholderException.KEY_UNBOUND), false);

        assertEquals(Arrays.asList("say first", "say last"), dispatcher.dispatched);
        assertEquals(0, execution.getFailed());
        assertTrue(records("ERROR").isEmpty());
    }

    @Test
    @DisplayName("without stopOnFailure the failure is counted and the bundle goes on")
    void unboundWithoutStop() {
        SubstitutingDispatcher dispatcher = new SubstitutingDispatcher(PlaceholderSource.EMPTY);
        BundleExecution execution = run(dispatcher, false, Collections.<String>emptyList(), false);

        assertEquals(Arrays.asList("say first", "say last"), dispatcher.dispatched);
        assertEquals(1, execution.getFailed());
        assertFalse(execution.isStoppedEarly());
    }
}
