package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.PendingRejoin;

import java.util.UUID;

/**
 * The decisions behind keeping an inbox opening across a reconnect the toolkit started (D19,
 * ARC-023). Pure: callers pass the state and the time, so the security-relevant rules are unit
 * tests (rule 16).
 *
 * <p>The gate still opens only by a person typing it, once per launch. A suspension carries that
 * person's opening over one leave their own request started, for that same player only, within
 * a deadline; anything else closes it.
 */
public final class InboxRejoin {

    /** How soon after the client said it is leaving the logout must come to count as that leave. */
    static final long LEAVE_GRACE_MILLIS = 15000L;
    /** How long after logging in again the token may take to arrive. */
    static final long TOKEN_GRACE_MILLIS = 10000L;

    /** What a report from the returning client does. */
    public enum Return {
        RESUME,
        /** Wrong or missing token: close. */
        TOKEN_MISMATCH,
        /** Not about the suspended opening: ignore. */
        NOT_SUSPENDED
    }

    /** Why a suspended opening must close now, or null to keep waiting. */
    public static final String REJOIN_TIMEOUT = "rejoinTimeout";
    public static final String RETURNED_OTHER_ROUTE = "returnedOtherRoute";

    private InboxRejoin() {
    }

    /**
     * Whether this logout is the leave of a reconnect the toolkit started for the operator: asked
     * for, and confirmed by the client as leaving a moment ago. A reconnect the client refused is
     * never confirmed, so the operator's next leave closes the inbox as usual.
     */
    public static boolean suspendsOnLogout(InboxOpening opening, UUID player, PendingRejoin pending,
                                           long nowMillis) {
        return opening != null && !opening.isSuspended() && pending != null && pending.isLeaving()
                && opening.getOperatorUuid().equals(player) && pending.getPlayer().equals(player)
                && nowMillis - pending.getLeftAtMillis() <= LEAVE_GRACE_MILLIS;
    }

    /**
     * Whether the inbox holds its requests because its operator's reconnect is starting: asked,
     * not refused, the leave not yet seen. Requests written meanwhile are meant for after the
     * return, so they wait; a client that never answers holds it for {@link #LEAVE_GRACE_MILLIS}
     * at most. However the reconnect was asked, a request, a bundle it runs, or a typed command.
     */
    public static boolean holdsPoll(InboxOpening opening, PendingRejoin pending, long nowMillis) {
        return opening != null && !opening.isSuspended() && pending != null
                && opening.getOperatorUuid().equals(pending.getPlayer())
                && nowMillis - pending.getAskedAtMillis() <= LEAVE_GRACE_MILLIS;
    }

    /** Whether a report is about the reconnect the inbox remembers for this player. */
    public static boolean isAbout(PendingRejoin pending, UUID player, long requestTick) {
        return pending != null && pending.getPlayer().equals(player) && pending.getRequestTick() == requestTick;
    }

    /** What a returning client's report does to the opening. */
    public static Return onReturn(InboxOpening opening, UUID player, String token, PendingRejoin pending) {
        if (opening == null || !opening.isSuspended() || !opening.getOperatorUuid().equals(player)) {
            return Return.NOT_SUSPENDED;
        }
        if (pending == null || !pending.getPlayer().equals(player) || token == null || token.isEmpty()
                || !token.equals(pending.getToken())) {
            return Return.TOKEN_MISMATCH;
        }
        return Return.RESUME;
    }

    /** Why a suspended opening must close at this moment, or null when it may keep waiting. */
    public static String closeReason(InboxOpening opening, long nowMillis) {
        if (opening == null || !opening.isSuspended()) {
            return null;
        }
        if (nowMillis > opening.getSuspendedUntilMillis()) {
            return REJOIN_TIMEOUT;
        }
        if (opening.getReturnedAtMillis() > 0L && nowMillis - opening.getReturnedAtMillis() > TOKEN_GRACE_MILLIS) {
            return RETURNED_OTHER_ROUTE;
        }
        return null;
    }
}
