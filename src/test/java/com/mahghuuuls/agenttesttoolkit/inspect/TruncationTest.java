package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NBT output bound.
 *
 * <p>Truncation is never silent, and the boundary is where that promise is easiest to
 * break invisibly. A dump one character short looks complete, and an agent comparing it against
 * expected contents would conclude the game is wrong rather than the log.
 */
class TruncationTest {

    private static String repeat(char c, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(c);
        }
        return out.toString();
    }

    @Test
    @DisplayName("text under the limit is untouched and not reported as truncated")
    void underLimitUntouched() {
        Truncation result = Truncation.of("abc", 10);
        assertEquals("abc", result.getText());
        assertEquals(3, result.getOriginalLength());
        assertEquals(3, result.getOutputLength());
        assertFalse(result.isTruncated());
    }

    @Test
    @DisplayName("text exactly at the limit is not truncated")
    void exactlyAtLimitNotTruncated() {
        // The limit is a maximum length, not an exclusive bound. Reporting truncation here
        // would be a lie about content that is in fact complete.
        Truncation result = Truncation.of("abcde", 5);
        assertEquals("abcde", result.getText());
        assertFalse(result.isTruncated());
        assertEquals(5, result.getOutputLength());
    }

    @Test
    @DisplayName("one character over the limit is truncated and says so")
    void oneOverIsTruncated() {
        Truncation result = Truncation.of("abcdef", 5);
        assertEquals("abcde", result.getText());
        assertTrue(result.isTruncated());
        assertEquals(6, result.getOriginalLength());
        assertEquals(5, result.getOutputLength());
    }

    @Test
    @DisplayName("the original length survives truncation, so the shortfall is knowable")
    void originalLengthReported() {
        // This is the field that tells an agent whether raising the configured limit would
        // recover the rest, which is the only useful response to a truncated dump.
        Truncation result = Truncation.of(repeat('x', 9000), 8192);
        assertEquals(9000, result.getOriginalLength());
        assertEquals(8192, result.getOutputLength());
        assertTrue(result.isTruncated());
    }

    @Test
    @DisplayName("empty and null are handled without a special case at the call site")
    void emptyAndNull() {
        assertEquals("", Truncation.of("", 10).getText());
        assertFalse(Truncation.of("", 10).isTruncated());
        assertEquals("", Truncation.of(null, 10).getText());
        assertEquals(0, Truncation.of(null, 10).getOriginalLength());
        assertFalse(Truncation.of(null, 10).isTruncated());
    }

    @Test
    @DisplayName("a limit below one is raised to one rather than producing nothing")
    void limitBelowOneIsRaised() {
        // A limit of zero would produce output that says nothing while still claiming to be a
        // dump. Configuration already rejects values below one; this is the second line of
        // defence rather than a substitute for it.
        Truncation result = Truncation.of("abc", 0);
        assertEquals("a", result.getText());
        assertTrue(result.isTruncated());
        assertEquals(3, result.getOriginalLength());
    }
}
