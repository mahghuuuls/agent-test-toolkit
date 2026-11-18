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
    private final String hint;

    public PlaceholderException(String key, String placeholder, String message) {
        this(key, placeholder, message, null);
    }

    /** @param hint the step to run first so the placeholder has a value; null when none is known */
    public PlaceholderException(String key, String placeholder, String message, String hint) {
        super(message);
        this.key = key;
        this.placeholder = placeholder;
        this.hint = hint;
    }

    public String getKey() {
        return key;
    }

    /** The placeholder text as written in the bundle, such as {@code ${operator.name}}. */
    public String getPlaceholder() {
        return placeholder;
    }

    /** The step to run first, such as {@code devtool session start <name>}; null when none is known. */
    public String getHint() {
        return hint;
    }

    /**
     * The failure's detail for its record and an inbox answer: the key, then the step to run
     * first when one is known. The key leads, so a reader matching on it still finds it, and an
     * agent that reads only the files learns the step without the chat.
     */
    public String getDetail() {
        return hint == null ? key : key + ": run " + hint + " first";
    }
}
