package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.PendingRejoin;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The same player back from a reconnect with another casing of the name. */
class InboxOperatorNameTest {

    private static final UUID OPERATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void start() {
        ToolkitState.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void stop() {
        ToolkitLog.stopCaptureForTesting();
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("a rename keeps everything but the name")
    void renamed() {
        InboxOpening opening = new InboxOpening(OPERATOR, "Mahghuuuls", 5L, 1L, "world|1").suspendedUntil(99L);
        InboxOpening renamed = opening.renamed("mahghuuuls");
        assertEquals("mahghuuuls", renamed.getOperatorName());
        assertEquals(OPERATOR, renamed.getOperatorUuid());
        assertTrue(renamed.isSuspended());
    }

    @Test
    @DisplayName("the resume takes the returning name, and its record carries the UUID")
    void resumeTakesTheReturningName() {
        long now = System.currentTimeMillis();
        ToolkitState.setInboxOpening(new InboxOpening(OPERATOR, "Mahghuuuls", 5L, 1L, "world|1")
                .suspendedUntil(now + 60_000L));
        ToolkitState.setPendingRejoin(new PendingRejoin(OPERATOR, "t0k", 7L, now).leaving(now));

        InboxGate.onReturn(OPERATOR, "mahghuuuls", "t0k", RecordContext.Snapshot.NONE);

        assertEquals("mahghuuuls", ToolkitState.getInboxOpening().getOperatorName());
        List<String> records = ToolkitLog.capturedForTesting();
        String resumed = null;
        for (String record : records) {
            if (record.contains("action=resumed")) {
                resumed = record;
            }
        }
        assertTrue(resumed != null, String.valueOf(records));
        assertTrue(resumed.contains("operator=mahghuuuls"), resumed);
        assertTrue(resumed.contains("operatorUuid=" + OPERATOR), resumed);
    }
}
