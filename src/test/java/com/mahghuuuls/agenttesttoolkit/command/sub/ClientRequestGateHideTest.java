package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Facts;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A player action whose screenshot leaves chat or the HUD out goes only to a 1.8.0 client. */
class ClientRequestGateHideTest {

    private static Facts client(String version) {
        return new Facts(false, false, true, Boolean.TRUE, version);
    }

    @Test
    @DisplayName("hide needs 1.8.0; without it a 1.7.0 client still gets the action")
    void hideNeedsTheNewClient() {
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal("use", false, true, client("1.7.0")));
        assertNull(ClientRequestGate.refusal("use", false, true, client("1.8.0")));
        assertNull(ClientRequestGate.refusal("use", false, false, client("1.7.0")));
        assertNull(ClientRequestGate.refusal("use", false, client("1.7.0")), "the old form means no hide");
    }
}
