package com.mahghuuuls.agenttesttoolkit.fixture;

/**
 * The tag that marks an entity as a toolkit fixture, and the rules around its text.
 *
 * <p>Ownership is the tag and nothing else. An entity with the right name or the right type
 * is not a fixture; an entity carrying {@code devtool.fx.<session>.<handle>} is, and only for
 * that session. The registry in {@code ToolkitState} is a cache of these tags, never a second
 * source of truth.
 *
 * <p>The alphabet is vanilla's selector value alphabet, letters, digits, underscore, dot and
 * hyphen, so that {@code @e[tag=...]} can address a fixture too. That is the cheapest possible
 * cross-check of the toolkit's own listing: the game itself can be asked.
 *
 * <p>The tag must say unambiguously which session and which handle it belongs to, so neither
 * part may contain the dot that separates them: the session part maps a dot to an underscore,
 * and a handle may not contain one. Before 1.4.0 the session part kept its dots and ownership
 * was a prefix match, so session {@code a} owned session {@code a.b}'s fixtures.
 *
 * <p>Pure, so the alphabet and the ownership rule are testable without a world.
 */
public final class FixtureTags {

    public static final String PREFIX = "devtool.fx.";

    private FixtureTags() {
    }

    /** True when every character is one vanilla accepts inside a selector value. */
    public static boolean isSelectorSafe(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!isSelectorChar(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** A handle: selector-safe and without the dot that separates it from the session part. */
    public static boolean isHandleSafe(String handle) {
        return isSelectorSafe(handle) && handle.indexOf('.') < 0;
    }

    private static boolean isSelectorChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '.' || c == '-';
    }

    /**
     * A session name made safe for a tag. A dot, and anything outside the alphabet, becomes an
     * underscore, so a session called "my test" or "my.test" owns fixtures tagged under
     * "my_test". The tag is recorded on every claim, so nobody has to guess at this.
     */
    public static String scope(String sessionName) {
        StringBuilder out = new StringBuilder(sessionName.length());
        for (int i = 0; i < sessionName.length(); i++) {
            char c = sessionName.charAt(i);
            out.append(c != '.' && isSelectorChar(c) ? c : '_');
        }
        return out.toString();
    }

    /** The full tag for a handle in a session. The handle must already be handle-safe. */
    public static String tag(String sessionName, String handle) {
        return PREFIX + scope(sessionName) + "." + handle;
    }

    /** Whether a tag marks a fixture of this session, whatever its handle. */
    public static boolean ownedBy(String tag, String sessionName) {
        return handleOf(tag, sessionName) != null;
    }

    /**
     * The handle inside a tag of this session, or null when the tag is not this session's.
     * The part after the session must itself be a valid handle, so a tag written by an older
     * version for a session with a dot in its name is owned by no session at all.
     */
    public static String handleOf(String tag, String sessionName) {
        if (tag == null) {
            return null;
        }
        String own = PREFIX + scope(sessionName) + ".";
        if (!tag.startsWith(own)) {
            return null;
        }
        String handle = tag.substring(own.length());
        return isHandleSafe(handle) ? handle : null;
    }
}
