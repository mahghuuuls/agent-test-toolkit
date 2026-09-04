package com.mahghuuuls.agenttesttoolkit.results;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleParser;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.ContextSource;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The result file.
 *
 * <p>Three properties: it is built from the records the log received and nothing else; a
 * nested bundle's records stay out of its parent's file; and a write that fails becomes an
 * error record without changing the bundle's outcome.
 */
class BundleResultTest {

    @BeforeEach
    void startCapture() {
        ToolkitLog.startCaptureForTesting();
        ToolkitLog.setTap(null);
    }

    @AfterEach
    void stopCapture() {
        ToolkitLog.setTap(null);
        ToolkitLog.stopCaptureForTesting();
    }

    private static final ContextSource CONTEXT = new ContextSource() {
        @Override
        public RecordContext.Snapshot snapshot() {
            return new RecordContext.Snapshot(RecordContext.SERVER, 900L);
        }
    };

    /** Writes an ASSERT record on "assert", fails "bad", succeeds otherwise. */
    private static final CommandDispatcher DISPATCHER = new CommandDispatcher() {
        @Override
        public CommandOutcome dispatch(String command) {
            if (command.startsWith("assert")) {
                ToolkitLog.write(LogRecord.of(EventType.ASSERT)
                        .add("predicate", "entities.count").add("expected", "1")
                        .add("observed", command.endsWith("fail") ? "2" : "1")
                        .add("outcome", command.endsWith("fail") ? "fail" : "pass"));
                return command.endsWith("fail")
                        ? CommandOutcome.failure("devtool.assert.failed") : CommandOutcome.success();
            }
            if (command.startsWith("mark")) {
                ToolkitLog.write(LogRecord.of(EventType.MARK).add("label", command.substring(5)));
            }
            return command.equals("bad") ? CommandOutcome.failure("no such command") : CommandOutcome.success();
        }
    };

    private static BundleExecution run(BundleDefinition definition, BundleResultCollector sink) {
        BundleExecution execution = new BundleExecution(definition, DISPATCHER, CONTEXT);
        if (sink != null) {
            execution.attachSink(sink);
        }
        BundleRecorder.recordStart(execution);
        BundleRecorder recorder = new BundleRecorder();
        while (!execution.isFinished()) {
            execution.onTick();
            // The scheduler sets the tap around each advance; reproduced here exactly.
            ToolkitLog.setTap(execution.getSink());
            try {
                execution.advance(recorder);
            } finally {
                ToolkitLog.setTap(null);
            }
        }
        return execution;
    }

    private static BundleDefinition bundle(String name, boolean stopOnFailure, String... commands) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        for (String command : commands) {
            list.add(new BundleCommand(command, 0));
        }
        return new BundleDefinition(name, null, stopOnFailure, false, true, list);
    }

    @Test
    @DisplayName("the file lists assertions, failures and markers copied from the records")
    void fileMatchesRecords(@TempDir File dir) throws Exception {
        BundleResultCollector sink = new BundleResultCollector(dir);
        BundleExecution execution = run(bundle("r1", false,
                "mark BEGIN", "assert pass", "assert fail", "bad", "mark END"), sink);

        File[] files = dir.listFiles();
        assertEquals(1, files.length);
        assertEquals("r1-900.json", files[0].getName());
        JsonObject doc = new JsonParser().parse(
                new String(Files.readAllBytes(files[0].toPath()), Charset.forName("UTF-8"))).getAsJsonObject();

        assertEquals(1, doc.get("schemaVersion").getAsInt());
        assertEquals("r1", doc.get("bundle").getAsString());
        assertEquals(execution.getHash(), doc.get("hash").getAsString());
        assertEquals("900", doc.get("startTick").getAsString());
        assertEquals("5", doc.get("executed").getAsString());
        assertEquals("2", doc.get("failed").getAsString());
        assertEquals("false", doc.get("stoppedEarly").getAsString());
        assertEquals(2, doc.getAsJsonArray("assertions").size());
        assertEquals("fail", doc.getAsJsonArray("assertions").get(1).getAsJsonObject().get("outcome").getAsString());
        assertEquals("2", doc.getAsJsonArray("assertions").get(1).getAsJsonObject().get("observed").getAsString());
        assertEquals(2, doc.getAsJsonArray("failures").size());
        assertEquals(2, doc.getAsJsonArray("markers").size());
        assertEquals("END", doc.getAsJsonArray("markers").get(1).getAsJsonObject().get("label").getAsString());

        // Every record in the file is also in the log: the file adds nothing.
        List<String> log = ToolkitLog.capturedForTesting();
        for (LogRecord record : sink.getRecords()) {
            assertTrue(log.contains(record.render()), record.render());
        }
    }

    @Test
    @DisplayName("an unflagged bundle writes nothing")
    void unflaggedWritesNothing(@TempDir File dir) {
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        list.add(new BundleCommand("mark X", 0));
        run(new BundleDefinition("plain", null, true, list), null);
        assertEquals(0, dir.listFiles().length);
    }

    @Test
    @DisplayName("records written while another execution advances do not reach this sink")
    void sinksAreIsolated(@TempDir File dir) {
        BundleResultCollector mine = new BundleResultCollector(null);
        BundleResultCollector theirs = new BundleResultCollector(null);

        ToolkitLog.setTap(mine);
        ToolkitLog.write(LogRecord.of(EventType.MARK).add("label", "mine"));
        ToolkitLog.setTap(theirs);
        ToolkitLog.write(LogRecord.of(EventType.MARK).add("label", "theirs"));
        ToolkitLog.setTap(null);
        ToolkitLog.write(LogRecord.of(EventType.MARK).add("label", "nobody"));

        assertEquals(1, mine.getRecords().size());
        assertEquals("mine", mine.getRecords().get(0).getFields().get("label"));
        assertEquals(1, theirs.getRecords().size());
        assertEquals("theirs", theirs.getRecords().get(0).getFields().get("label"));
    }

    @Test
    @DisplayName("a child's start record, written during the parent's turn, reaches only the child's sink")
    void childStartGoesToChildOnly() {
        BundleResultCollector parentSink = new BundleResultCollector(null);
        BundleResultCollector childSink = new BundleResultCollector(null);
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        list.add(new BundleCommand("mark child", 0));
        BundleExecution child = new BundleExecution(
                new BundleDefinition("child", null, true, false, true, list), DISPATCHER, CONTEXT);
        child.attachSink(childSink);

        // The parent is mid-advance: its sink is the tap when the child's start is written.
        ToolkitLog.setTap(parentSink);
        try {
            BundleRecorder.recordStart(child);
        } finally {
            ToolkitLog.setTap(null);
        }

        assertEquals(0, parentSink.getRecords().size(), parentSink.getRecords().toString());
        assertEquals(1, childSink.getRecords().size());
        assertEquals(EventType.BUNDLE_START, childSink.getRecords().get(0).getEventType());
        assertEquals("child", childSink.getRecords().get(0).getFields().get("bundle"));
    }

    @Test
    @DisplayName("a bundle discarded on server stop still gets its end record into the file")
    void discardedRunStillEndsItsFile(@TempDir File dir) throws Exception {
        BundleResultCollector sink = new BundleResultCollector(dir);
        List<BundleCommand> list = new ArrayList<BundleCommand>();
        list.add(new BundleCommand("mark a", 0));
        list.add(new BundleCommand("mark b", 40));
        BundleExecution execution = new BundleExecution(
                new BundleDefinition("cut", null, true, false, true, list), DISPATCHER, CONTEXT);
        execution.attachSink(sink);
        BundleRecorder.recordStart(execution);
        execution.onTick();
        ToolkitLog.setTap(sink);
        try {
            execution.advance(new BundleRecorder());
        } finally {
            ToolkitLog.setTap(null);
        }
        assertFalse(execution.isFinished());

        // No tap is set here, as on the real shutdown path.
        BundleRecorder.recordDiscarded(execution);

        File[] files = dir.listFiles();
        assertEquals(1, files.length);
        JsonObject doc = new JsonParser().parse(
                new String(Files.readAllBytes(files[0].toPath()), Charset.forName("UTF-8"))).getAsJsonObject();
        assertEquals("true", doc.get("discardedOnServerStop").getAsString());
        assertEquals("true", doc.get("stoppedEarly").getAsString());
        assertEquals("1", doc.get("executed").getAsString());
    }

    @Test
    @DisplayName("a write that cannot happen is an error record and the outcome is unchanged")
    void writeFailureIsRecordedOnly(@TempDir File dir) throws Exception {
        File notADirectory = new File(dir, "file.txt");
        Files.write(notADirectory.toPath(), new byte[0]);
        BundleResultCollector sink = new BundleResultCollector(notADirectory);
        BundleExecution execution = run(bundle("r2", true, "mark A"), sink);

        assertEquals(0, execution.getFailed());
        assertTrue(execution.isFinished());
        boolean reported = false;
        for (String line : ToolkitLog.capturedForTesting()) {
            if (line.contains("[ERROR]") && line.contains("Bundle result could not be written")) {
                reported = true;
            }
        }
        assertTrue(reported, ToolkitLog.capturedForTesting().toString());
    }

    @Test
    @DisplayName("the parser accepts result as a boolean and rejects anything else")
    void parserHandlesResult() throws Exception {
        Map<String, BundleDefinition> on = BundleParser.parse(
                "{\"t\": {\"result\": true, \"commands\": [\"a\"]}}");
        assertTrue(on.get("t").isResultRequested());
        assertFalse(BundleParser.parse("{\"t\": {\"commands\": [\"a\"]}}").get("t").isResultRequested());
        try {
            BundleParser.parse("{\"t\": {\"result\": 1, \"commands\": [\"a\"]}}");
            org.junit.jupiter.api.Assertions.fail("should have thrown");
        } catch (com.mahghuuuls.agenttesttoolkit.bundle.BundleParseException e) {
            assertTrue(e.getMessage().contains("result"), e.getMessage());
        }
    }
}
