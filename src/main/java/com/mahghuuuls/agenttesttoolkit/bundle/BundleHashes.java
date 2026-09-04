package com.mahghuuuls.agenttesttoolkit.bundle;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes that tie a bundle run to the text that defined it.
 *
 * <p>Two hashes, because they answer two questions. The raw hash of a file's bytes answers
 * "is this the exact file I audited", and changes when a comment or a space does. The
 * normalized hash of one parsed bundle answers "would this bundle do the same thing", and
 * changes only when a command, a delay, a tolerated key, or the stop or trace rule changes.
 * Evidence tooling wants the second; a reviewer comparing files wants the first.
 *
 * <p>The normalized form is versioned, and the version is part of the hashed text, so a
 * future change to the rendering cannot collide with an old hash by accident. The description
 * is deliberately left out: it is documentation, and editing it must not make a bundle look
 * like a different bundle.
 */
public final class BundleHashes {

    /** Bumped whenever {@link #normalized} changes what it renders or how. */
    public static final String NORMALIZATION_VERSION = "1";

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private BundleHashes() {
    }

    /** Lower-case hexadecimal SHA-256 of the bytes as given. */
    public static String sha256(byte[] bytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java 8 runtime ships SHA-256. Treated as impossible rather than handled.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        byte[] out = digest.digest(bytes);
        char[] hex = new char[out.length * 2];
        for (int i = 0; i < out.length; i++) {
            int v = out[i] & 0xFF;
            hex[i * 2] = HEX[v >>> 4];
            hex[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(hex);
    }

    /** The normalized hash of one bundle: SHA-256 of {@link #normalized}. */
    public static String normalizedHash(BundleDefinition definition) {
        return sha256(normalized(definition).getBytes(UTF8));
    }

    /**
     * Renders a bundle in one fixed shape, independent of the JSON it came from.
     *
     * <p>One line per element, a fixed field order, no whitespace that the file could vary,
     * and every string length-prefixed so that a command containing a newline or a separator
     * cannot be confused with two commands. Deterministic by construction; two files that
     * parse to the same bundle render to the same text.
     */
    static String normalized(BundleDefinition definition) {
        StringBuilder out = new StringBuilder();
        out.append("v").append(NORMALIZATION_VERSION).append('\n');
        field(out, "name", definition.getName());
        out.append("stopOnFailure=").append(definition.isStopOnFailure()).append('\n');
        out.append("trace=").append(definition.isTraced()).append('\n');
        out.append("commands=").append(definition.size()).append('\n');
        for (BundleCommand command : definition.getCommands()) {
            field(out, "command", command.getCommand());
            out.append("delayTicks=").append(command.getDelayTicks()).append('\n');
            out.append("tolerated=").append(command.getToleratedFailures().size()).append('\n');
            for (String key : command.getToleratedFailures()) {
                field(out, "key", key);
            }
        }
        return out.toString();
    }

    private static void field(StringBuilder out, String name, String value) {
        out.append(name).append('=').append(value.length()).append(':').append(value).append('\n');
    }
}
