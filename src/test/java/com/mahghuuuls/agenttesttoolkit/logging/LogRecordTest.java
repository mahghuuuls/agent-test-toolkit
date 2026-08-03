package com.mahghuuuls.agenttesttoolkit.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the REQ-033 formatting rules.
 *
 * <p>These matter more than usual on this project. The agent cannot run Gradle or launch the
 * game, so logic reachable without a running Minecraft is the only evidence available that
 * does not cost the owner a launch cycle. The record format is also the product: an agent
 * parses it, so a formatting regression is a functional defect rather than a cosmetic one.
 *
 * <p>Control characters are built from numeric values rather than written as literals or
 * unicode escapes. Java translates unicode escapes before parsing, so a {@code \\u2028} in
 * source would become a real line terminator and fail to compile.
 */
class LogRecordTest {

    private static final char NUL = 0x00;
    private static final char BELL = 0x07;
    private static final char VERTICAL_TAB = 0x0B;
    private static final char FORM_FEED = 0x0C;
    private static final char LINE_SEPARATOR = 0x2028;
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private static String withChar(String before, char c, String after) {
        return before + c + after;
    }

    @Test
    @DisplayName("renders the fixed prefix and event type")
    void rendersPrefixAndEventType() {
        assertEquals("[DevToolkit][MARK]", LogRecord.of(EventType.MARK).render());
    }

    @Test
    @DisplayName("renders fields as key=value in insertion order")
    void rendersFieldsInInsertionOrder() {
        String out = LogRecord.of(EventType.MARK)
                .add("alpha", "1")
                .add("beta", "2")
                .add("gamma", "3")
                .render();
        assertEquals("[DevToolkit][MARK] alpha=1 beta=2 gamma=3", out);
    }

    @Test
    @DisplayName("field order is stable across records of the same event type")
    void fieldOrderIsStable() {
        String first = LogRecord.of(EventType.MARK).add("a", "1").add("b", "2").render();
        String second = LogRecord.of(EventType.MARK).add("a", "9").add("b", "8").render();
        assertEquals(first.indexOf("a="), second.indexOf("a="));
        assertEquals(first.indexOf("b="), second.indexOf("b="));
    }

    @Test
    @DisplayName("quotes a value containing whitespace")
    void quotesWhitespaceValues() {
        assertEquals("[DevToolkit][MARK] label=\"BEFORE CAST\"",
                LogRecord.of(EventType.MARK).add("label", "BEFORE CAST").render());
    }

    @Test
    @DisplayName("leaves a value without whitespace unquoted")
    void leavesPlainValuesUnquoted() {
        assertEquals("[DevToolkit][MARK] label=BEFORE_CAST",
                LogRecord.of(EventType.MARK).add("label", "BEFORE_CAST").render());
    }

    @Test
    @DisplayName("escapes quote and backslash characters inside a quoted value")
    void escapesQuotesAndBackslashes() {
        assertEquals("[DevToolkit][MARK] label=\"say \\\"hi\\\" now\"",
                LogRecord.of(EventType.MARK).add("label", "say \"hi\" now").render());
        assertEquals("[DevToolkit][MARK] label=\"a\\\\b c\"",
                LogRecord.of(EventType.MARK).add("label", "a\\b c").render());
    }

    @Test
    @DisplayName("quotes a value that contains a quote even without whitespace")
    void quotesEmbeddedQuoteWithoutWhitespace() {
        assertEquals("[DevToolkit][MARK] label=\"a\\\"b\"",
                LogRecord.of(EventType.MARK).add("label", "a\"b").render());
    }

    @Test
    @DisplayName("omits an absent optional field rather than emitting a placeholder")
    void omitsAbsentFields() {
        String out = LogRecord.of(EventType.MARK)
                .add("present", "yes")
                .add("nullValue", (String) null)
                .add("emptyValue", "")
                .render();
        assertEquals("[DevToolkit][MARK] present=yes", out);
        assertFalse(out.contains("nullValue"));
        assertFalse(out.contains("emptyValue"));
    }

    @Test
    @DisplayName("renders block coordinates as integers")
    void rendersBlockCoordinatesAsIntegers() {
        assertEquals("[DevToolkit][BLOCK_PLACE] posX=10 posY=-4 posZ=300",
                LogRecord.of(EventType.BLOCK_PLACE).addBlockPos("pos", 10, -4, 300).render());
    }

    @Test
    @DisplayName("renders entity positions to two decimal places")
    void rendersEntityPositionsToTwoDecimals() {
        String out = LogRecord.of(EventType.ENTITY_SPAWN).addEntityPos("pos", 1.0, -2.5, 3.0).render();
        assertTrue(out.contains("posX=1.00"), out);
        assertTrue(out.contains("posY=-2.50"), out);
        assertTrue(out.contains("posZ=3.00"), out);
    }

    @Test
    @DisplayName("decimal formatting is locale independent")
    void decimalFormattingIsLocaleIndependent() {
        // A locale using a decimal comma would otherwise emit "1,50" and corrupt the record
        // for any parser splitting on the separator. This is why Locale.ROOT is pinned.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1.50", LogRecord.formatDecimal(1.5));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("re-adding a key overwrites the value and keeps its position")
    void reAddingKeyKeepsPosition() {
        assertEquals("[DevToolkit][MARK] a=9 b=2",
                LogRecord.of(EventType.MARK).add("a", "1").add("b", "2").add("a", "9").render());
    }

    @Test
    @DisplayName("every event type renders in the bracketed prefix form")
    void everyEventTypeRendersConsistently() {
        for (EventType type : EventType.values()) {
            assertEquals("[DevToolkit][" + type.name() + "]", LogRecord.of(type).render());
        }
    }

    @Test
    @DisplayName("rejects a null event type and an empty field key")
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(null));
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(EventType.MARK).add("", "v"));
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(EventType.MARK).add(null, "v"));
    }

    @Test
    @DisplayName("rejects a field key that is not camelCase")
    void rejectsNonCamelCaseKeys() {
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(EventType.MARK).add("Label", "v"));
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(EventType.MARK).add("health_before", "v"));
        assertThrows(IllegalArgumentException.class, () -> LogRecord.of(EventType.MARK).add("health-before", "v"));
        assertEquals("[DevToolkit][MARK] slot0=x",
                LogRecord.of(EventType.MARK).add("slot0", "x").render());
    }

    // --- Control character handling -------------------------------------------------
    // Covers the defect found in independent review: a value containing a newline was quoted
    // but not escaped, so it split the record across two physical lines and broke REQ-032's
    // one-event-one-line guarantee. Quoting alone does not help, because a quoted newline is
    // still a newline. Reachable from command blocks, RCON, and bundle files.

    @Test
    @DisplayName("a rendered record never contains a line break, whatever the value")
    void renderedRecordIsAlwaysASingleLine() {
        String[] hostile = {
                withChar("line1", '\n', "line2"),
                withChar("carriage", '\r', "return"),
                withChar("tab", '\t', "separated"),
                withChar("form", FORM_FEED, "feed"),
                withChar("nul", NUL, "byte"),
                withChar("vertical", VERTICAL_TAB, "tab"),
                withChar("line", LINE_SEPARATOR, "separator"),
                withChar("paragraph", PARAGRAPH_SEPARATOR, "separator")
        };
        for (String value : hostile) {
            String out = LogRecord.of(EventType.MARK).add("label", value).render();
            assertFalse(out.indexOf('\n') >= 0, "record contained a newline");
            assertFalse(out.indexOf('\r') >= 0, "record contained a carriage return");
            assertFalse(out.indexOf(LINE_SEPARATOR) >= 0, "record contained U+2028");
            assertFalse(out.indexOf(PARAGRAPH_SEPARATOR) >= 0, "record contained U+2029");
        }
    }

    @Test
    @DisplayName("escapes newline, carriage return and tab readably")
    void escapesCommonControlCharacters() {
        assertEquals("[DevToolkit][MARK] label=\"line1\\nline2\"",
                LogRecord.of(EventType.MARK).add("label", withChar("line1", '\n', "line2")).render());
        assertEquals("[DevToolkit][MARK] label=\"a\\rb\"",
                LogRecord.of(EventType.MARK).add("label", withChar("a", '\r', "b")).render());
        assertEquals("[DevToolkit][MARK] label=\"a\\tb\"",
                LogRecord.of(EventType.MARK).add("label", withChar("a", '\t', "b")).render());
    }

    @Test
    @DisplayName("escapes any remaining control character as a unicode escape")
    void escapesOtherControlCharactersAsUnicode() {
        assertEquals("[DevToolkit][MARK] label=\"a\\u0000b\"",
                LogRecord.of(EventType.MARK).add("label", withChar("a", NUL, "b")).render());
        assertEquals("[DevToolkit][MARK] label=\"a\\u0007b\"",
                LogRecord.of(EventType.MARK).add("label", withChar("a", BELL, "b")).render());
        assertEquals("[DevToolkit][MARK] label=\"a\\u2028b\"",
                LogRecord.of(EventType.MARK).add("label", withChar("a", LINE_SEPARATOR, "b")).render());
    }

    @Test
    @DisplayName("quotes a whitespace-only value rather than dropping it")
    void quotesWhitespaceOnlyValue() {
        // Not empty, so it is not omitted. It must survive as a visible quoted value rather
        // than an unparseable bare run of spaces.
        assertEquals("[DevToolkit][MARK] label=\"   \"",
                LogRecord.of(EventType.MARK).add("label", "   ").render());
    }

    @Test
    @DisplayName("leaves ordinary non-ASCII text intact and unquoted")
    void leavesOrdinaryUnicodeAlone() {
        // Accented and non-Latin characters are not control characters and must not be
        // mangled into escapes, which would make player names and registry names unreadable.
        String accented = withChar("caf", (char) 0x00E9, "");
        assertEquals("[DevToolkit][MARK] label=" + accented,
                LogRecord.of(EventType.MARK).add("label", accented).render());
    }
}
