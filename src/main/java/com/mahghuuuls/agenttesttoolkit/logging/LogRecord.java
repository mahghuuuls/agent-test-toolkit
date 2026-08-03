package com.mahghuuuls.agenttesttoolkit.logging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds one structured toolkit record.
 *
 * <p>This class owns every formatting rule in REQ-033, which is why nothing else in the
 * project is permitted to format a record by hand. The rules are:
 *
 * <ul>
 *   <li>Field names are camelCase.</li>
 *   <li>Field order is insertion order, so a given event type always renders its fields
 *       in the same sequence.</li>
 *   <li>A value containing whitespace is quoted, and quote characters inside it are escaped.</li>
 *   <li>An absent optional value is omitted entirely rather than rendered as a placeholder.
 *       An empty or null value is treated as absent.</li>
 *   <li>Block coordinates render as integers, entity positions to two decimal places.</li>
 * </ul>
 *
 * <p>The rendered form is <code>[DevToolkit][EVENT_TYPE] key=value key=value</code> on a
 * single line, per REQ-031 and REQ-032.
 *
 * <p>Deliberately free of any Minecraft type so the formatting rules can be unit tested
 * without a running game. This matters more than usual here: the owner runs every build
 * and launch, so logic reachable without the game is the cheapest evidence available.
 */
public final class LogRecord {

    private static final String PREFIX = "[DevToolkit]";

    /** Unicode LINE SEPARATOR, treated as a line break by some tooling. */
    private static final char LINE_SEPARATOR = 0x2028;
    /** Unicode PARAGRAPH SEPARATOR, likewise. */
    private static final char PARAGRAPH_SEPARATOR = 0x2029;

    private final EventType eventType;
    private final Map<String, String> fields = new LinkedHashMap<String, String>();

    private LogRecord(EventType eventType) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        this.eventType = eventType;
    }

    public static LogRecord of(EventType eventType) {
        return new LogRecord(eventType);
    }

    /**
     * Adds a field. A null or empty value is ignored, satisfying the omit-rather-than-placeholder
     * rule. Re-adding an existing key overwrites the value and keeps the original position.
     */
    public LogRecord add(String key, String value) {
        requireCamelCaseKey(key);
        if (value == null || value.isEmpty()) {
            return this;
        }
        fields.put(key, value);
        return this;
    }

    /**
     * REQ-033 requires camelCase field names. Centralising the check here keeps the rule
     * enforceable rather than conventional, which is the whole justification for routing
     * every record through this class. Keys are internal literals, so a violation is a
     * programming error and failing fast is correct.
     */
    private static void requireCamelCaseKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("field key must not be null or empty");
        }
        if (!Character.isLowerCase(key.charAt(0))) {
            throw new IllegalArgumentException("field key must start lowercase (camelCase): " + key);
        }
        for (int i = 0; i < key.length(); i++) {
            if (!Character.isLetterOrDigit(key.charAt(i))) {
                throw new IllegalArgumentException("field key must be alphanumeric camelCase: " + key);
            }
        }
    }

    public LogRecord add(String key, long value) {
        return add(key, Long.toString(value));
    }

    public LogRecord add(String key, boolean value) {
        return add(key, Boolean.toString(value));
    }

    /**
     * Adds a floating point value rendered to two decimal places, the convention REQ-033
     * fixes for entity positions and damage amounts.
     */
    public LogRecord addDecimal(String key, double value) {
        return add(key, formatDecimal(value));
    }

    /**
     * Adds a block position as three integer fields. Block coordinates are integers by
     * REQ-033, so they must not go through {@link #addDecimal}.
     */
    public LogRecord addBlockPos(String prefix, int x, int y, int z) {
        add(prefix + "X", x);
        add(prefix + "Y", y);
        add(prefix + "Z", z);
        return this;
    }

    /**
     * Adds an entity position as three two-decimal fields.
     */
    public LogRecord addEntityPos(String prefix, double x, double y, double z) {
        addDecimal(prefix + "X", x);
        addDecimal(prefix + "Y", y);
        addDecimal(prefix + "Z", z);
        return this;
    }

    public String render() {
        StringBuilder out = new StringBuilder(PREFIX);
        out.append('[').append(eventType.name()).append(']');
        for (Map.Entry<String, String> field : fields.entrySet()) {
            out.append(' ').append(field.getKey()).append('=').append(quoteIfNeeded(field.getValue()));
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return render();
    }

    static String formatDecimal(double value) {
        // String.format is locale sensitive and would emit a comma separator in some locales,
        // which would corrupt a machine readable record. Locale.ROOT is not optional here.
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    static String quoteIfNeeded(String value) {
        if (!needsQuoting(value)) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            appendEscaped(out, value.charAt(i));
        }
        out.append('"');
        return out.toString();
    }

    /**
     * Escapes one character into the quoted form.
     *
     * <p>Control characters must never be emitted literally. A raw newline would split the
     * record across two physical lines, breaking the one-event-one-line guarantee in REQ-032
     * and the greppability the whole log format depends on. Quoting alone does not prevent
     * that, because a quoted newline is still a newline.
     *
     * <p>This is reachable without a malicious user: command blocks, RCON, and bundle files
     * can all carry a value that chat input never could.
     */
    private static void appendEscaped(StringBuilder out, char c) {
        switch (c) {
            case '"':
                out.append("\\\"");
                return;
            case '\\':
                out.append("\\\\");
                return;
            case '\n':
                out.append("\\n");
                return;
            case '\r':
                out.append("\\r");
                return;
            case '\t':
                out.append("\\t");
                return;
            default:
                break;
        }
        if (isControl(c)) {
            // Any remaining control character becomes a unicode escape rather than being
            // emitted raw, so no unprintable byte can reach the log.
            out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
            return;
        }
        out.append(c);
    }

    private static boolean needsQuoting(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || isControl(c) || c == '"' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    /**
     * True for characters that must not appear literally in a record. Deliberately broader
     * than {@link Character#isISOControl}: it also covers the Unicode line and paragraph
     * separators, which some tooling treats as line breaks.
     */
    private static boolean isControl(char c) {
        // Compared numerically on purpose. Writing these as unicode escapes in a char
        // literal would not compile: Java translates unicode escapes before parsing, so
        // the escape would become an actual line terminator inside the source file.
        return Character.isISOControl(c) || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR;
    }
}
