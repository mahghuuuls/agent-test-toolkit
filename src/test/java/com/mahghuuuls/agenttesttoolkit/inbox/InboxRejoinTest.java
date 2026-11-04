package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.PendingRejoin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeping an inbox opening across a reconnect the toolkit started (D19, REQ-207, ARC-023). */
class InboxRejoinTest {

    private static final UUID OPERATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long NOW = 1_000_000L;

    private static InboxOpening open() {
        return new InboxOpening(OPERATOR, "Developer", 5L, 1L, "world|1");
    }

    private static PendingRejoin leaving(UUID player, long leftAt) {
        return new PendingRejoin(player, "t0k", 7L, leftAt).leaving(leftAt);
    }

    @Test
    @DisplayName("the poll holds while the operator's reconnect starts, for a while at most")
    void holdsPoll() {
        PendingRejoin asked = new PendingRejoin(OPERATOR, "t0k", 7L, NOW);
        assertTrue(InboxRejoin.holdsPoll(open(), asked, NOW + 500L));
        assertTrue(InboxRejoin.holdsPoll(open(), asked.leaving(NOW + 1000L), NOW + 1100L), "said it leaves");
        assertFalse(InboxRejoin.holdsPoll(open(), asked, NOW + InboxRejoin.LEAVE_GRACE_MILLIS + 1L),
                "a client that never answers");
        assertFalse(InboxRejoin.holdsPoll(open(), null, NOW), "nothing asked, or refused");
        assertFalse(InboxRejoin.holdsPoll(open(), new PendingRejoin(OTHER, "t", 7L, NOW), NOW), "not the operator");
        assertFalse(InboxRejoin.holdsPoll(open().suspendedUntil(NOW + 5L), asked, NOW), "suspended: not polled anyway");
    }

    @Test
    @DisplayName("the operator's logout soon after their client said it is leaving suspends")
    void suspends() {
        assertTrue(InboxRejoin.suspendsOnLogout(open(), OPERATOR, leaving(OPERATOR, NOW - 1000L), NOW));
    }

    @Test
    @DisplayName("a reconnect asked but not confirmed, another player, a stale leave or a suspended opening do not")
    void doesNotSuspend() {
        assertFalse(InboxRejoin.suspendsOnLogout(open(), OPERATOR, null, NOW), "no reconnect asked");
        assertFalse(InboxRejoin.suspendsOnLogout(open(), OPERATOR, new PendingRejoin(OPERATOR, "t", 7L, NOW), NOW),
                "asked, but the client never said it is leaving: refused, or not heard");
        assertFalse(InboxRejoin.suspendsOnLogout(open(), OTHER, leaving(OTHER, NOW), NOW), "not the operator");
        assertFalse(InboxRejoin.suspendsOnLogout(open(), OPERATOR, leaving(OTHER, NOW), NOW),
                "someone else's reconnect");
        assertFalse(InboxRejoin.suspendsOnLogout(open(), OPERATOR,
                leaving(OPERATOR, NOW - InboxRejoin.LEAVE_GRACE_MILLIS - 1L), NOW), "too long ago");
        assertFalse(InboxRejoin.suspendsOnLogout(open().suspendedUntil(NOW + 5L), OPERATOR,
                leaving(OPERATOR, NOW), NOW), "already suspended");
        assertFalse(InboxRejoin.suspendsOnLogout(null, OPERATOR, leaving(OPERATOR, NOW), NOW));
    }

    @Test
    @DisplayName("a report is about the remembered reconnect only for that player and request tick")
    void isAbout() {
        PendingRejoin pending = new PendingRejoin(OPERATOR, "t0k", 7L, NOW);
        assertTrue(InboxRejoin.isAbout(pending, OPERATOR, 7L));
        assertFalse(InboxRejoin.isAbout(pending, OPERATOR, 8L));
        assertFalse(InboxRejoin.isAbout(pending, OTHER, 7L));
        assertFalse(InboxRejoin.isAbout(null, OPERATOR, 7L));
    }

    @Test
    @DisplayName("the returning client resumes only with the token it was sent away with")
    void returns() {
        InboxOpening suspended = open().suspendedUntil(NOW + 60000L);
        PendingRejoin pending = leaving(OPERATOR, NOW);
        assertEquals(InboxRejoin.Return.RESUME, InboxRejoin.onReturn(suspended, OPERATOR, "t0k", pending));
        assertEquals(InboxRejoin.Return.TOKEN_MISMATCH, InboxRejoin.onReturn(suspended, OPERATOR, "nope", pending));
        assertEquals(InboxRejoin.Return.TOKEN_MISMATCH, InboxRejoin.onReturn(suspended, OPERATOR, "", pending));
        assertEquals(InboxRejoin.Return.TOKEN_MISMATCH, InboxRejoin.onReturn(suspended, OPERATOR, "t0k", null));
        // A player with the same name on an offline server has the same UUID but not the token.
        assertEquals(InboxRejoin.Return.TOKEN_MISMATCH, InboxRejoin.onReturn(suspended, OPERATOR, null, pending));
        assertEquals(InboxRejoin.Return.NOT_SUSPENDED, InboxRejoin.onReturn(open(), OPERATOR, "t0k", pending));
        assertEquals(InboxRejoin.Return.NOT_SUSPENDED, InboxRejoin.onReturn(suspended, OTHER, "t0k", pending));
    }

    @Test
    @DisplayName("a suspension closes after its deadline, or when the operator came back without the token")
    void closes() {
        InboxOpening suspended = open().suspendedUntil(NOW + 60000L);
        assertNull(InboxRejoin.closeReason(suspended, NOW));
        assertEquals(InboxRejoin.REASON_REJOIN_TIMEOUT, InboxRejoin.closeReason(suspended, NOW + 60001L));
        InboxOpening back = suspended.returnedAt(NOW);
        assertNull(InboxRejoin.closeReason(back, NOW + InboxRejoin.TOKEN_GRACE_MILLIS));
        assertEquals(InboxRejoin.REASON_RETURNED_OTHER_ROUTE,
                InboxRejoin.closeReason(back, NOW + InboxRejoin.TOKEN_GRACE_MILLIS + 1L));
        assertNull(InboxRejoin.closeReason(open(), NOW + 999999L), "a listening opening never times out");
    }

    @Test
    @DisplayName("a suspended opening resumes listening, keeping who opened it and where")
    void resumedKeepsIdentity() {
        InboxOpening resumed = open().suspendedUntil(NOW + 5L).returnedAt(NOW).resumed();
        assertFalse(resumed.isSuspended());
        assertEquals(0L, resumed.getReturnedAtMillis());
        assertEquals(OPERATOR, resumed.getOperatorUuid());
        assertEquals("world|1", resumed.getWorldKey());
    }
}
