package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.ALREADY_OPEN;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.KEYS_OFF;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.NOT_LOCAL;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.NOT_OPERATOR;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.OPEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a join opens the command inbox by itself (D24, REQ-222, ARC-029). */
class LocalJoinTest {

    @Test
    @DisplayName("the host or a matching secret opens it for an operator, with both keys on and the inbox closed")
    void decisions() {
        assertEquals(OPEN, LocalJoin.decide(true, true, false, true, false, true), "single-player host");
        assertEquals(OPEN, LocalJoin.decide(true, true, false, false, true, true), "secret returned");
        assertEquals(KEYS_OFF, LocalJoin.decide(false, true, false, true, true, true), "openOnLocalJoin off");
        assertEquals(KEYS_OFF, LocalJoin.decide(true, false, false, true, true, true), "inbox disabled");
        assertEquals(ALREADY_OPEN, LocalJoin.decide(true, true, true, true, true, true));
        assertEquals(NOT_LOCAL, LocalJoin.decide(true, true, false, false, false, true), "a guest, or a tunnel");
        assertEquals(NOT_OPERATOR, LocalJoin.decide(true, true, false, true, false, false));
    }

    @Test
    @DisplayName("a proof matches only the exact secret, never null or a different length")
    void secrets() {
        SecureRandom random = new SecureRandom();
        String secret = LocalJoin.newSecret(random);
        assertEquals(32, secret.length());
        assertTrue(secret.matches("[0-9a-f]{32}"));
        assertNotEquals(secret, LocalJoin.newSecret(random));
        assertTrue(LocalJoin.matches(secret, secret));
        assertFalse(LocalJoin.matches(secret, secret.substring(1)));
        assertFalse(LocalJoin.matches(secret, secret + "0"));
        assertFalse(LocalJoin.matches(secret, ""));
        assertFalse(LocalJoin.matches(secret, null));
        assertFalse(LocalJoin.matches(null, secret), "no secret written: nothing matches");
        assertFalse(LocalJoin.matches("short", "short"));
    }
}
