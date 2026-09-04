package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparison rules behind a verdict.
 *
 * <p>The one that matters most is the last: an entity that is not among loaded entities is
 * only absent when the place it should be is loaded. Reporting absent for an unloaded chunk
 * would let a bundle prove something the game never checked.
 */
class AssertionsTest {

    @Test
    @DisplayName("a count passes only on the exact number, and carries both values")
    void counts() {
        Assertions.Result pass = Assertions.count(1, 1);
        assertTrue(pass.passed());
        assertEquals("1", pass.getExpected());
        assertEquals("1", pass.getObserved());
        assertNull(pass.failureKey());

        Assertions.Result fail = Assertions.count(2, 1);
        assertEquals(Assertions.Outcome.FAIL, fail.getOutcome());
        assertEquals("2", fail.getExpected());
        assertEquals("1", fail.getObserved());
        assertEquals(Assertions.KEY_FAILED, fail.failureKey());
    }

    @Test
    @DisplayName("text comparison is exact and shows none for a missing value")
    void text() {
        assertTrue(Assertions.equalsText("creative", "creative").passed());
        Assertions.Result fail = Assertions.equalsText("0", "-1");
        assertEquals(Assertions.Outcome.FAIL, fail.getOutcome());
        assertEquals("-1", fail.getObserved());
        assertEquals("none", Assertions.equalsText("0", null).getObserved());
    }

    @Test
    @DisplayName("a radius passes at the boundary and fails just beyond it")
    void radius() {
        assertTrue(Assertions.withinRadius(5.0, 5.0).passed());
        Assertions.Result fail = Assertions.withinRadius(5.0, 5.01);
        assertEquals(Assertions.Outcome.FAIL, fail.getOutcome());
        assertEquals("within 5.00", fail.getExpected());
        assertEquals("5.01", fail.getObserved());
    }

    @Test
    @DisplayName("present passes when found and fails when not, whatever the region state")
    void presence() {
        assertTrue(Assertions.presence(true, true, null).passed());
        assertEquals(Assertions.Outcome.FAIL, Assertions.presence(false, true, Boolean.TRUE).getOutcome());
        Assertions.Result notFoundLoaded = Assertions.presence(true, false, Boolean.TRUE);
        assertEquals(Assertions.Outcome.FAIL, notFoundLoaded.getOutcome());
        assertEquals("absent", notFoundLoaded.getObserved());
    }

    @Test
    @DisplayName("absent passes only when the region is loaded; otherwise the outcome is unloaded")
    void absenceNeedsALoadedRegion() {
        Assertions.Result absent = Assertions.presence(false, false, Boolean.TRUE);
        assertTrue(absent.passed());
        assertEquals("absent", absent.getObserved());

        Assertions.Result unloaded = Assertions.presence(false, false, Boolean.FALSE);
        assertEquals(Assertions.Outcome.UNLOADED, unloaded.getOutcome());
        assertEquals("notFound", unloaded.getObserved());
        assertEquals(Assertions.KEY_UNLOADED, unloaded.failureKey());

        Assertions.Result unknown = Assertions.presence(false, false, null);
        assertEquals(Assertions.Outcome.UNLOADED, unknown.getOutcome());
        assertTrue(unknown.getNote().contains("no position"), unknown.getNote());
    }
}
