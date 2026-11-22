package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.QuietScope;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which client reports give a single inbox request's late failure line: the final refused or
 * failed answer of a step marked while that request was worked on, once; nothing for a done
 * answer, a step a bundle sent, or a step asked before the player left.
 */
class InboxClientFailuresTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000042");

    /** A quiet bundle run, as the scope sees one. */
    private static final QuietScope.Run BUNDLE_RUN = new QuietScope.Run() {
        @Override
        public boolean isQuiet() {
            return true;
        }

        @Override
        public void noteSuppressed(int lines) {
        }
    };

    @BeforeEach
    @AfterEach
    void reset() {
        InboxClientFailures.resetForTesting();
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    private static void markWithin(QuietScope.Run scope, String step, long tick, String token) {
        QuietScope.enter(scope);
        try {
            InboxClientFailures.markIfRequest(PLAYER, step, tick, token);
        } finally {
            QuietScope.exit();
        }
    }

    private static ClientActionReport report(String step, String result, String reason, String token, long tick) {
        return new ClientActionReport(step, result, reason, "", "", token, tick);
    }

    @Test
    @DisplayName("a close a request sent, failed on the client, gives the request's line once")
    void failedCloseFromRequest() {
        markWithin(QuietScope.REQUEST, "close", 10L, "");
        assertEquals("[DevToolkit] Inbox request 'client close' failed: noScreen",
                InboxClientFailures.lineFor(PLAYER, report("close", "failed", "noScreen", "", 10L)));
        assertNull(InboxClientFailures.lineFor(PLAYER, report("close", "failed", "noScreen", "", 10L)),
                "the final answer forgets the mark, so no second line");
    }

    @Test
    @DisplayName("a refused token step gives the line after a started answer, which is not final")
    void refusedTokenStep() {
        markWithin(QuietScope.REQUEST, "hold", 20L, "t1");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("hold", "started", "", "t1", 20L)));
        assertEquals("[DevToolkit] Inbox request 'client hold' failed: refused",
                InboxClientFailures.lineFor(PLAYER, report("hold", "refused", "", "t1", 20L)),
                "with no reason, the result stands in");
    }

    @Test
    @DisplayName("a done answer gives no line, and forgets the mark")
    void doneIsSilent() {
        markWithin(QuietScope.REQUEST, "close", 30L, "");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("close", "done", "", "", 30L)));
        assertNull(InboxClientFailures.lineFor(PLAYER, report("close", "failed", "noScreen", "", 30L)));
    }

    @Test
    @DisplayName("a step sent inside a bundle run, or outside any scope, is not marked")
    void bundleStepsAreNotMarked() {
        markWithin(BUNDLE_RUN, "use", 40L, "t2");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("use", "failed", "noTarget", "t2", 40L)));
        InboxClientFailures.markIfRequest(PLAYER, "close", 41L, "");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("close", "failed", "noScreen", "", 41L)));
    }

    @Test
    @DisplayName("a report for another request tick does not match an otherwise matching mark")
    void tickMustMatch() {
        markWithin(QuietScope.REQUEST, "use", 60L, "t3");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("use", "failed", "noTarget", "t3", 61L)),
                "same step, token and result, one tick later");
        assertNull(InboxClientFailures.lineFor(PLAYER, report("use", "failed", "noTarget", "t4", 60L)),
                "same step, tick and result, another token");
        assertEquals("[DevToolkit] Inbox request 'client use' failed: noTarget",
                InboxClientFailures.lineFor(PLAYER, report("use", "failed", "noTarget", "t3", 60L)),
                "the mark itself was kept and matches its own report");
    }

    @Test
    @DisplayName("a leave forgets the marks of steps asked before it")
    void logoutForgets() {
        markWithin(QuietScope.REQUEST, "close", 50L, "");
        InboxClientFailures.forget(PLAYER);
        assertNull(InboxClientFailures.lineFor(PLAYER, report("close", "failed", "noScreen", "", 50L)));
    }
}
