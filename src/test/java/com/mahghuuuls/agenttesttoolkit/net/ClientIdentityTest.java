package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which client a lifecycle step belongs to: a logout forgets the respawn, close or quit asked on
 * that connection, and the records name the player's UUID and the connection.
 */
class ClientIdentityTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    @AfterEach
    void reset() {
        ClientCapabilities.resetForTesting();
    }

    @Test
    @DisplayName("a close, respawn or quit asked before a logout is not answered after it")
    void logoutForgetsLifecycleSteps() {
        for (String step : new String[] {"close", "respawn", "quit"}) {
            ClientCapabilities.expect(PLAYER, step, 40L);
            ClientCapabilities.loggedOut(PLAYER);
            assertFalse(ClientCapabilities.accept(PLAYER, new ClientActionReport(step, "done", "", "", "", "", 40L)),
                    step + ": an answer with the same step and request tick after the rejoin");
        }
        ClientCapabilities.expect(PLAYER, "close", 41L);
        assertTrue(ClientCapabilities.accept(PLAYER, new ClientActionReport("close", "done", "", "", "", "", 41L)),
                "without a logout the answer is taken");
    }

    @Test
    @DisplayName("a reconnect's request survives the logout it causes")
    void reconnectKept() {
        ClientCapabilities.expect(PLAYER, "reconnect", 50L);
        ClientCapabilities.loggedOut(PLAYER);
        assertTrue(ClientCapabilities.accept(PLAYER, new ClientActionReport("reconnect", "done", "", "", "", "", 50L)));
    }

    @Test
    @DisplayName("records carry playerUuid, and connection once a hello numbered it, a new number per connection")
    void identityFields() {
        LogRecord before = LogRecord.of(EventType.CLIENT_ACTION);
        ClientCapabilities.addIdentity(before, PLAYER);
        assertEquals(PLAYER.toString(), before.getFields().get("playerUuid"));
        assertNull(before.getFields().get("connection"), "no hello yet: left out, not guessed");

        ClientCapabilities.connected(PLAYER);
        LogRecord first = LogRecord.of(EventType.CLIENT_ACTION);
        ClientCapabilities.addIdentity(first, PLAYER);
        String firstConnection = first.getFields().get("connection");
        assertTrue(firstConnection != null && Integer.parseInt(firstConnection) > 0, String.valueOf(firstConnection));

        ClientCapabilities.loggedOut(PLAYER);
        ClientCapabilities.connected(PLAYER);
        LogRecord second = LogRecord.of(EventType.CLIENT_ACTION);
        ClientCapabilities.addIdentity(second, PLAYER);
        assertNotEquals(firstConnection, second.getFields().get("connection"), "the rejoin's own number");
    }
}
