package com.mahghuuuls.agenttesttoolkit.fixture;

import com.mahghuuuls.agenttesttoolkit.fixture.FixtureRemovals.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a removal's record says once it is checked (REQ-210). */
class FixtureRemovalsTest {

    @Test
    @DisplayName("a clear in chat is checked at the end of its own tick, one from a bundle step at the end of the next")
    void dueByWorldClock() {
        // Chat: the clear runs at the start of server tick 100, world time still 500.
        assertFalse(FixtureRemovals.isDue(500L, 500L, 99, 99), "nothing has run yet");
        assertTrue(FixtureRemovals.isDue(501L, 500L, 100, 100), "the world updated in this same tick");
        // Bundle step: at the end of tick 100, the world already at 501.
        assertFalse(FixtureRemovals.isDue(501L, 501L, 100, 100), "the update of this tick came before it");
        assertTrue(FixtureRemovals.isDue(502L, 501L, 101, 100));
        // A world that unloaded: two server ticks are enough.
        assertFalse(FixtureRemovals.isDue(-1L, 501L, 101, 100));
        assertTrue(FixtureRemovals.isDue(-1L, 501L, 102, 100));
    }

    @Test
    @DisplayName("not found is removed")
    void gone() {
        assertEquals(Outcome.REMOVED, FixtureRemovals.outcome(false, false, false));
    }

    @Test
    @DisplayName("a new entity under the same UUID is not the removed one: removed")
    void newEntitySameUuid() {
        assertEquals(Outcome.REMOVED, FixtureRemovals.outcome(true, false, false));
    }

    @Test
    @DisplayName("the same entity, dead, in a world no longer updating entities: removed, unload pending")
    void deadButListed() {
        assertEquals(Outcome.REMOVED_UNLOAD_PENDING, FixtureRemovals.outcome(true, true, true));
    }

    @Test
    @DisplayName("the same entity, alive: failed, still alive")
    void broughtBack() {
        assertEquals(Outcome.STILL_ALIVE, FixtureRemovals.outcome(true, true, false));
    }
}
