package com.mahghuuuls.agenttesttoolkit.inbox;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Whether a join opens the command inbox by itself.
 *
 * <p>Pure, so every rule here is unit-tested without a game. A join is local when it is the
 * single-player host's, over the in-process channel nothing outside the game can open, or when
 * the client returned the secret the dedicated server wrote into its own folder: an address alone
 * cannot tell, since a tunnel on the same machine makes every player look like {@code 127.0.0.1}.
 * The player must also be an operator, so the opening is as strong as typing
 * {@code devtool inbox open}.
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
        NOT_OPERATOR,
        /**
         * Single player, over the in-process channel, but the name is not the owner's: refused with
         * a record, so the inbox does not stay closed without a word.
         */
        NOT_HOST
    }

    static final int SECRET_LENGTH = 32;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * True when two names are the same name in another case. The game reads the single-player
     * host's data from the world file only on an exact match, so such a pair reads another copy.
     * Names that differ otherwise are another player, not a warning.
     */
    public static boolean nameCaseDiffers(String playerName, String ownerName) {
        return playerName != null && ownerName != null && playerName.equalsIgnoreCase(ownerName)
                && !playerName.equals(ownerName);
    }

    private LocalJoin() {
    }

    /** What a proven local join does to operator rights, with the grant key. */
    public enum Grant {
        /** Nothing to do: a key off, no proof, or the toolkit gave the rights already in this run. */
        NONE,
        /** Give the joining player operator rights for this session. */
        GRANT,
        /** The player is in the operator list already, at any level: left as is, and said so. */
        KEEP,
        /** The server checks accounts: no rights from a proof there, refused with a record. */
        ONLINE_MODE
    }

    /**
     * Whether a dedicated-server join gets operator rights, decided before the inbox decision
     * so that the inbox then sees an operator. Decided at every proven join, also while the
     * inbox is already open or suspended, so a player back from a reconnect gets them again. Only
     * on an offline-mode server: there the name the join carries is all the server knows, and the
     * proof that the client shares the server's folder is what makes it the owner.
     */
    public static Grant decideGrant(boolean grantKey, boolean openOnLocalJoin, boolean inboxEnabled,
                                    boolean proofMatched, boolean inOpsList, boolean givenThisRun,
                                    boolean onlineMode) {
        if (!grantKey || !openOnLocalJoin || !inboxEnabled || !proofMatched || givenThisRun) {
            return Grant.NONE;
        }
        // In the list at any level, even one too low for the toolkit: the owner put it there,
        // and replacing it would raise it now and remove it at the leave.
        if (inOpsList) {
            return Grant.KEEP;
        }
        return onlineMode ? Grant.ONLINE_MODE : Grant.GRANT;
    }

    /** What a dedicated-server join does once the client has sent its proof. */
    public static Decision decideDedicated(boolean openOnLocalJoin, boolean inboxEnabled, boolean inboxOpen,
                                           boolean proofMatched, boolean operator) {
        Decision gate = gate(openOnLocalJoin, inboxEnabled, inboxOpen);
        if (gate != null) {
            return gate;
        }
        if (!proofMatched) {
            return Decision.NOT_LOCAL;
        }
        return operator ? Decision.OPEN : Decision.NOT_OPERATOR;
    }

    /** The checks both kinds of join start with: a key off, or the inbox already open. */
    private static Decision gate(boolean openOnLocalJoin, boolean inboxEnabled, boolean inboxOpen) {
        if (!openOnLocalJoin || !inboxEnabled) {
            return Decision.KEYS_OFF;
        }
        if (inboxOpen) {
            return Decision.ALREADY_OPEN;
        }
        return null;
    }

    /**
     * Whether an inbox opening is the single-player host's when the integrated server stops, so
     * it is kept across a reconnect instead of closed as a LAN guest's would be. The same name rule
     * as the join, so a host the join opened it for is never taken for a guest here.
     */
    public static boolean isHostAtStop(String ownerName, String operatorName) {
        return isOwnerName(operatorName, ownerName);
    }

    /**
     * Whether a single-player join is the host's: over the in-process channel, with the player's
     * name equal to the integrated server's owner ignoring case. The owner is the launch name as
     * typed, while the player's name is the profile name with the account's own case, so a
     * development launch as {@code mahghuuuls} joins as {@code Mahghuuuls}. Vanilla compares the
     * two the same way when it gives the host cheats, so the inbox and command rights agree on who
     * the host is.
     */
    public static boolean isHost(boolean localChannel, String playerName, String ownerName) {
        return localChannel && isOwnerName(playerName, ownerName);
    }

    /**
     * Whether a player name is the integrated server's owner, ignoring case. Every place that asks
     * whether a player is the single-player host uses this, so they cannot disagree.
     */
    public static boolean isOwnerName(String playerName, String ownerName) {
        return playerName != null && playerName.equalsIgnoreCase(ownerName);
    }

    /**
     * What a single-player join does. Only the host joins over the in-process channel, so a player
     * there who is not taken for the host means the names did not match: that is refused with a
     * record rather than left silent. A join over the network is never local here.
     */
    public static Decision decideSinglePlayer(boolean openOnLocalJoin, boolean inboxEnabled, boolean inboxOpen,
                                              boolean localChannel, String playerName, String ownerName,
                                              boolean operator) {
        Decision gate = gate(openOnLocalJoin, inboxEnabled, inboxOpen);
        if (gate != null) {
            return gate;
        }
        if (!localChannel) {
            return Decision.NOT_LOCAL;
        }
        if (!isHost(true, playerName, ownerName)) {
            return Decision.NOT_HOST;
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
