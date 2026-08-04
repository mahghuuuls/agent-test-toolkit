package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * A bundle file could not be understood.
 *
 * <p>Carries enough context to identify the problem without opening the file, per REQ-110.
 * "Failed to parse" alone is useless when several bundles live in one file; the message names
 * the bundle and the position where it can.
 */
public final class BundleParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public BundleParseException(String message) {
        super(message);
    }

    public BundleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
