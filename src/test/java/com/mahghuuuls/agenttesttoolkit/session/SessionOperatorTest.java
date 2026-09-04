package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When an operator binding exists, and what the log says about it.
 *
 * <p>The binding is the identity every placeholder and checkpoint will trust, so the rules
 * about its lifetime are the ones to pin: none without a session, gone when the session
 * stops, gone when the player leaves, and each change written down with its reason.
 */
class SessionOperatorTest {

    private static final RecordContext.Snapshot CONTEXT =
            new RecordContext.Snapshot(RecordContext.SERVER, 42L);
    private static final UUID ALICE = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BOB = UUID.fromString("66666666-7777-8888-9999-000000000000");

    @BeforeEach
    void reset() {
        ToolkitState.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void cleanup() {
        ToolkitLog.stopCaptureForTesting();
        ToolkitState.resetForTesting();
    }

    private static List<String> operatorRecords() {
        List<String> out = new ArrayList<String>();
        for (String line : ToolkitLog.capturedForTesting()) {
            if (line.contains("[OPERATOR]")) {
                out.add(line);
            }
        }
        return out;
    }

    @Test
    @DisplayName("binding requires an active session and records nothing without one")
    void noSessionNoBinding() {
        assertFalse(SessionManager.bindOperator(ALICE, "Alice", CONTEXT));
        assertNull(ToolkitState.getOperator());
        assertTrue(operatorRecords().isEmpty());
    }

    @Test
    @DisplayName("binding records the name and UUID under the session")
    void bindingIsRecorded() {
        SessionManager.start("s1", CONTEXT);
        assertTrue(SessionManager.bindOperator(ALICE, "Alice", CONTEXT));

        OperatorBinding binding = ToolkitState.getOperator();
        assertNotNull(binding);
        assertEquals("Alice", binding.getName());
        assertEquals(ALICE, binding.getUuid());
        assertEquals("@p[name=Alice]", binding.getSelector());

        List<String> records = operatorRecords();
        assertEquals(1, records.size(), records.toString());
        assertTrue(records.get(0).contains("action=bound"), records.get(0));
        assertTrue(records.get(0).contains("name=Alice"), records.get(0));
        assertTrue(records.get(0).contains("uuid=" + ALICE), records.get(0));
        assertTrue(records.get(0).contains("session=s1"), records.get(0));
    }

    @Test
    @DisplayName("rebinding replaces and says who was replaced")
    void rebindingReplaces() {
        SessionManager.start("s1", CONTEXT);
        SessionManager.bindOperator(ALICE, "Alice", CONTEXT);
        SessionManager.bindOperator(BOB, "Bob", CONTEXT);
        assertEquals("Bob", ToolkitState.getOperator().getName());
        String last = operatorRecords().get(1);
        assertTrue(last.contains("replaced=Alice"), last);
    }

    @Test
    @DisplayName("stopping the session unbinds with reason sessionStop, before the stop record")
    void sessionStopUnbinds() {
        SessionManager.start("s1", CONTEXT);
        SessionManager.bindOperator(ALICE, "Alice", CONTEXT);
        SessionManager.stop(CONTEXT);

        assertNull(ToolkitState.getOperator());
        List<String> all = ToolkitLog.capturedForTesting();
        int unbound = -1;
        int stop = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).contains("action=unbound")) {
                unbound = i;
                assertTrue(all.get(i).contains("reason=sessionStop"), all.get(i));
                assertTrue(all.get(i).contains("session=s1"), all.get(i));
            }
            if (all.get(i).contains("[SESSION_STOP]")) {
                stop = i;
            }
        }
        assertTrue(unbound >= 0 && stop > unbound, all.toString());
    }

    @Test
    @DisplayName("starting a new session drops the old binding with the old session")
    void newSessionDropsBinding() {
        SessionManager.start("s1", CONTEXT);
        SessionManager.bindOperator(ALICE, "Alice", CONTEXT);
        SessionManager.start("s2", CONTEXT);
        assertNull(ToolkitState.getOperator());
        assertTrue(operatorRecords().get(1).contains("reason=sessionStop"));
    }

    @Test
    @DisplayName("a disconnect unbinds with its own reason, and unbinding twice is silent")
    void disconnectUnbinds() {
        SessionManager.start("s1", CONTEXT);
        SessionManager.bindOperator(ALICE, "Alice", CONTEXT);
        assertNotNull(SessionManager.unbindOperator("disconnect", CONTEXT));
        assertNull(ToolkitState.getOperator());
        assertTrue(operatorRecords().get(1).contains("reason=disconnect"));
        assertNull(SessionManager.unbindOperator("disconnect", CONTEXT));
        assertEquals(2, operatorRecords().size());
    }
}
