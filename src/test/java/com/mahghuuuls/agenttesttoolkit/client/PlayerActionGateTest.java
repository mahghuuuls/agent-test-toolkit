package com.mahghuuuls.agenttesttoolkit.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** When the client refuses a player action, and in which order the reasons are checked. */
class PlayerActionGateTest {

    @Test
    @DisplayName("no world, then an open screen, then an unbound key; otherwise it may act")
    void refusals() {
        assertEquals("noWorld", PlayerActionGate.refusal(false, true, true, 0));
        assertEquals("screenOpen", PlayerActionGate.refusal(true, true, true, 0));
        assertEquals("unbound", PlayerActionGate.refusal(true, false, true, 0));
        assertNull(PlayerActionGate.refusal(true, false, true, -99), "the right mouse button");
        assertNull(PlayerActionGate.refusal(true, false, false, 0), "select presses no key");
        assertEquals("screenOpen", PlayerActionGate.refusal(true, true, false, 0), "select waits for no screen too");
    }
}
