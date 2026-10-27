package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The RELOAD record says what triggered it: a typed command or a server start. */
class ReloadRecordTest {

    private static final RecordContext.Snapshot CONTEXT =
            new RecordContext.Snapshot(RecordContext.SERVER, 12L);

    @BeforeEach
    void setUp() {
        ToolkitState.resetForTesting();
    }

    @AfterEach
    void tearDown() {
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("a server-start reload and a typed one write the same record but for the trigger")
    void trigger() {
        String start = ReloadSubCommand.record(CONTEXT, 150, 1, ReloadSubCommand.TRIGGER_SERVER_START).render();
        String typed = ReloadSubCommand.record(CONTEXT, 150, 1, ReloadSubCommand.TRIGGER_COMMAND).render();

        assertTrue(start.contains("[RELOAD]"), start);
        assertTrue(start.contains("bundles=150 problems=1 trigger=serverStart"), start);
        assertTrue(typed.contains("bundles=150 problems=1 trigger=command"), typed);
        assertTrue(start.replace("serverStart", "command").equals(typed), "nothing else differs");
    }
}
