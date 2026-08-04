package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session lifecycle, record stamping, and emitted record content.
 *
 * <p>Covers everything about REQ-041, REQ-043 and REQ-050 through REQ-055 that does not need a
 * running game. The one thing these cannot cover is the behavior REQ-052 exists for: whether
 * the state survives an integrated server shutdown. That is a property of where the state
 * lives rather than of its logic, so no single-threaded unit test can distinguish a static
 * that survives from one a lifecycle handler would clear. It needs a real world exit and is
 * verified manually.
 */
class SessionManagerTest {

    /** A world context, as a command run inside a loaded world would produce. */
    private static final RecordContext.Snapshot IN_WORLD =
            new RecordContext.Snapshot(RecordContext.SERVER, 1000L);

    /** No world context, as a console command before any world loads would produce. */
    private static final RecordContext.Snapshot NO_WORLD = RecordContext.Snapshot.NONE;

    @BeforeEach
    void setUp() {
        ToolkitState.resetForTesting();
        ToolkitLog.startCaptureForTesting();
    }

    @AfterEach
    void tearDown() {
        ToolkitLog.stopCaptureForTesting();
        ToolkitState.resetForTesting();
    }

    // --- Lifecycle ------------------------------------------------------------------

    @Test
    @DisplayName("no session is active in a fresh process")
    void freshProcessHasNoSession() {
        assertFalse(ToolkitState.hasActiveSession());
        assertNull(ToolkitState.getActiveSession());
    }

    @Test
    @DisplayName("start activates a named session at tick zero")
    void startActivatesSession() {
        assertNull(SessionManager.start("spell_damage", IN_WORLD));
        assertTrue(ToolkitState.hasActiveSession());
        assertEquals("spell_damage", ToolkitState.getActiveSession().getName());
        assertEquals(0L, ToolkitState.getActiveSession().getTick());
    }

    @Test
    @DisplayName("stop clears the session and reports its name")
    void stopClearsSession() {
        SessionManager.start("spell_damage", IN_WORLD);
        assertEquals("spell_damage", SessionManager.stop(IN_WORLD));
        assertFalse(ToolkitState.hasActiveSession());
    }

    @Test
    @DisplayName("stop with no active session is a no-op and emits nothing")
    void stopWithNoSessionIsNoOp() {
        assertNull(SessionManager.stop(IN_WORLD));
        assertFalse(ToolkitState.hasActiveSession());
        assertTrue(ToolkitLog.capturedForTesting().isEmpty());
    }

    @Test
    @DisplayName("starting while active replaces the previous session")
    void startReplacesActiveSession() {
        SessionManager.start("a", IN_WORLD);
        assertEquals("a", SessionManager.start("b", IN_WORLD));
        assertEquals("b", ToolkitState.getActiveSession().getName());
    }

    @Test
    @DisplayName("a replacing session starts its tick counter from zero")
    void replacementResetsTick() {
        SessionManager.start("a", IN_WORLD);
        SessionManager.onServerTick();
        SessionManager.onServerTick();
        assertEquals(2L, ToolkitState.getActiveSession().getTick());

        SessionManager.start("b", IN_WORLD);
        assertEquals(0L, ToolkitState.getActiveSession().getTick());
    }

    @Test
    @DisplayName("the tick counter advances once per server tick")
    void tickCounterAdvances() {
        SessionManager.start("timing", IN_WORLD);
        for (int i = 0; i < 40; i++) {
            SessionManager.onServerTick();
        }
        assertEquals(40L, ToolkitState.getActiveSession().getTick());
    }

    @Test
    @DisplayName("ticking with no active session does not fail")
    void tickWithNoSessionIsSafe() {
        SessionManager.onServerTick();
        assertFalse(ToolkitState.hasActiveSession());
    }

    @Test
    @DisplayName("a session name must not be empty")
    void rejectsEmptySessionName() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSession(""));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSession(null));
    }

    // --- Emitted record content -----------------------------------------------------
    // These exist because independent review found `side` missing from SESSION_START and
    // SESSION_STOP while the whole suite passed: nothing could observe a rendered record.
    // REQ-041 requires side on EVERY record, so it is asserted per event type, not once.

    @Test
    @DisplayName("SESSION_START carries side, worldTick and session name")
    void sessionStartRecordCarriesRequiredFields() {
        SessionManager.start("spell_damage", IN_WORLD);
        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(1, records.size());
        assertEquals("[DevToolkit][SESSION_START] side=SERVER worldTick=1000 session=spell_damage",
                records.get(0));
    }

    @Test
    @DisplayName("SESSION_STOP carries side, worldTick, session name and final tick")
    void sessionStopRecordCarriesRequiredFields() {
        SessionManager.start("spell_damage", IN_WORLD);
        SessionManager.onServerTick();
        SessionManager.onServerTick();
        ToolkitLog.startCaptureForTesting();

        SessionManager.stop(IN_WORLD);
        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(1, records.size());
        assertEquals("[DevToolkit][SESSION_STOP] side=SERVER worldTick=1000 session=spell_damage sessionTick=2",
                records.get(0));
    }

    @Test
    @DisplayName("replacement emits SESSION_STOP before SESSION_START, both with side")
    void replacementEmitsStopThenStart() {
        SessionManager.start("first", IN_WORLD);
        ToolkitLog.startCaptureForTesting();

        SessionManager.start("second", IN_WORLD);
        List<String> records = ToolkitLog.capturedForTesting();
        assertEquals(2, records.size());
        assertTrue(records.get(0).startsWith("[DevToolkit][SESSION_STOP]"), records.get(0));
        assertTrue(records.get(1).startsWith("[DevToolkit][SESSION_START]"), records.get(1));
        assertTrue(records.get(0).contains("side=SERVER"), records.get(0));
        assertTrue(records.get(1).contains("side=SERVER"), records.get(1));
    }

    @Test
    @DisplayName("side is still emitted when no world context exists, worldTick is omitted")
    void sideSurvivesMissingWorldContext() {
        // REQ-041 has no exception for a worldless sender: side is always resolvable and must
        // always appear. worldTick genuinely cannot be known, so it is omitted per REQ-033.
        SessionManager.start("console_started", NO_WORLD);
        String record = ToolkitLog.capturedForTesting().get(0);
        assertEquals("[DevToolkit][SESSION_START] side=SERVER session=console_started", record);
        assertFalse(record.contains("worldTick"));
    }

    // --- Stamping -------------------------------------------------------------------

    @Test
    @DisplayName("stamp adds session name and tick while a session is active")
    void stampAddsSessionFields() {
        SessionManager.start("spell_damage", IN_WORLD);
        SessionManager.onServerTick();
        SessionManager.onServerTick();
        SessionManager.onServerTick();

        String out = SessionStamp.apply(LogRecord.of(EventType.MARK)).add("label", "X").render();
        assertEquals("[DevToolkit][MARK] session=spell_damage sessionTick=3 label=X", out);
    }

    @Test
    @DisplayName("stamp omits both fields when no session is active")
    void stampOmitsFieldsWithoutSession() {
        // REQ-054: a marker must work without a session, and REQ-033 says an absent optional
        // value is omitted rather than rendered as a placeholder.
        String out = SessionStamp.apply(LogRecord.of(EventType.MARK)).add("label", "X").render();
        assertEquals("[DevToolkit][MARK] label=X", out);
        assertFalse(out.contains("session"));
    }

    @Test
    @DisplayName("a session name containing whitespace is quoted in records")
    void sessionNameWithWhitespaceIsQuoted() {
        SessionManager.start("spell damage test", IN_WORLD);
        String out = SessionStamp.apply(LogRecord.of(EventType.MARK)).render();
        assertTrue(out.contains("session=\"spell damage test\""), out);
    }
}
