package com.mahghuuuls.agenttesttoolkit.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.ContextSource;
import com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderException;
import com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource;
import com.mahghuuuls.agenttesttoolkit.bundle.Placeholders;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxRequest;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An unbound placeholder's message names the step that is missing; its key does not change. */
class PlaceholderHintTest {

    @BeforeEach
    void reset() {
        ToolkitState.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
        ToolkitState.resetForTesting();
    }

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return new RecordContext.Snapshot(RecordContext.SERVER, 9L);
        }
    };

    /** A one-command bundle whose placeholders are filled from {@code source}, as the server's dispatcher does. */
    private static BundleExecution run(String command, final PlaceholderSource source) {
        BundleExecution execution = new BundleExecution(new BundleDefinition("hinted", null, true,
                Collections.singletonList(new BundleCommand(command, 0))), new CommandDispatcher() {
                    @Override
                    public CommandOutcome dispatch(String text) {
                        return CommandOutcome.success();
                    }

                    @Override
                    public String resolve(String text) throws PlaceholderException {
                        return Placeholders.substitute(text, source);
                    }
                }, CONTEXT);
        execution.advance(new BundleRecorder());
        return execution;
    }

    private static String errorRecord() {
        for (String record : ToolkitLog.capturedForTesting()) {
            if (record.contains("[ERROR]")) {
                return record;
            }
        }
        throw new AssertionError("no ERROR record in " + ToolkitLog.capturedForTesting());
    }

    private static JsonObject answer(BundleExecution execution, File dir) throws Exception {
        InboxDispatch.answerRun(InboxRequest.parse("h1", "run hinted"), execution, false, dir);
        return new JsonParser().parse(new String(Files.readAllBytes(new File(dir, "h1.result.json").toPath()),
                Charset.forName("UTF-8"))).getAsJsonObject();
    }

    @Test
    @DisplayName("an unbound placeholder's step reaches the failure record's detail and the inbox answer; the key stays")
    void hintInFailureDetail(@TempDir File dir) throws Exception {
        BundleExecution execution = run("gamemode creative ${operator.name}", new StatePlaceholderSource());

        String record = errorRecord();
        assertTrue(record.contains("detail=\"devtool.placeholder.unbound: run devtool session start <name> first\""),
                record);
        assertEquals(PlaceholderException.KEY_UNBOUND, execution.getFirstFailure(), "the key is unchanged");
        JsonObject doc = answer(execution, dir);
        assertEquals(PlaceholderException.KEY_UNBOUND, doc.get("firstFailure").getAsString());
        assertEquals("devtool.placeholder.unbound: run devtool session start <name> first",
                doc.get("detail").getAsString());
    }

    @Test
    @DisplayName("with a session, the failure detail names the claim of the handle")
    void claimHintInFailureDetail() {
        ToolkitState.setActiveSession(new DiagnosticSession("hints"));
        run("kill ${fixture.cow.uuid}", new StatePlaceholderSource());
        assertTrue(errorRecord().contains("run devtool fixture claim cow <selector|uuid|entityId> first"),
                errorRecord());
    }

    @Test
    @DisplayName("without a hint the failure detail is the key alone, and the inbox answer has no detail")
    void noHintDetail(@TempDir File dir) throws Exception {
        BundleExecution execution = run("say ${operator.name}", PlaceholderSource.EMPTY);
        assertTrue(errorRecord().contains("detail=devtool.placeholder.unbound"), errorRecord());
        assertFalse(errorRecord().contains("first"), errorRecord());
        assertFalse(answer(execution, dir).has("detail"));
    }

    private static PlaceholderException unbound(String command) {
        return assertThrows(PlaceholderException.class,
                () -> Placeholders.substitute(command, new StatePlaceholderSource()));
    }

    @Test
    @DisplayName("with no session, every placeholder names devtool session start")
    void noSession() {
        PlaceholderException operator = unbound("gamemode creative ${operator.name}");
        assertEquals(PlaceholderException.KEY_UNBOUND, operator.getKey());
        assertTrue(operator.getMessage().contains("devtool session start <name>"), operator.getMessage());
        PlaceholderException fixture = unbound("kill ${fixture.cow.uuid}");
        assertTrue(fixture.getMessage().contains("devtool session start <name>"), fixture.getMessage());
    }

    @Test
    @DisplayName("with a session and no operator, an operator placeholder names devtool session operator")
    void noOperator() {
        ToolkitState.setActiveSession(new DiagnosticSession("hints"));
        PlaceholderException e = unbound("tp ${operator.selector} 0 64 0");
        assertEquals(PlaceholderException.KEY_UNBOUND, e.getKey());
        assertTrue(e.getMessage().contains("devtool session operator <player>"), e.getMessage());
    }

    @Test
    @DisplayName("with a session, an unclaimed handle names devtool fixture claim with that handle")
    void unclaimedHandle() {
        ToolkitState.setActiveSession(new DiagnosticSession("hints"));
        ToolkitState.setOperator(new OperatorBinding(UUID.randomUUID(), "Dev"));
        PlaceholderException e = unbound("kill ${fixture.cow.uuid}");
        assertTrue(e.getMessage().contains("devtool fixture claim cow "), e.getMessage());
        assertEquals("Dev", new StatePlaceholderSource().operatorName());
    }

    @Test
    @DisplayName("a source without hints keeps the earlier message")
    void noHint() {
        assertNull(com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource.EMPTY.unboundHint("operator.name"));
        PlaceholderException e = assertThrows(PlaceholderException.class, () -> Placeholders.substitute(
                "say ${operator.name}", com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource.EMPTY));
        assertEquals("Placeholder ${operator.name} has no value: nothing is bound for it.", e.getMessage());
    }
}
