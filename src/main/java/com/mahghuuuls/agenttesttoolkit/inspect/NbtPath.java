package com.mahghuuuls.agenttesttoolkit.inspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A path to one value inside saved NBT: keys separated by dots, and list indexes in brackets.
 *
 * <pre>
 * Inventory[0].id
 * tag.ench[0].lvl
 * ForgeCaps."jawms:player_mana".currentMana
 * ForgeCaps.jawms:player_mana.currentMana      (the same path: a colon needs no quotes)
 * </pre>
 *
 * <p>A key is written bare unless it holds a dot, a bracket, a double quote or whitespace; then
 * it is written in double quotes, with {@code \"} for a quote and {@code \\} for a backslash
 * inside them. A colon is allowed bare because capability and registry ids are the keys most
 * paths go through, and every one of them has a colon; quoting them is allowed, never required.
 * An index follows a key or another index ({@code a[0][1]}); a path never starts with one, since
 * what it is read from is always a compound.
 *
 * <p>Free of game types, so the grammar is tested without the game; resolving a path over a
 * compound is {@link NbtLookup}'s.
 */
public final class NbtPath {

    /** One step of a path: a compound key, or a list index. */
    public static final class Segment {
        private final String key;
        private final int index;

        private Segment(String key, int index) {
            this.key = key;
            this.index = index;
        }

        /** True for {@code [n]}, false for a key. */
        public boolean isIndex() {
            return key == null;
        }

        /** The key, without quotes; null for an index. */
        public String getKey() {
            return key;
        }

        /** The index; -1 for a key. */
        public int getIndex() {
            return index;
        }
    }

    /** At most nine digits, so an index always fits an int. */
    private static final int MAX_INDEX_DIGITS = 9;

    private final String text;
    private final List<Segment> segments;
    /** Where each segment ends in {@link #text}, so a prefix is shown exactly as it was typed. */
    private final int[] ends;

    private NbtPath(String text, List<Segment> segments, int[] ends) {
        this.text = text;
        this.segments = Collections.unmodifiableList(segments);
        this.ends = ends;
    }

    /**
     * Reads a path.
     *
     * @throws IllegalArgumentException with a message naming what is wrong and where
     */
    public static NbtPath parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("the path is empty");
        }
        List<Segment> segments = new ArrayList<Segment>();
        List<Integer> ends = new ArrayList<Integer>();
        int i = 0;
        boolean expectKey = true;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (expectKey) {
                StringBuilder key = new StringBuilder();
                if (c == '"') {
                    i = readQuoted(text, i, key);
                } else {
                    int start = i;
                    while (i < text.length() && !isSpecial(text.charAt(i))) {
                        key.append(text.charAt(i));
                        i++;
                    }
                    if (i == start) {
                        throw new IllegalArgumentException(i < text.length()
                                ? "expected a key at character " + (i + 1) + " but found '" + text.charAt(i) + "'"
                                : "expected a key at the end of the path");
                    }
                }
                segments.add(new Segment(key.toString(), -1));
                ends.add(Integer.valueOf(i));
                expectKey = false;
            } else if (c == '.') {
                i++;
                expectKey = true;
                if (i == text.length()) {
                    throw new IllegalArgumentException("the path ends with a dot");
                }
            } else if (c == '[') {
                int close = text.indexOf(']', i);
                if (close < 0) {
                    throw new IllegalArgumentException("'[' at character " + (i + 1) + " is never closed");
                }
                String digits = text.substring(i + 1, close);
                if (digits.isEmpty() || digits.length() > MAX_INDEX_DIGITS || !allDigits(digits)) {
                    throw new IllegalArgumentException("'[" + digits + "]' is not an index; an index is a whole number of 0 or more");
                }
                segments.add(new Segment(null, Integer.parseInt(digits)));
                i = close + 1;
                ends.add(Integer.valueOf(i));
            } else {
                throw new IllegalArgumentException("expected '.' or '[' at character " + (i + 1) + " but found '" + c
                        + "'; a key with a dot, a bracket, a quote or a space is written in double quotes");
            }
        }
        int[] endArray = new int[ends.size()];
        for (int k = 0; k < endArray.length; k++) {
            endArray[k] = ends.get(k).intValue();
        }
        return new NbtPath(text, segments, endArray);
    }

    /**
     * Where a path written at the start of {@code text} ends: the first whitespace outside double
     * quotes, or the length of the text. Lets a command take a path and then a value from one line
     * even when a quoted key holds a space.
     */
    public static int end(String text) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted && c == '\\' && i + 1 < text.length()) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                return i;
            }
        }
        return text.length();
    }

    /** The steps, in order. Never empty. */
    public List<Segment> getSegments() {
        return segments;
    }

    /** The first {@code count} steps as they were typed, such as {@code tag.ench[0]}. */
    public String prefix(int count) {
        if (count <= 0) {
            return "";
        }
        return text.substring(0, ends[Math.min(count, ends.length) - 1]);
    }

    /** The path as it was typed. */
    @Override
    public String toString() {
        return text;
    }

    private static int readQuoted(String text, int open, StringBuilder key) {
        int i = open + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()
                    && (text.charAt(i + 1) == '"' || text.charAt(i + 1) == '\\')) {
                key.append(text.charAt(i + 1));
                i += 2;
            } else if (c == '"') {
                return i + 1;
            } else {
                key.append(c);
                i++;
            }
        }
        throw new IllegalArgumentException("the quote at character " + (open + 1) + " is never closed");
    }

    private static boolean isSpecial(char c) {
        return c == '.' || c == '[' || c == ']' || c == '"' || Character.isWhitespace(c);
    }

    private static boolean allDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
