package com.mahghuuuls.agenttesttoolkit.fixture;

/**
 * The tag that marks an entity as a toolkit fixture, and the rules around its text.
 *
 * <p>Ownership is the tag and nothing else. An entity with the right name or the right type
 * is not a fixture; an entity carrying {@code devtool.fx.<session>.<handle>} is, and only for
 * that session. Cleanup checks the tag at the moment of removal, so a registry entry that
 * has drifted from the world can never delete the wrong thing.
 *
 * <p>The alphabet is vanilla's selector value alphabet, letters, digits, underscore, dot and
 * hyphen, so that {@code @e[tag=...]} can address a fixture too. That is the cheapest possible
 * cross-check of the toolkit's own listing: the game itself can be asked.
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
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * A session name made safe for a tag. Anything outside the alphabet becomes an underscore,
     * so a session called "my test" owns fixtures tagged under "my_test". The tag is recorded
     * on every claim, so nobody has to guess at this.
     */
    public static String scope(String sessionName) {
        StringBuilder out = new StringBuilder(sessionName.length());
        for (int i = 0; i < sessionName.length(); i++) {
            char c = sessionName.charAt(i);
            out.append(isSelectorSafe(String.valueOf(c)) ? c : '_');
        }
        return out.toString();
    }

    /** The full tag for a handle in a session. The handle must already be selector-safe. */
    public static String tag(String sessionName, String handle) {
        return PREFIX + scope(sessionName) + "." + handle;
    }

    /** Whether a tag marks a fixture of this session, whatever its handle. */
    public static boolean ownedBy(String tag, String sessionName) {
        return tag != null && tag.startsWith(PREFIX + scope(sessionName) + ".");
    }

    /** The handle inside a tag of this session, or null when the tag is not this session's. */
    public static String handleOf(String tag, String sessionName) {
        if (!ownedBy(tag, sessionName)) {
            return null;
        }
        return tag.substring((PREFIX + scope(sessionName) + ".").length());
    }
}
