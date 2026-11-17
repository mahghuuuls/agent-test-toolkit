package com.mahghuuuls.agenttesttoolkit.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a key binding by its registered name, and lists bindings, from plain values, so the
 * lookup's refusals and the duplicate marks are tested without a game.
 *
 * <p>A binding's name is its description, such as {@code key.jump}. Two bindings may share one:
 * the game then keeps only the later one in its name map, so a lookup scans every binding and
 * refuses a name that matches more than one rather than picking either.
 */
final class KeyLookup {

    /** No registered binding has the name. */
    static final String REASON_UNKNOWN_KEY = "unknownKey";
    /** More than one registered binding has the name. */
    static final String REASON_AMBIGUOUS_KEY = "ambiguousKey";
    /** At most this many bindings are listed by one request. */
    static final int MAX_LISTED = 300;

    /** One binding, as the controls screen shows it. */
    static final class Entry {
        final String name;
        final String category;
        final int keyCode;
        /** The key's name as the controls screen shows it, with its modifier. */
        final String keyName;
        /** {@code NONE}, {@code SHIFT}, {@code CONTROL} or {@code ALT}. */
        final String modifier;
        /** Where the binding acts: {@code IN_GAME}, {@code GUI}, {@code UNIVERSAL}, or a mod's own. */
        final String context;

        Entry(String name, String category, int keyCode, String keyName, String modifier, String context) {
            this.name = name;
            this.category = category;
            this.keyCode = keyCode;
            this.keyName = keyName;
            this.modifier = modifier;
            this.context = context;
        }
    }

    /** The binding a name found, by its place in the list, or why none. */
    static final class Found {
        /** The place in the list; -1 when refused. */
        final int index;
        final String refusal;

        private Found(int index, String refusal) {
            this.index = index;
            this.refusal = refusal;
        }
    }

    /** One listed binding, and whether its name is shared. */
    static final class Listed {
        final Entry entry;
        final boolean duplicate;

        Listed(Entry entry, boolean duplicate) {
            this.entry = entry;
            this.duplicate = duplicate;
        }
    }

    private KeyLookup() {
    }

    /** The one binding with this exact name; refused when none, several, or one without a key. */
    static Found find(List<Entry> entries, String name) {
        int match = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name.equals(name)) {
                if (match >= 0) {
                    return new Found(-1, REASON_AMBIGUOUS_KEY);
                }
                match = i;
            }
        }
        if (match < 0) {
            return new Found(-1, REASON_UNKNOWN_KEY);
        }
        if (entries.get(match).keyCode == 0) {
            return new Found(-1, PlayerActionGate.REASON_UNBOUND);
        }
        return new Found(match, null);
    }

    /**
     * The bindings whose name starts with the prefix (all for an empty one), in the list's
     * order, at most {@link #MAX_LISTED}; a name shared with any other binding is marked, also
     * when the other is not listed.
     */
    static List<Listed> list(List<Entry> entries, String prefix) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (Entry entry : entries) {
            Integer count = counts.get(entry.name);
            counts.put(entry.name, count == null ? 1 : count + 1);
        }
        List<Listed> out = new ArrayList<Listed>();
        for (Entry entry : entries) {
            if (out.size() >= MAX_LISTED) {
                break;
            }
            if (entry.name.startsWith(prefix == null ? "" : prefix)) {
                out.add(new Listed(entry, counts.get(entry.name) > 1));
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Which binding the game counted a press on, when not the one named: the game counts a key's
     * press on the one binding on that key whose modifier and context are active, which may be
     * another binding on the same key, or none. Null when the named binding was counted, or when
     * a count could not be read and nothing can be said.
     *
     * @param names  the bindings on the key
     * @param before each one's press count before the press; -1 when unreadable
     * @param after  each one's press count after it
     * @param named  the place of the named binding among them
     */
    static String countedElsewhere(List<String> names, int[] before, int[] after, int named) {
        if (before[named] < 0 || after[named] < 0 || after[named] > before[named]) {
            return null;
        }
        for (int i = 0; i < names.size(); i++) {
            if (i != named && before[i] >= 0 && after[i] > before[i]) {
                return names.get(i);
            }
        }
        return "none";
    }

    /** How many bindings match the prefix, listed or not. */
    static int matching(List<Entry> entries, String prefix) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.name.startsWith(prefix == null ? "" : prefix)) {
                count++;
            }
        }
        return count;
    }
}
