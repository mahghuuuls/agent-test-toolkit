package com.mahghuuuls.agenttesttoolkit.wait;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.ContextSource;
import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the server keeps of a run's client steps and pictures, and the two conditions that read it:
 * a client action's wait, through the token its step was sent with, and a picture's wait, through
 * the label and request tick its request carried. Driven with plain values in place of the
 * network, so the matching and the forgetting are checked without a game.
 */
class ClientReportsTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @BeforeEach
    void reset() {
        ClientReports.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
        ClientReports.resetForTesting();
    }

    private static BundleExecution run(String name) {
        return new BundleExecution(new BundleDefinition(name, null, true,
                Collections.singletonList(new BundleCommand("say hi", 0))),
                new CommandDispatcher() {
                    @Override
                    public CommandOutcome dispatch(String command) {
                        return CommandOutcome.success();
                    }
                }, new ContextSource() {
                    @Override
                    public RecordContext.Snapshot snapshot() {
                        return RecordContext.Snapshot.NONE;
                    }
                });
    }

    private static Map<String, String> fields(String... pairs) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Test
    @DisplayName("a started wait on a hold passes on its started answer; a done wait then fails on an early release")
    void holdAnswers() {
        BundleExecution run = run("holds");
        ClientReports.sent(run, PLAYER, TOKEN, "key", 100L, "key.sneak", null);
        ClientActionWait started = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_STARTED, null);
        ClientActionWait done = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_DONE, "key.sneak");
        assertEquals(WaitCondition.Status.PENDING, started.poll(1).getStatus());

        ClientReports.reported(PLAYER, TOKEN, "started", null, fields("binding", "key.sneak"));
        assertEquals(WaitCondition.Status.SATISFIED, started.poll(2).getStatus());
        assertEquals(WaitCondition.Status.PENDING, done.poll(2).getStatus());

        ClientReports.reported(PLAYER, TOKEN, "done", null, fields("heldTicks", "7", "released", "focusLost"));
        WaitCondition.Poll poll = done.poll(3);
        assertEquals(WaitCondition.Status.FAILED, poll.getStatus());
        assertEquals("devtool.client.releasedEarly", poll.getKey());
        assertEquals("released=focusLost heldTicks=7", poll.getDetail());
        assertEquals(WaitCondition.Status.SATISFIED, started.poll(3).getStatus(), "started reads the first answer");
    }

    @Test
    @DisplayName("an answer to another token, or from another player, settles nothing")
    void otherTokensAndPlayers() {
        BundleExecution run = run("tokens");
        ClientReports.sent(run, PLAYER, TOKEN, "use", 100L, null, null);
        ClientActionWait wait = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_DONE, null);
        ClientReports.reported(PLAYER, "ffffffffffffffffffffffffffffffff", "done", null, fields());
        ClientReports.reported(OTHER, TOKEN, "done", null, fields());
        assertEquals(WaitCondition.Status.PENDING, wait.poll(1).getStatus());
        ClientReports.reported(PLAYER, TOKEN, "refused", "screenOpen", fields());
        assertEquals("devtool.client.actionFailed", wait.poll(2).getKey());
    }

    @Test
    @DisplayName("a hold's target comes from its started answer, which is the only one that names it")
    void holdTargetFromStarted() {
        BundleExecution run = run("target");
        String cow = "11111111-2222-3333-4444-555555555555";
        ClientReports.sent(run, PLAYER, TOKEN, "hold", 100L, "attack", cow);
        ClientActionWait wait = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_STARTED, null);
        ClientReports.reported(PLAYER, TOKEN, "started", null,
                fields("target", "entity", "entityUuid", cow, "alive", "false", "health", "0.00"));
        assertEquals(WaitCondition.Status.PENDING, wait.poll(1).getStatus(), "an expected target waits as done does");
        ClientReports.reported(PLAYER, TOKEN, "done", null, fields("heldTicks", "20", "released", "done"));
        assertEquals("devtool.client.targetDead", wait.poll(2).getKey());
    }

    @Test
    @DisplayName("a logout forgets the player's tokens: a waiting step is cancelled and await finds no hold")
    void logoutClearsTokens() {
        BundleExecution run = run("logout");
        ClientReports.sent(run, PLAYER, TOKEN, "key", 100L, "key.sneak", null);
        ClientActionWait wait = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_DONE, null);
        assertEquals(TOKEN, ClientReports.latestHold(run, PLAYER, "key.sneak"));

        ClientReports.loggedOut(PLAYER);

        WaitCondition.Poll poll = wait.poll(1);
        assertEquals(WaitCondition.Status.CANCELLED, poll.getStatus());
        assertEquals("senderLost", poll.getReason());
        assertNull(ClientReports.latestHold(run, PLAYER, "key.sneak"));
        assertNull(ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_DONE, null));
        ClientReports.reported(PLAYER, TOKEN, "done", null, fields());
        assertEquals(WaitCondition.Status.CANCELLED, wait.poll(2).getStatus(), "a late answer to the old token counts for nothing");
    }

    @Test
    @DisplayName("await reads the latest hold of that input in that run only")
    void latestHoldPerRunAndInput() {
        BundleExecution first = run("first");
        BundleExecution second = run("second");
        String later = "fedcba9876543210fedcba9876543210";
        ClientReports.sent(first, PLAYER, TOKEN, "key", 100L, "key.sneak", null);
        ClientReports.sent(first, PLAYER, later, "key", 101L, "key.sneak", null);
        ClientReports.sent(first, PLAYER, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "use", 102L, null, null);
        assertEquals(later, ClientReports.latestHold(first, PLAYER, "key.sneak"));
        assertNull(ClientReports.latestHold(second, PLAYER, "key.sneak"), "another run started none");
        assertNull(ClientReports.latestHold(first, PLAYER, "key.forward"));
        assertNull(ClientReports.latestHold(first, OTHER, "key.sneak"));
    }

    @Test
    @DisplayName("a run that ends forgets what it kept")
    void runEndForgets() {
        BundleExecution run = run("ends");
        ClientReports.sent(run, PLAYER, TOKEN, "key", 100L, "key.sneak", null);
        ClientReports.requestedPicture(run, PLAYER, "shot_a", 100L);
        while (!run.isFinished()) {
            run.onTick();
            run.advance(new BundleRecorder());
        }
        assertNull(ClientReports.latestHold(run, PLAYER, "key.sneak"));
        assertNull(ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_DONE, null));
        assertNull(ScreenshotWait.forLatest(run, "shot_a"));
    }

    @Test
    @DisplayName("a picture's wait matches its player, label and request tick, and only the latest request of the run")
    void pictureMatching() {
        BundleExecution run = run("pictures");
        ClientReports.requestedPicture(run, PLAYER, "shot_a", 100L);
        ClientReports.requestedPicture(run, PLAYER, "shot_a", 140L);
        ScreenshotWait wait = ScreenshotWait.forLatest(run, "shot_a");
        assertNotNull(wait);
        assertEquals(PLAYER, wait.getPlayer());

        ClientReports.pictureReported(PLAYER, "shot_a", 100L, true, "screenshots/shot_a-100.png", 5L, null);
        assertEquals(WaitCondition.Status.PENDING, wait.poll(1).getStatus(), "the earlier request's report");
        ClientReports.pictureReported(PLAYER, "shot_b", 140L, true, "screenshots/shot_b-140.png", 5L, null);
        ClientReports.pictureReported(OTHER, "shot_a", 140L, true, "screenshots/shot_a-140.png", 5L, null);
        assertEquals(WaitCondition.Status.PENDING, wait.poll(2).getStatus(), "another label, another player");

        ClientReports.pictureReported(PLAYER, "shot_a", 140L, true, "screenshots/shot_a-140.png", 9L, null);
        assertEquals(WaitCondition.Status.SATISFIED, wait.poll(3).getStatus());
        assertNull(ScreenshotWait.forLatest(run, "shot_b"), "nothing asked under that label");
        assertNull(ScreenshotWait.forLatest(run("other"), "shot_a"), "another run asked nothing");
    }

    @Test
    @DisplayName("a picture reported before its wait counts; a failed one fails with screenshot.failed; a logout cancels")
    void pictureOutcomes() {
        BundleExecution run = run("outcomes");
        ClientReports.requestedPicture(run, PLAYER, "early", 100L);
        ClientReports.pictureReported(PLAYER, "early", 100L, true, "screenshots/early-100.png", 3L, null);
        assertEquals(WaitCondition.Status.SATISFIED, ScreenshotWait.forLatest(run, "early").poll(0).getStatus());

        ClientReports.requestedPicture(run, PLAYER, "broken", 101L);
        ClientReports.pictureReported(PLAYER, "broken", 101L, false, "", 3L, "noFile");
        WaitCondition.Poll failed = ScreenshotWait.forLatest(run, "broken").poll(0);
        assertEquals(WaitCondition.Status.FAILED, failed.getStatus());
        assertEquals("devtool.screenshot.failed", failed.getKey());
        assertEquals("label=broken requestTick=101 reason=noFile", failed.getDetail(), "the client's reason travels on");

        ClientReports.requestedPicture(run, PLAYER, "gone", 102L);
        ScreenshotWait gone = ScreenshotWait.forLatest(run, "gone");
        ClientReports.loggedOut(PLAYER);
        assertEquals(WaitCondition.Status.CANCELLED, gone.poll(1).getStatus());
        assertTrue(ScreenshotWait.forLatest(run, "early").poll(1).getStatus() == WaitCondition.Status.SATISFIED,
                "a picture already reported stays reported");
    }

    @Test
    @DisplayName("a client wait's AWAIT record names condition=client, the step, its request tick, the outcome and the key")
    void awaitRecordFields() {
        BundleExecution run = run("record");
        ClientReports.sent(run, PLAYER, TOKEN, "key", 4321L, "key.sneak", null);
        ClientActionWait wait = ClientActionWait.forToken(PLAYER, TOKEN, ClientWaitDecision.MODE_STARTED, null);
        ClientReports.reported(PLAYER, TOKEN, "refused", "screenOpen", fields());
        WaitCondition.Poll poll = wait.poll(3);
        BundleRecorder.recordAwait(RecordContext.Snapshot.NONE, "record", "h", 1,
                "devtool client key key.sneak hold 215 wait=started", wait, "failed", poll.getKey(), 3, 200);
        String line = ToolkitLog.capturedForTesting().get(ToolkitLog.capturedForTesting().size() - 1);
        for (String field : new String[] {"[AWAIT]", "condition=client", "step=key", "requestTick=4321",
                "outcome=failed", "key=devtool.client.actionFailed", "mode=started", "result=refused",
                "detail=\"result=refused reason=screenOpen\""}) {
            assertTrue(line.contains(field), field + " in " + line);
        }

        LogRecord expecting = LogRecord.of(EventType.AWAIT);
        String cow = "11111111-2222-3333-4444-555555555555";
        ClientReports.sent(run, PLAYER, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "attack", 50L, null, cow);
        ClientActionWait target = ClientActionWait.forToken(PLAYER, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                ClientWaitDecision.MODE_STARTED, null);
        ClientReports.reported(PLAYER, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "done", null,
                fields("target", "entity", "entityUuid", cow, "alive", "true"));
        assertEquals(WaitCondition.Status.SATISFIED, target.poll(1).getStatus());
        target.describe(expecting);
        assertEquals("done", expecting.getFields().get("mode"), "an expected target always waits as done");
        assertEquals(cow, expecting.getFields().get("expectUuid"));
        assertEquals(cow, expecting.getFields().get("entityUuid"));
        assertEquals("true", expecting.getFields().get("alive"));
        assertEquals("50", expecting.getFields().get("requestTick"));
    }
}
