package com.mahghuuuls.agenttesttoolkit.bundle;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which runs are quiet, and where their kept-back lines are counted. */
class QuietScopeTest {

    /** A run with a fixed quietness that counts what it is told. */
    static final class CountingRun implements QuietScope.Run {
        private final boolean quiet;
        int suppressed;

        CountingRun(boolean quiet) {
            this.quiet = quiet;
        }

        @Override
        public boolean isQuiet() {
            return quiet;
        }

        @Override
        public void noteSuppressed(int lines) {
            suppressed += lines;
        }
    }

    @AfterEach
    void leaveAll() {
        while (QuietScope.depth() > 0) {
            QuietScope.exit();
        }
    }

    @Test
    @DisplayName("a top-level run is quiet exactly when the inbox started it")
    void originDefault() {
        assertTrue(QuietScope.decide(null, null, RunOrigin.INBOX));
        assertFalse(QuietScope.decide(null, null, RunOrigin.CHAT));
        assertFalse(QuietScope.decide(null, null, RunOrigin.CONSOLE));
        assertFalse(QuietScope.decide(null, null, RunOrigin.JOIN));
    }

    @Test
    @DisplayName("the run's own word outranks the channel, either way")
    void ownWordOverridesOrigin() {
        assertFalse(QuietScope.decide(Boolean.FALSE, null, RunOrigin.INBOX));
        assertTrue(QuietScope.decide(Boolean.TRUE, null, RunOrigin.CHAT));
    }

    @Test
    @DisplayName("a nested run follows its parent unless it says otherwise")
    void nestedInherits() {
        assertTrue(QuietScope.decide(null, Boolean.TRUE, RunOrigin.NESTED));
        assertFalse(QuietScope.decide(null, Boolean.FALSE, RunOrigin.NESTED));
        assertFalse(QuietScope.decide(Boolean.FALSE, Boolean.TRUE, RunOrigin.NESTED));
        assertTrue(QuietScope.decide(Boolean.TRUE, Boolean.FALSE, RunOrigin.NESTED));
    }

    @Test
    @DisplayName("the innermost run decides, and leaving it gives the decision back")
    void depthAndInnermost() {
        CountingRun loud = new CountingRun(false);
        CountingRun quiet = new CountingRun(true);
        assertEquals(0, QuietScope.depth());
        assertFalse(QuietScope.isQuiet());

        QuietScope.enter(quiet);
        QuietScope.enter(loud);
        assertEquals(2, QuietScope.depth());
        assertFalse(QuietScope.isQuiet(), "a loud run entered inside a quiet one is loud");
        assertFalse(QuietScope.suppress(1));
        QuietScope.exit();

        assertTrue(QuietScope.isQuiet());
        assertTrue(QuietScope.suppress(2));
        assertTrue(QuietScope.suppress(1));
        QuietScope.exit();

        assertEquals(0, QuietScope.depth());
        assertFalse(QuietScope.isQuiet());
        assertFalse(QuietScope.suppress(1), "outside every run nothing is kept back");
        assertEquals(3, quiet.suppressed);
        assertEquals(0, loud.suppressed);
        QuietScope.exit();
        assertEquals(0, QuietScope.depth(), "leaving with nothing entered does nothing");
    }

    @Test
    @DisplayName("another thread is not inside this thread's quiet run")
    void perThread() throws Exception {
        QuietScope.enter(new CountingRun(true));
        final AtomicBoolean seenQuiet = new AtomicBoolean(true);
        Thread other = new Thread(new Runnable() {
            @Override
            public void run() {
                seenQuiet.set(QuietScope.isQuiet());
            }
        });
        other.start();
        other.join();
        assertFalse(seenQuiet.get());
        assertTrue(QuietScope.isQuiet());
    }
}
