package com.mahghuuuls.agenttesttoolkit.inbox;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Whether a join opens the command inbox by itself (D24, REQ-222, ARC-029).
 *
 * <p>Pure, for tests (rule 18). A join is local when it is the single-player host's, over the
 * in-process channel nothing outside the game can open, or when the client returned the secret
 * the dedicated server wrote into its own folder: an address alone cannot tell, since a tunnel on
 * the same machine makes every player look like {@code 127.0.0.1} (FQ42). The player must also be
 * an operator, so the opening is as strong as typing {@code devtool inbox open}.
 */
public final class LocalJoin {

    /** What a join does to the inbox. */
    public enum Decision {
        OPEN,
        /** A key is off: nothing happens, and nothing is recorded. */
        KEYS_OFF,
        /** Already open, for this player or another: nothing changes. */
        ALREADY_OPEN,
        /** Neither the host's channel nor a matching secret. */
        NOT_LOCAL,
        /** Local, but not allowed to run toolkit commands: refused with a record. */
        NOT_OPERATOR
    }

    static final int SECRET_LENGTH = 32;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private LocalJoin() {
    }

    public static Decision decide(boolean openOnLocalJoin, boolean inboxEnabled, boolean inboxOpen,
                                  boolean hostOverLocalChannel, boolean proofMatched, boolean operator) {
        if (!openOnLocalJoin || !inboxEnabled) {
            return Decision.KEYS_OFF;
        }
        if (inboxOpen) {
            return Decision.ALREADY_OPEN;
        }
        if (!hostOverLocalChannel && !proofMatched) {
            return Decision.NOT_LOCAL;
        }
        return operator ? Decision.OPEN : Decision.NOT_OPERATOR;
    }

    /** A fresh secret: 32 hexadecimal characters. */
    public static String newSecret(SecureRandom random) {
        StringBuilder out = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            out.append(Character.forDigit(random.nextInt(16), 16));
        }
        return out.toString();
    }

    /** Whether a proof equals the secret, compared in constant time. Null on either side never matches. */
    public static boolean matches(String secret, String proof) {
        if (secret == null || proof == null || secret.length() != SECRET_LENGTH) {
            return false;
        }
        return MessageDigest.isEqual(secret.getBytes(UTF8), proof.getBytes(UTF8));
    }
}
