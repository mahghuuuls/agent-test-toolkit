package com.mahghuuuls.agenttesttoolkit.session;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import com.mahghuuuls.agenttesttoolkit.world.WorldIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What counts as the same world, and what a world load does to an active session. */
class WorldChangeTest {

    private static final RecordContext.Snapshot CONTEXT =
            new RecordContext.Snapshot(RecordContext.SERVER, 500L);
    private static final WorldIdentity A = new WorldIdentity("New World", 42L);
    private static final WorldIdentity B = new WorldIdentity("CYC4 World B", 7L);

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

    @Test
    @DisplayName("the same folder and seed are the same world; a new seed under the same name is not")
    void identity() {
        assertEquals(A, new WorldIdentity("New World", 42L));
        assertEquals(A.key(), new WorldIdentity("New World", 42L).key());
        assertNotEquals(A, new WorldIdentity("New World", 43L));
        assertNotEquals(A.key(), new WorldIdentity("New World", 43L).key());
        assertFalse(A.endsSessionFrom(A.key()));
        assertTrue(A.endsSessionFrom(B.key()));
        assertFalse(A.endsSessionFrom(null), "a session that never knew its world is kept");
    }

    @Test
    @DisplayName("reopening the same world keeps the session, and the record says so")
    void sameWorldKeepsSession() {
        WorldChangeListener.loaded(A, CONTEXT);
        SessionManager.start("cyc4", CONTEXT);
        ToolkitLog.startCaptureForTesting();

        WorldChangeListener.loaded(A, CONTEXT);

        assertTrue(ToolkitState.hasActiveSession());
        List<String> lines = ToolkitLog.capturedForTesting();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[WORLD]"), lines.get(0));
        assertTrue(lines.get(0).contains("session=cyc4"), lines.get(0));
        assertTrue(lines.get(0).contains("folder=\"New World\""), lines.get(0));
        assertTrue(lines.get(0).contains("sameAsSession=true"), lines.get(0));
    }

    @Test
    @DisplayName("a different world stops the session first, and its record carries no session")
    void otherWorldStopsSessionFirst() {
        WorldChangeListener.loaded(A, CONTEXT);
        SessionManager.start("cyc4", CONTEXT);
        ToolkitLog.startCaptureForTesting();

        WorldChangeListener.loaded(B, CONTEXT);

        assertFalse(ToolkitState.hasActiveSession());
        List<String> lines = ToolkitLog.capturedForTesting();
        assertEquals(2, lines.size(), String.valueOf(lines));
        assertTrue(lines.get(0).contains("[SESSION_STOP]"), lines.get(0));
        assertTrue(lines.get(0).contains("reason=worldChanged"), lines.get(0));
        assertTrue(lines.get(1).contains("[WORLD]"), lines.get(1));
        assertFalse(lines.get(1).contains("session="), "nothing from world B carries the old session");
        assertTrue(lines.get(1).contains("sameAsSession=false"), lines.get(1));
        assertEquals(B.key(), ToolkitState.getCurrentWorldKey());
    }

    @Test
    @DisplayName("a world change ends the operator binding and runs the stop hook, in order")
    void operatorAndHookOnWorldChange() {
        final List<String> hooks = new java.util.ArrayList<String>();
        SessionManager.setStopHook(new Runnable() {
            @Override
            public void run() {
                hooks.add("stopped");
            }
        });
        try {
            WorldChangeListener.loaded(A, CONTEXT);
            SessionManager.start("cyc4", CONTEXT);
            SessionManager.bindOperator(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "Dev", CONTEXT);
            ToolkitLog.startCaptureForTesting();

            WorldChangeListener.loaded(B, CONTEXT);

            List<String> lines = ToolkitLog.capturedForTesting();
            assertEquals(3, lines.size(), String.valueOf(lines));
            assertTrue(lines.get(0).contains("[OPERATOR]") && lines.get(0).contains("action=unbound"), lines.get(0));
            assertTrue(lines.get(1).contains("[SESSION_STOP]"), lines.get(1));
            assertTrue(lines.get(2).contains("[WORLD]"), lines.get(2));
            assertEquals(java.util.Arrays.asList("stopped"), hooks, "checkpoints are forgotten too");
        } finally {
            SessionManager.setStopHook(null);
        }
    }

    @Test
    @DisplayName("the world-load handler runs at the highest priority, before the scenario check")
    void highestPriority() throws Exception {
        net.minecraftforge.fml.common.eventhandler.SubscribeEvent annotation = WorldChangeListener.class
                .getMethod("onWorldLoad", net.minecraftforge.event.world.WorldEvent.Load.class)
                .getAnnotation(net.minecraftforge.fml.common.eventhandler.SubscribeEvent.class);
        assertEquals(net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST, annotation.priority());
    }

    @Test
    @DisplayName("with no session, a world load only records the world")
    void noSession() {
        WorldChangeListener.loaded(A, CONTEXT);

        List<String> lines = ToolkitLog.capturedForTesting();
        assertEquals(1, lines.size());
        assertFalse(lines.get(0).contains("sameAsSession"), lines.get(0));
    }

    @Test
    @DisplayName("a session started before any world loaded is kept when one loads")
    void sessionWithoutWorldKept() {
        SessionManager.start("early", CONTEXT);
        WorldChangeListener.loaded(B, CONTEXT);

        assertTrue(ToolkitState.hasActiveSession());
    }
}
