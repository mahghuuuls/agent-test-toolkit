package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Facts;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Refusal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which client steps the server refuses at once, with which key, and in which order. */
class ClientRequestGateTest {

    private static Facts client(String version, Boolean allowed) {
        return new Facts(false, false, true, allowed, version);
    }

    @Test
    @DisplayName("a player action goes only to a client that said hello with 1.7.0, whatever it allows")
    void actionsNeedTheNewClient() {
        for (String step : new String[] {"select", "use", "attack", "hold", "drop", "inventory", "tooltip"}) {
            assertNull(ClientRequestGate.refusal(step, false, client("1.7.0", Boolean.TRUE)), step);
            assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal(step, false, client("1.6.0", Boolean.TRUE)));
            assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal(step, false, client("1.6.0", Boolean.FALSE)),
                    "a 1.6.0 client that does not allow actions is still too old");
            assertEquals(Refusal.NOT_ALLOWED, ClientRequestGate.refusal(step, false, client("1.7.0", Boolean.FALSE)));
            assertEquals(Refusal.NOT_ALLOWED, ClientRequestGate.refusal(step, false, client(null, null)), "no hello yet");
            assertEquals(Refusal.NO_CLIENT, ClientRequestGate.refusal(step, false, new Facts(false, false, false, null, null)));
            assertEquals("devtool.client.noClient", Refusal.CLIENT_TOO_OLD.key);
        }
    }

    @Test
    @DisplayName("a dead player may choose a slot but not click; an old client is refused before the death counts")
    void dead() {
        Facts deadNew = new Facts(true, false, true, Boolean.TRUE, "1.7.0");
        assertNull(ClientRequestGate.refusal("select", false, deadNew));
        assertEquals(Refusal.DEAD, ClientRequestGate.refusal("use", false, deadNew));
        assertEquals(Refusal.DEAD, ClientRequestGate.refusal("attack", false, deadNew));
        assertEquals(Refusal.CLIENT_TOO_OLD,
                ClientRequestGate.refusal("use", false, new Facts(true, false, true, Boolean.TRUE, "1.6.0")));
        assertEquals("devtool.client.dead", Refusal.DEAD.key);
        assertEquals(Refusal.DEAD, ClientRequestGate.refusal("hold", false, deadNew));
        assertEquals(Refusal.DEAD, ClientRequestGate.refusal("drop", false, deadNew));
        assertNull(ClientRequestGate.refusal("inventory", false, deadNew));
        assertEquals(Refusal.DEAD, ClientRequestGate.refusal("tooltip", false, deadNew));
    }

    @Test
    @DisplayName("a busy client takes no action and no step")
    void busy() {
        Facts busy = new Facts(false, true, true, Boolean.TRUE, "1.7.0");
        assertEquals(Refusal.BUSY, ClientRequestGate.refusal("use", false, busy));
        assertEquals(Refusal.BUSY, ClientRequestGate.refusal("close", false, busy));
        assertEquals(Refusal.BUSY, ClientRequestGate.refusal("reconnect", false, busy));
    }

    @Test
    @DisplayName("a reconnect's logout goes only to a 1.7.0 client; without it an older one may reconnect as before")
    void logout() {
        assertNull(ClientRequestGate.refusal("reconnect", true, client("1.7.0", Boolean.TRUE)));
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal("reconnect", true, client("1.6.0", Boolean.TRUE)));
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal("reconnect", true, client("1.6.0", Boolean.FALSE)));
        assertNull(ClientRequestGate.refusal("reconnect", false, client("1.6.0", Boolean.TRUE)));
        assertNull(ClientRequestGate.refusal("reconnect", false, client("1.4.0", Boolean.TRUE)));
    }

    @Test
    @DisplayName("the lifecycle steps keep their order and their 1.5.0 rule for quit")
    void lifecycleUnchanged() {
        assertEquals(Refusal.NOT_DEAD, ClientRequestGate.refusal("respawn", false, client("1.7.0", Boolean.TRUE)));
        assertNull(ClientRequestGate.refusal("respawn", false, new Facts(true, false, true, Boolean.TRUE, "1.4.0")));
        assertEquals(Refusal.NOT_DEAD, ClientRequestGate.refusal("respawn", false, new Facts(false, true, false, null, null)),
                "not dead before busy and before the client checks");
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal("quit", false, client("1.4.0", Boolean.TRUE)));
        assertEquals(Refusal.NOT_ALLOWED, ClientRequestGate.refusal("quit", false, client("1.4.0", Boolean.FALSE)),
                "the quit rule checks the version only for a client that allows actions, as in 1.5.0");
        assertNull(ClientRequestGate.refusal("quit", false, client("1.5.0", Boolean.TRUE)));
        assertEquals(Refusal.NO_CLIENT, ClientRequestGate.refusal("close", false, new Facts(false, false, false, null, null)));
        assertEquals(Refusal.NOT_ALLOWED, ClientRequestGate.refusal("close", false, client("1.4.0", Boolean.FALSE)));
        assertNull(ClientRequestGate.refusal("close", false, client("1.4.0", Boolean.TRUE)));
    }
}
