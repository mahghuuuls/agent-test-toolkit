package com.mahghuuuls.agenttesttoolkit.inspect;

import java.util.Arrays;
import java.util.Locale;

/**
 * The words of {@code find block <block> [<state>] <x1> <y1> <z1> <x2> <y2> <z2> [limit <n>]}.
 *
 * <p>Pure, so the forms are unit-tested: a state is a word holding {@code =}, never a
 * coordinate, so a coordinate typo is a usage error instead of a strange state.
 */
public final class FindArgs {

    public static final int DEFAULT_LIMIT = 64;
    public static final int MAX_LIMIT = 1024;

    public final String block;
    /** The state text, or null for none. */
    public final String state;
    /** The six coordinate words, as typed (they may be {@code ~}-relative). */
    public final String[] coordinates;
    public final int limit;

    private FindArgs(String block, String state, String[] coordinates, int limit) {
        this.block = block;
        this.state = state;
        this.coordinates = coordinates;
        this.limit = limit;
    }

    /** The parsed words, or null when they are not one of the forms. */
    public static FindArgs parse(String[] args) {
        if (args == null || args.length < 8 || !"block".equals(args[0].toLowerCase(Locale.ROOT))) {
            return null;
        }
        int end = args.length;
        int limit = DEFAULT_LIMIT;
        if (end >= 10 && "limit".equals(args[end - 2].toLowerCase(Locale.ROOT))) {
            try {
                limit = Integer.parseInt(args[end - 1]);
            } catch (NumberFormatException notNumber) {
                return null;
            }
            if (limit < 1 || limit > MAX_LIMIT) {
                return null;
            }
            end -= 2;
        }
        boolean withState = end == 9;
        if (end != 8 && !withState) {
            return null;
        }
        String state = withState ? args[2] : null;
        if (state != null && state.indexOf('=') < 0) {
            return null;
        }
        int first = withState ? 3 : 2;
        String[] coordinates = Arrays.copyOfRange(args, first, first + 6);
        for (String word : coordinates) {
            if (word.indexOf('=') >= 0) {
                return null;
            }
        }
        return new FindArgs(args[1], state, coordinates, limit);
    }
}
