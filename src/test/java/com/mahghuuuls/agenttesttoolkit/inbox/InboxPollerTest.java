package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.server.MinecraftServer;
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

/** The gate and one pass over the folder, on a temporary folder and without a server. */
class InboxPollerTest {

    private static final RecordContext.Snapshot CONTEXT =
            new RecordContext.Snapshot(RecordContext.SERVER, 500L);
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Records every request it is handed. */
    private static final class Recorder implements InboxRequestHandler {
        final List<String> lines = new ArrayList<String>();

        @Override
        public void handle(MinecraftServer server, InboxRequest request, RecordContext.Snapshot context) {
            lines.add(request.getCommandLine());
        }
    }

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

    private static void write(File dir, String name, String content) throws Exception {
        Files.write(new File(dir, name).toPath(), content.getBytes(Charset.forName("UTF-8")));
    }

    private static List<String> records(String action) {
        List<String> found = new ArrayList<String>();
        for (String record : ToolkitLog.capturedForTesting()) {
            if (record.contains("[INBOX]") && record.contains("action=" + action)) {
                found.add(record);
            }
        }
        return found;
    }

    @Test
    @DisplayName("the gate refuses when the configuration key is false")
    void disabledRefuses() {
        assertEquals(InboxGate.OpenResult.DISABLED, InboxGate.open(false, OWNER, "Dev", CONTEXT));
        assertFalse(InboxGate.isOpen());
        assertTrue(records("refused").get(0).contains("reason=disabled"));
    }

    @Test
    @DisplayName("the gate opens once, for one player, and records it")
    void openOnce() {
        assertEquals(InboxGate.OpenResult.OPENED, InboxGate.open(true, OWNER, "Dev", CONTEXT));
        assertTrue(InboxGate.isOpen());
        assertTrue(InboxGate.isOperator(OWNER));
        assertEquals(1, records("opened").size());
        assertEquals(InboxGate.OpenResult.ALREADY_OPEN, InboxGate.open(true, OWNER, "Dev", CONTEXT));
        assertEquals(InboxGate.OpenResult.OPEN_FOR_ANOTHER, InboxGate.open(true, OTHER, "Bob", CONTEXT));
        assertFalse(InboxGate.isOperator(OTHER));
        assertEquals(1, records("opened").size());
    }

    @Test
    @DisplayName("closing records the reason, and closing twice does nothing")
    void close() {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        assertTrue(InboxGate.close("operatorLeft", CONTEXT));
        assertFalse(InboxGate.isOpen());
        assertTrue(records("closed").get(0).contains("reason=operatorLeft"));
        assertFalse(InboxGate.close("closed", CONTEXT));
        assertEquals(1, records("closed").size());
    }

    @Test
    @DisplayName("a fresh process state starts closed")
    void startsClosed() {
        assertFalse(InboxGate.isOpen());
        assertFalse(com.mahghuuuls.agenttesttoolkit.config.ToolkitConfig.DEFAULTS.isInboxEnabled(),
                "the configuration half of the gate is off by default");
        assertNull(InboxGate.current());
    }

    @Test
    @DisplayName("while closed, nothing in the folder is read or changed")
    void closedTouchesNothing(@TempDir File dir) throws Exception {
        write(dir, "a1.req", "run x");
        Recorder handler = new Recorder();

        assertEquals(0, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertTrue(new File(dir, "a1.req").exists());
        assertTrue(handler.lines.isEmpty());
        assertTrue(ToolkitLog.capturedForTesting().isEmpty());
    }

    @Test
    @DisplayName("requests run in file-name order, each once, and temporary files are ignored")
    void orderOnceAndTemp(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "b2.req", "run second");
        write(dir, "a1.req", "run first");
        write(dir, "c3.tmp", "run not_yet");
        write(dir, "notes.txt", "run ignored");
        Recorder handler = new Recorder();

        assertEquals(2, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertEquals(Arrays.asList("devtool run first", "devtool run second"), handler.lines);
        assertTrue(new File(dir, "a1.claimed").exists());
        assertFalse(new File(dir, "a1.req").exists());
        assertTrue(new File(dir, "c3.tmp").exists());

        // A second pass finds nothing: claimed requests never run again.
        assertEquals(0, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertEquals(2, handler.lines.size());

        // The temporary file, once renamed, is picked up.
        assertTrue(new File(dir, "c3.tmp").renameTo(new File(dir, "c3.req")));
        assertEquals(1, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertEquals("devtool run not_yet", handler.lines.get(2));
    }

    @Test
    @DisplayName("a refused request is claimed, recorded with its reason, and never handed on")
    void refusedNeverHandled(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a1.req", "gamemode creative");
        Recorder handler = new Recorder();

        assertEquals(1, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertTrue(handler.lines.isEmpty());
        assertEquals(1, records("claimed").size());
        String refused = records("refused").get(0);
        assertTrue(refused.contains("reason=verbNotAllowed"), refused);
        assertTrue(refused.contains("id=a1"), refused);
        assertTrue(refused.contains("operator=Dev"), refused);
        assertFalse(refused.contains("detail="), "no detail for another verb");
    }

    @Test
    @DisplayName("a line written with the devtool root is refused with the accepted line in the record and the result file")
    void refusedWithDetail(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a2.req", "devtool client respawn");
        assertEquals(1, InboxPoller.pollOnce(new InboxFiles(dir), new Recorder(), null, CONTEXT, true));
        String refused = records("refused").get(0);
        assertTrue(refused.contains("reason=verbNotAllowed"), refused);
        assertTrue(refused.contains("detail=\"use:client respawn\""), refused);
        String result = new String(java.nio.file.Files.readAllBytes(new File(dir, "a2.result.json").toPath()), "UTF-8");
        assertTrue(result.contains("\"detail\": \"use:client respawn\""), result);
    }

    @Test
    @DisplayName("a file too large to be a request is read as malformed")
    void oversizedIsMalformed(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        StringBuilder big = new StringBuilder("mark ");
        for (int i = 0; i < InboxFiles.MAX_FILE_BYTES + 10; i++) {
            big.append('x');
        }
        write(dir, "a1.req", big.toString());
        Recorder handler = new Recorder();

        InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true);
        assertTrue(handler.lines.isEmpty());
        assertTrue(records("refused").get(0).contains("reason=malformed"));
    }

    @Test
    @DisplayName("a request that closes the inbox stops the rest of the batch")
    void closingStopsBatch(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a1.req", "run first");
        write(dir, "b2.req", "run second");
        final List<String> seen = new ArrayList<String>();
        InboxRequestHandler closer = new InboxRequestHandler() {
            @Override
            public void handle(MinecraftServer server, InboxRequest request, RecordContext.Snapshot context) {
                seen.add(request.getCommandLine());
                InboxGate.close("operatorLeft", CONTEXT);
            }
        };

        assertEquals(1, InboxPoller.pollOnce(new InboxFiles(dir), closer, null, CONTEXT, true));
        assertEquals(1, seen.size());
        assertTrue(new File(dir, "b2.req").exists());
    }

    @Test
    @DisplayName("requests waiting when the inbox opens are refused, never run")
    void leftoversRefused(@TempDir File dir) throws Exception {
        write(dir, "a1.req", "run written_too_early");
        InboxGate.open(true, OWNER, "Dev", CONTEXT);

        assertEquals(1, InboxPoller.refuseLeftovers(new InboxFiles(dir), CONTEXT));
        assertTrue(records("refused").get(0).contains("reason=placedBeforeOpen"));
        Recorder handler = new Recorder();
        assertEquals(0, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertTrue(handler.lines.isEmpty());
    }

    @Test
    @DisplayName("a reused id is reported once and never run")
    void duplicateIdReportedOnce(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        InboxPoller.refuseLeftovers(new InboxFiles(dir), CONTEXT);
        write(dir, "a1.req", "run first");
        write(dir, "b1.req", "bogus");
        Recorder handler = new Recorder();
        InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true);
        String earlierAnswer = new String(java.nio.file.Files.readAllBytes(
                new File(dir, "b1.result.json").toPath()), "UTF-8");
        write(dir, "a1.req", "run again");
        write(dir, "b1.req", "mark reused");

        InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true);
        InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true);
        assertEquals(1, handler.lines.size());
        int duplicates = 0;
        for (String record : records("refused")) {
            if (record.contains("reason=duplicateId")) {
                duplicates++;
                assertTrue(record.contains("id=a1 request=\"run again\"")
                        || record.contains("id=b1 request=\"mark reused\""), record);
            }
        }
        assertEquals(2, duplicates);
        assertTrue(new File(dir, "a1.req").exists(), "the reused request is left waiting, not claimed");
        assertEquals("run first", new String(java.nio.file.Files.readAllBytes(
                new File(dir, "a1.claimed").toPath()), "UTF-8"), "the earlier claim is untouched");
        assertEquals(earlierAnswer, new String(java.nio.file.Files.readAllBytes(
                new File(dir, "b1.result.json").toPath()), "UTF-8"), "the earlier answer is not overwritten");
    }

    @Test
    @DisplayName("an exception from the handler is contained and recorded as failed")
    void handlerExceptionContained(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a1.req", "run first");
        write(dir, "b2.req", "run second");
        final List<String> seen = new ArrayList<String>();
        InboxRequestHandler thrower = new InboxRequestHandler() {
            @Override
            public void handle(MinecraftServer server, InboxRequest request, RecordContext.Snapshot context) {
                seen.add(request.getCommandLine());
                throw new IllegalStateException("another mod's listener");
            }
        };

        assertEquals(2, InboxPoller.pollOnce(new InboxFiles(dir), thrower, null, CONTEXT, true));
        assertEquals(2, seen.size());
        assertEquals(2, records("failed").size());
        assertTrue(records("failed").get(0).contains("reason=IllegalStateException"));
    }

    @Test
    @DisplayName("with the configuration key turned off, the open inbox closes and runs nothing")
    void configOffCloses(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a1.req", "run first");
        Recorder handler = new Recorder();

        assertEquals(0, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, false));
        assertFalse(InboxGate.isOpen());
        assertTrue(records("closed").get(0).contains("reason=disabled"));
        assertTrue(new File(dir, "a1.req").exists());
        assertTrue(handler.lines.isEmpty());
    }

    @Test
    @DisplayName("a folder named like a request is ignored")
    void directoryIgnored(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        assertTrue(new File(dir, "a1.req").mkdir());
        Recorder handler = new Recorder();

        assertEquals(0, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertTrue(handler.lines.isEmpty());
    }

    @Test
    @DisplayName("a reload ends the batch, so the next poll checks the key before running more")
    void reloadEndsBatch(@TempDir File dir) throws Exception {
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        write(dir, "a1.req", "reload");
        write(dir, "b2.req", "run after_reload");
        Recorder handler = new Recorder();

        assertEquals(1, InboxPoller.pollOnce(new InboxFiles(dir), handler, null, CONTEXT, true));
        assertEquals(Arrays.asList("devtool reload"), handler.lines);
        assertTrue(new File(dir, "b2.req").exists());
    }

    @Test
    @DisplayName("a refused request and a leftover both get a result file")
    void refusalsAreAnswered(@TempDir File dir) throws Exception {
        write(dir, "a0.req", "run left_over");
        InboxGate.open(true, OWNER, "Dev", CONTEXT);
        InboxPoller.refuseLeftovers(new InboxFiles(dir), CONTEXT);
        write(dir, "a1.req", "gamemode creative");
        InboxPoller.pollOnce(new InboxFiles(dir), new Recorder(), null, CONTEXT, true);

        assertTrue(new String(Files.readAllBytes(new File(dir, "a0.result.json").toPath()), Charset.forName("UTF-8"))
                .contains("\"placedBeforeOpen\""));
        assertTrue(new String(Files.readAllBytes(new File(dir, "a1.result.json").toPath()), Charset.forName("UTF-8"))
                .contains("\"verbNotAllowed\""));
    }
}
