package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * A placeholder could not be filled in when its command was about to run.
 *
 * <p>Carries a failure key in the toolkit's own namespace, so the failure classifies and
 * tolerates exactly like a vanilla command error. The message names the placeholder, which
 * is what a reader of the error record needs.
 */
public final class PlaceholderException extends Exception {

    /** The value exists in principle but nothing is bound right now. */
    public static final String KEY_UNBOUND = "devtool.placeholder.unbound";
    /** The value exists but contains a character that could break the command it goes into. */
    public static final String KEY_UNSAFE = "devtool.placeholder.unsafe";

    private final String key;
    private final String placeholder;

    public PlaceholderException(String key, String placeholder, String message) {
        super(message);
        this.key = key;
        this.placeholder = placeholder;
    }

    public String getKey() {
        return key;
    }

    /** The placeholder text as written in the bundle, such as {@code ${operator.name}}. */
    public String getPlaceholder() {
        return placeholder;
    }
}
