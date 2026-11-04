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
import com.mahghuuuls.agenttesttoolkit.inbox.InboxRequest;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxResult;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxStatus;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Result files, the status file, and the completion hook a run result depends on. */
class InboxResultTest {

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return new RecordContext.Snapshot(RecordContext.SERVER, 7L);
        }
    };

    @BeforeEach
    void start() {
        ToolkitLog.startCaptureForTesting();
        RunCompletion.take();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
        RunCompletion.take();
    }

    private static JsonObject read(File file) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file.toPath()),
                Charset.forName("UTF-8"))).getAsJsonObject();
    }

    /** "bad" fails, everything else succeeds. */
    private static BundleExecution execution(String... commands) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String command : commands) {
            list.add(new BundleCommand(command, 0));
        }
        return new BundleExecution(new BundleDefinition("inbox_target", null, true, list),
                new CommandDispatcher() {
                    @Override
                    public CommandOutcome dispatch(String command) {
                        return "bad".equals(command)
                                ? CommandOutcome.failure("no such command") : CommandOutcome.success();
                    }
                }, CONTEXT);
    }

    @Test
    @DisplayName("a refused request's file names the outcome and reason, and no temporary file is left")
    void refusedFile(@TempDir File dir) throws Exception {
        InboxRequest request = InboxRequest.parse("a1", "gamemode creative");
        assertTrue(InboxResult.refused(request, request.getRefusal()).writeTo(dir));

        JsonObject doc = read(new File(dir, "a1.result.json"));
        assertEquals(1, doc.get("schemaVersion").getAsInt());
        assertEquals("a1", doc.get("id").getAsString());
        assertEquals("gamemode creative", doc.get("request").getAsString());
        assertEquals("refused", doc.get("outcome").getAsString());
        assertEquals("verbNotAllowed", doc.get("reason").getAsString());
        assertEquals(Arrays.asList("a1.result.json"), Arrays.asList(dir.list()));
    }

    @Test
    @DisplayName("a run that finished cleanly is done, with the bundle's counters")
    void runDone(@TempDir File dir) throws Exception {
        BundleExecution execution = execution("one", "two");
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());
        InboxDispatch.answerRun(InboxRequest.parse("r1", "run inbox_target"), execution, false, dir);

        JsonObject doc = read(new File(dir, "r1.result.json"));
        assertEquals("done", doc.get("outcome").getAsString());
        assertEquals("inbox_target", doc.get("bundle").getAsString());
        assertEquals(2, doc.get("executed").getAsInt());
        assertEquals(0, doc.get("failed").getAsInt());
        assertFalse(doc.has("reason"));
        assertEquals(0, doc.get("tolerated").getAsInt());
        assertFalse(doc.has("discardedOnServerStop"));
        assertFalse(doc.has("resultFile"), "the bundle wrote no result file");
    }

    @Test
    @DisplayName("a run whose bundle wrote a result file names it in the answer")
    void runNamesItsResultFile(@TempDir File dir) throws Exception {
        BundleExecution execution = execution("one");
        BundleRecorder.recordStart(execution);
        execution.advance(new BundleRecorder());
        execution.setResultFile("inbox_target-900.json");
        InboxDispatch.answerRun(InboxRequest.parse("r4", "run inbox_target"), execution, false, dir);

        assertEquals("inbox_target-900.json", read(new File(dir, "r4.result.json")).get("resultFile").getAsString());
    }

    @Test
    @DisplayName("a run that stopped on a failure is failed, with the failing detail")
    void runFailed(@TempDir File dir) throws Exception {
        BundleExecution execution = execution("one", "bad", "three");
        execution.advance(new BundleRecorder());
        InboxDispatch.answerRun(InboxRequest.parse("r2", "run inbox_target"), execution, false, dir);

        JsonObject doc = read(new File(dir, "r2.result.json"));
        assertEquals("failed", doc.get("outcome").getAsString());
        assertEquals("bundleFailed", doc.get("reason").getAsString());
        assertEquals(1, doc.get("failed").getAsInt());
        assertTrue(doc.get("stoppedEarly").getAsBoolean());
        assertEquals("no such command", doc.get("firstFailure").getAsString());
    }

    @Test
    @DisplayName("a run discarded at server stop is failed with serverStopped")
    void runDiscarded(@TempDir File dir) throws Exception {
        BundleExecution execution = execution("one");
        InboxDispatch.answerRun(InboxRequest.parse("r3", "run inbox_target"), execution, true, dir);

        JsonObject doc = read(new File(dir, "r3.result.json"));
        assertEquals("failed", doc.get("outcome").getAsString());
        assertEquals("serverStopped", doc.get("reason").getAsString());
        assertTrue(doc.get("discardedOnServerStop").getAsBoolean());
        assertTrue(doc.get("stoppedEarly").getAsBoolean(), "a discarded run stopped early, as BUNDLE_END says");
    }

    @Test
    @DisplayName("a completion listener is told once when the run finishes")
    void completionOnFinish() {
        final List<Boolean> calls = new ArrayList<Boolean>();
        BundleExecution execution = execution("one");
        execution.addCompletionListener(new BundleExecution.CompletionListener() {
            @Override
            public void completed(BundleExecution e, boolean discarded) {
                calls.add(discarded);
            }
        });
        execution.advance(new BundleRecorder());
        execution.notifyDiscarded();

        assertEquals(Arrays.asList(Boolean.FALSE), calls);
    }

    @Test
    @DisplayName("a completion listener is told when the run is discarded at server stop")
    void completionOnDiscard() {
        final List<Boolean> calls = new ArrayList<Boolean>();
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        list.add(new BundleCommand("one", 0));
        list.add(new BundleCommand("two", 100));
        BundleExecution execution = new BundleExecution(
                new BundleDefinition("long", null, true, list), new CommandDispatcher() {
                    @Override
                    public CommandOutcome dispatch(String command) {
                        return CommandOutcome.success();
                    }
                }, CONTEXT);
        execution.addCompletionListener(new BundleExecution.CompletionListener() {
            @Override
            public void completed(BundleExecution e, boolean discarded) {
                calls.add(discarded);
            }
        });
        execution.advance(new BundleRecorder());
        assertFalse(execution.isFinished());
        BundleRecorder.recordDiscarded(execution);

        assertEquals(Arrays.asList(Boolean.TRUE), calls);
    }

    @Test
    @DisplayName("an offer is taken once, and none is left after")
    void offerTakenOnce() {
        BundleExecution.CompletionListener listener = new BundleExecution.CompletionListener() {
            @Override
            public void completed(BundleExecution e, boolean discarded) {
            }
        };
        RunCompletion.offer(listener);
        assertTrue(RunCompletion.isPending());
        assertEquals(listener, RunCompletion.take());
        assertNull(RunCompletion.take());
        assertFalse(RunCompletion.isPending());
    }

    @Test
    @DisplayName("an offer no run took is withdrawn, and a taken one counts as started")
    void settleOffer() {
        BundleExecution.CompletionListener listener = new BundleExecution.CompletionListener() {
            @Override
            public void completed(BundleExecution e, boolean discarded) {
            }
        };
        RunCompletion.offer(listener);
        assertFalse(InboxDispatch.settleOffer(true), "a pending offer means no run started");
        assertFalse(RunCompletion.isPending(), "the offer is withdrawn");

        RunCompletion.offer(listener);
        RunCompletion.take();
        assertTrue(InboxDispatch.settleOffer(true), "a taken offer means the run was submitted");

        RunCompletion.offer(listener);
        assertFalse(InboxDispatch.settleOffer(false), "a verb other than run never started a run");
        assertTrue(RunCompletion.isPending(), "and leaves an offer it did not make alone");
    }

    @Test
    @DisplayName("the status file exists while open and is gone after delete")
    void statusFile(@TempDir File dir) throws Exception {
        InboxOpening opening = new InboxOpening(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Dev", 40L, 1000L);
        InboxStatus.write(dir, opening, "New World");

        JsonObject doc = read(new File(dir, InboxStatus.FILE_NAME));
        assertTrue(doc.get("open").getAsBoolean());
        assertEquals("Dev", doc.get("operator").getAsString());
        assertEquals("New World", doc.get("world").getAsString());
        assertEquals(40L, doc.get("openedWorldTick").getAsLong());

        InboxStatus.delete(dir);
        assertFalse(new File(dir, InboxStatus.FILE_NAME).exists());
    }
}
