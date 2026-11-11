package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A cleared handle still names what it removed, for the placeholder only, until the session ends. */
class ClearedFixtureTest {

    private static final String UUID_A = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String UUID_B = "bbbbbbbb-0000-0000-0000-000000000002";

    @BeforeEach
    @AfterEach
    void reset() {
        ToolkitState.resetForTesting();
    }

    @Test
    @DisplayName("after a clear the placeholder resolves, while the handle is no longer claimed")
    void clearedStillNamed() {
        ToolkitState.putFixture("pig", UUID_A);
        ToolkitState.clearFixture("pig");
        assertNull(ToolkitState.getFixtureUuid("pig"), "every fixture action sees it as unknown");
        assertEquals(UUID_A, new StatePlaceholderSource().fixtureUuid("pig"));
    }

    @Test
    @DisplayName("a released handle is not kept: its entity is still alive")
    void releasedNotKept() {
        ToolkitState.putFixture("pig", UUID_A);
        ToolkitState.removeFixture("pig");
        assertNull(new StatePlaceholderSource().fixtureUuid("pig"));
    }

    @Test
    @DisplayName("claiming the handle again names the new entity, and forgets the cleared one")
    void reclaimed() {
        ToolkitState.putFixture("pig", UUID_A);
        ToolkitState.clearFixture("pig");
        ToolkitState.putFixture("pig", UUID_B);
        assertEquals(UUID_B, new StatePlaceholderSource().fixtureUuid("pig"));
        ToolkitState.removeFixture("pig");
        assertNull(new StatePlaceholderSource().fixtureUuid("pig"),
                "released after the new claim: the earlier cleared UUID must not come back");
    }

    @Test
    @DisplayName("the session's end forgets cleared handles too")
    void sessionEnd() {
        ToolkitState.putFixture("pig", UUID_A);
        ToolkitState.clearFixture("pig");
        ToolkitState.clearFixtures();
        assertNull(new StatePlaceholderSource().fixtureUuid("pig"));
    }
}
