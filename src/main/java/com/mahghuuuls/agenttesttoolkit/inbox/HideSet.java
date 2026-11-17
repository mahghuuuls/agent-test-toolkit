package com.mahghuuuls.agenttesttoolkit.inbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * What a screenshot leaves out: the one owner of the values and of how a set of them is written,
 * for the screenshot command, an action's {@code screenshotHide}, the client request channel and
 * the client that takes the picture, so none of them can accept a set another refuses.
 *
 * <p>Pure: no game type, so the server, the inbox and the client read it alike and it is
 * unit-tested without a game.
 */
public final class HideSet {

    /** The chat lines. */
    public static final String CHAT = "chat";
    /** The whole HUD, as F1 hides it, chat included. */
    public static final String HUD = "hud";
    /** The toasts in the top right corner; read only by a 1.9.0 client. */
    public static final String TOASTS = "toasts";
    /** Every value, in the order a set of them is written. */
    public static final List<String> VALUES = Collections.unmodifiableList(Arrays.asList(CHAT, HUD, TOASTS));

    private HideSet() {
    }

    /**
     * A value as one written form: one value or a comma list of them, each at most once, written
     * back in the order of {@link #VALUES}, so {@code toasts,chat} and {@code chat,toasts} are the
     * same set and a 1.8.0 value stays the same text. Empty for empty; null for anything else, such
     * as an unknown or repeated value or an empty item.
     */
    public static String canonical(String value) {
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            return "";
        }
        boolean[] given = new boolean[VALUES.size()];
        for (String part : value.split(",", -1)) {
            int at = VALUES.indexOf(part);
            if (at < 0 || given[at]) {
                return null;
            }
            given[at] = true;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < given.length; i++) {
            if (given[i]) {
                out.append(out.length() == 0 ? "" : ",").append(VALUES.get(i));
            }
        }
        return out.toString();
    }

    /** Whether a value is a set as {@link #canonical} reads it, empty included. */
    public static boolean isValid(String value) {
        return canonical(value) != null;
    }

    /** Whether a written set (as {@link #canonical} gives it) names one value. */
    public static boolean has(String set, String value) {
        if (set == null || set.isEmpty()) {
            return false;
        }
        for (String part : set.split(",")) {
            if (part.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
