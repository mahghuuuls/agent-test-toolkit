package com.mahghuuuls.agenttesttoolkit.inspect;

/**
 * Bounds a long value for the log, and says so when it does.
 *
 * <p>Truncation must never be silent. NBT is the one output the toolkit produces that can be
 * arbitrarily large, and a silently shortened tag is worse than no output at all, because it
 * looks complete. An agent comparing a truncated dump against expected contents would conclude
 * the game is wrong.
 *
 * <p>Both lengths are reported. The original length is what tells an agent how much it is
 * missing and whether raising the configured limit would help.
 *
 * <p>Pure, so the boundary cases can be checked without a game. Off-by-one here is invisible in
 * play and would only surface as a confusing diff much later.
 */
public final class Truncation {

    private final String text;
    private final int originalLength;
    private final boolean truncated;

    private Truncation(String text, int originalLength, boolean truncated) {
        this.text = text;
        this.originalLength = originalLength;
        this.truncated = truncated;
    }

    /**
     * @param value the full text; null is treated as empty
     * @param limit the maximum number of characters to keep; values below 1 are treated as 1,
     *              since a limit of zero would produce output that says nothing at all
     */
    public static Truncation of(String value, int limit) {
        String full = value == null ? "" : value;
        int effectiveLimit = limit < 1 ? 1 : limit;
        if (full.length() <= effectiveLimit) {
            // Exactly at the limit is not truncated. The limit is a maximum length, not an
            // exclusive bound, and reporting truncation here would be a lie about the content.
            return new Truncation(full, full.length(), false);
        }
        return new Truncation(full.substring(0, effectiveLimit), full.length(), true);
    }

    /** The text to write: the whole value, or its first {@code limit} characters. */
    public String getText() {
        return text;
    }

    public int getOriginalLength() {
        return originalLength;
    }

    public int getOutputLength() {
        return text.length();
    }

    public boolean isTruncated() {
        return truncated;
    }
}
