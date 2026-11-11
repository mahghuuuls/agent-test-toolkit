package com.mahghuuuls.agenttesttoolkit.inspect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a block search looks for: a block, and optionally some of its state's properties.
 *
 * <p>Pure: it compares names and values as text, the way a blockstate is written
 * ({@code facing=north,half=top}), so the parsing, the check against what the block can take,
 * and the match are unit-tested without a game.
 */
public final class BlockMatch {

    private BlockMatch() {
    }

    /**
     * Parses {@code name=value,name=value}. Null when the text is not that form, or names one
     * property twice.
     */
    public static Map<String, String> parseState(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Map<String, String> wanted = new LinkedHashMap<String, String>();
        for (String pair : text.split(",", -1)) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1 || pair.indexOf('=', eq + 1) >= 0) {
                return null;
            }
            if (wanted.put(pair.substring(0, eq), pair.substring(eq + 1)) != null) {
                return null;
            }
        }
        return Collections.unmodifiableMap(wanted);
    }

    /**
     * The first wanted property the block does not have, or whose value it cannot take, as
     * {@code name=value}; null when every wanted property is possible.
     *
     * @param allowed each property of the block with the names of the values it can take
     */
    public static String firstImpossible(Map<String, List<String>> allowed, Map<String, String> wanted) {
        for (Map.Entry<String, String> entry : wanted.entrySet()) {
            List<String> values = allowed.get(entry.getKey());
            if (values == null || !values.contains(entry.getValue())) {
                return entry.getKey() + "=" + entry.getValue();
            }
        }
        return null;
    }

    /**
     * Whether one state stores a property: some other value of it would give that state
     * another metadata. A door's upper half stores its hinge but not its facing, so its facing
     * always reads the default and says nothing about the door.
     *
     * @param metaNow the state's metadata
     * @param metaPerValue the metadata the state would have with each value of the property
     */
    public static boolean isStoredAt(int metaNow, int[] metaPerValue) {
        for (int meta : metaPerValue) {
            if (meta != metaNow) {
                return true;
            }
        }
        return false;
    }

    /** Whether a state with these property values has every wanted one. */
    public static boolean matches(Map<String, String> wanted, Map<String, String> actual) {
        for (Map.Entry<String, String> entry : wanted.entrySet()) {
            if (!entry.getValue().equals(actual.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
