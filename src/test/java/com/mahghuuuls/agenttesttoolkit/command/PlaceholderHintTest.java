package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderException;
import com.mahghuuuls.agenttesttoolkit.bundle.Placeholders;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An unbound placeholder's message names the step that is missing; its key does not change. */
class PlaceholderHintTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ToolkitState.resetForTesting();
    }

    private static PlaceholderException unbound(String command) {
        return assertThrows(PlaceholderException.class,
                () -> Placeholders.substitute(command, new StatePlaceholderSource()));
    }

    @Test
    @DisplayName("with no session, every placeholder names devtool session start")
    void noSession() {
        PlaceholderException operator = unbound("gamemode creative ${operator.name}");
        assertEquals(PlaceholderException.KEY_UNBOUND, operator.getKey());
        assertTrue(operator.getMessage().contains("devtool session start <name>"), operator.getMessage());
        PlaceholderException fixture = unbound("kill ${fixture.cow.uuid}");
        assertTrue(fixture.getMessage().contains("devtool session start <name>"), fixture.getMessage());
    }

    @Test
    @DisplayName("with a session and no operator, an operator placeholder names devtool session operator")
    void noOperator() {
        ToolkitState.setActiveSession(new DiagnosticSession("hints"));
        PlaceholderException e = unbound("tp ${operator.selector} 0 64 0");
        assertEquals(PlaceholderException.KEY_UNBOUND, e.getKey());
        assertTrue(e.getMessage().contains("devtool session operator <player>"), e.getMessage());
    }

    @Test
    @DisplayName("with a session, an unclaimed handle names devtool fixture claim with that handle")
    void unclaimedHandle() {
        ToolkitState.setActiveSession(new DiagnosticSession("hints"));
        ToolkitState.setOperator(new OperatorBinding(UUID.randomUUID(), "Dev"));
        PlaceholderException e = unbound("kill ${fixture.cow.uuid}");
        assertTrue(e.getMessage().contains("devtool fixture claim cow "), e.getMessage());
        assertEquals("Dev", new StatePlaceholderSource().operatorName());
    }

    @Test
    @DisplayName("a source without hints keeps the earlier message")
    void noHint() {
        assertNull(com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource.EMPTY.unboundHint("operator.name"));
        PlaceholderException e = assertThrows(PlaceholderException.class, () -> Placeholders.substitute(
                "say ${operator.name}", com.mahghuuuls.agenttesttoolkit.bundle.PlaceholderSource.EMPTY));
        assertEquals("Placeholder ${operator.name} has no value: nothing is bound for it.", e.getMessage());
    }
}
