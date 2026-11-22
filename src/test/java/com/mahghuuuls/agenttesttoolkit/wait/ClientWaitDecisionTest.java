package com.mahghuuuls.agenttesttoolkit.wait;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.mahghuuuls.agenttesttoolkit.wait.ClientWaitDecision.MODE_DONE;
import static com.mahghuuuls.agenttesttoolkit.wait.ClientWaitDecision.MODE_STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What a client's answer means for a waiting step, over every result and every release word the
 * client sends, and over the expected target's outcomes. The condition and {@code client await}
 * both ask here, so a word mapped wrong here is mapped wrong for every waiting step.
 */
class ClientWaitDecisionTest {

    private static final String COW = "11111111-2222-3333-4444-555555555555";
    private static final String OTHER = "99999999-2222-3333-4444-555555555555";

    private static ClientWaitDecision.Decision plain(String mode, String result, String reason, String released,
                                                     String heldTicks) {
        return ClientWaitDecision.decide(mode, result, reason, released, heldTicks, null, null, null);
    }

    private static ClientWaitDecision.Decision target(String result, String reportedUuid, String alive) {
        return ClientWaitDecision.decide(MODE_DONE, result, null, null, null, COW, reportedUuid, alive);
    }

    @Test
    @DisplayName("no answer yet is pending in both modes")
    void noAnswerIsPending() {
        assertEquals(ClientWaitDecision.Status.PENDING, plain(MODE_STARTED, null, null, null, null).getStatus());
        assertEquals(ClientWaitDecision.Status.PENDING, plain(MODE_DONE, null, null, null, null).getStatus());
    }

    @Test
    @DisplayName("refused, failed and left fail with actionFailed and carry the client's reason, in both modes")
    void refusalsFailWithTheReason() {
        for (String mode : new String[] {MODE_STARTED, MODE_DONE}) {
            for (String result : new String[] {"refused", "failed", "left"}) {
                ClientWaitDecision.Decision decision = plain(mode, result, "screenOpen", null, null);
                assertEquals(ClientWaitDecision.Status.FAILED, decision.getStatus(), mode + " " + result);
                assertEquals("devtool.client.actionFailed", decision.getKey());
                assertEquals("result=" + result + " reason=screenOpen", decision.getDetail());
            }
        }
        assertEquals("result=failed", plain(MODE_DONE, "failed", "", null, null).getDetail(), "no empty reason word");
    }

    @Test
    @DisplayName("started passes a started wait and keeps a done wait pending")
    void startedAnswer() {
        assertEquals(ClientWaitDecision.Status.SATISFIED, plain(MODE_STARTED, "started", null, null, null).getStatus());
        assertEquals(ClientWaitDecision.Status.PENDING, plain(MODE_DONE, "started", null, null, null).getStatus());
    }

    @Test
    @DisplayName("done passes; a step answered once passes a started wait on its only answer")
    void doneAnswer() {
        assertEquals(ClientWaitDecision.Status.SATISFIED, plain(MODE_DONE, "done", null, null, null).getStatus());
        assertEquals(ClientWaitDecision.Status.SATISFIED, plain(MODE_STARTED, "done", null, null, null).getStatus());
        assertNull(plain(MODE_DONE, "done", null, null, null).getKey());
    }

    @Test
    @DisplayName("a release of done passes; every other release word fails releasedEarly with released and heldTicks")
    void everyReleaseWord() {
        assertEquals(ClientWaitDecision.Status.SATISFIED, plain(MODE_DONE, "done", null, "done", "215").getStatus());
        for (String released : new String[] {"screenOpen", "died", "focusLost", "nextAction", "released", "left"}) {
            ClientWaitDecision.Decision decision = plain(MODE_DONE, "done", null, released, "12");
            assertEquals(ClientWaitDecision.Status.FAILED, decision.getStatus(), released);
            assertEquals("devtool.client.releasedEarly", decision.getKey());
            assertEquals("released=" + released + " heldTicks=12", decision.getDetail());
        }
        assertEquals("released=nextAction", plain(MODE_DONE, "done", null, "nextAction", null).getDetail(),
                "a turn has no heldTicks");
    }

    @Test
    @DisplayName("the expected entity, alive, passes; another or none fails wrongTarget; dying fails targetDead")
    void targetOutcomes() {
        assertEquals(ClientWaitDecision.Status.SATISFIED, target("done", COW, "true").getStatus());
        assertEquals(ClientWaitDecision.Status.SATISFIED, target("done", COW.toUpperCase(), "true").getStatus(),
                "UUID text in either case");
        ClientWaitDecision.Decision wrong = target("done", OTHER, "true");
        assertEquals("devtool.client.wrongTarget", wrong.getKey());
        assertEquals("expected=" + COW + " entityUuid=" + OTHER, wrong.getDetail());
        ClientWaitDecision.Decision none = target("done", null, null);
        assertEquals("devtool.client.wrongTarget", none.getKey());
        assertEquals("expected=" + COW + " entityUuid=none", none.getDetail());
        ClientWaitDecision.Decision dead = target("done", COW, "false");
        assertEquals("devtool.client.targetDead", dead.getKey());
        assertEquals("entityUuid=" + COW + " alive=false", dead.getDetail());
        assertEquals(ClientWaitDecision.Status.SATISFIED, target("done", COW, null).getStatus(),
                "a 1.9.0 client says nothing of alive, so only the entity is checked");
    }

    @Test
    @DisplayName("with an expected target a started answer never passes, even for a started wait")
    void expectWaitsForDone() {
        assertEquals(ClientWaitDecision.Status.PENDING,
                ClientWaitDecision.decide(MODE_STARTED, "started", null, null, null, COW, COW, "true").getStatus());
        assertEquals(ClientWaitDecision.Status.PENDING, target("started", OTHER, "true").getStatus(),
                "judged when the action is over");
    }

    @Test
    @DisplayName("a refusal outranks the target, and the target outranks an early release")
    void order() {
        assertEquals("devtool.client.actionFailed", target("refused", OTHER, "false").getKey());
        assertEquals("devtool.client.wrongTarget",
                ClientWaitDecision.decide(MODE_DONE, "done", null, "focusLost", "3", COW, OTHER, "true").getKey());
        assertEquals("devtool.client.releasedEarly",
                ClientWaitDecision.decide(MODE_DONE, "done", null, "focusLost", "3", COW, COW, "true").getKey());
    }
}
