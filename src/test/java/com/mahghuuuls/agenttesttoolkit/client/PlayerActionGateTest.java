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
        assertEquals("noWorld", PlayerActionGate.refusal(false, true, true, 0, false, false));
        assertEquals("screenOpen", PlayerActionGate.refusal(true, true, true, 0, false, false));
        assertEquals("unbound", PlayerActionGate.refusal(true, false, true, 0, false, false));
        assertNull(PlayerActionGate.refusal(true, false, true, -99, false, false), "the right mouse button");
        assertNull(PlayerActionGate.refusal(true, false, false, 0, false, false), "select presses no key");
        assertEquals("screenOpen", PlayerActionGate.refusal(true, true, false, 0, false, false), "select waits for no screen too");
    }

    @Test
    @DisplayName("a tooltip of an empty slot, then modifier keys this client cannot hold; both after the screen and key checks")
    void tooltipRefusals() {
        assertEquals("emptySlot", PlayerActionGate.refusal(true, false, false, 0, true, true));
        assertEquals("modifierUnavailable", PlayerActionGate.refusal(true, false, false, 0, false, true));
        assertEquals("screenOpen", PlayerActionGate.refusal(true, true, false, 0, true, true));
        assertEquals("noWorld", PlayerActionGate.refusal(false, false, false, 0, true, false));
        assertNull(PlayerActionGate.refusal(true, false, false, 0, false, false));
    }
}
