package com.mahghuuuls.agenttesttoolkit.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When the join-name warning is written: single player, and the same name in another case only. */
class HostNameListenerTest {

    @Test
    @DisplayName("the reported pair in single player warns; a dedicated server, equal names and other names do not")
    void shouldWarn() {
        assertTrue(HostNameListener.shouldWarn(false, "mahghuuuls", "Mahghuuuls"));
        assertFalse(HostNameListener.shouldWarn(true, "mahghuuuls", "Mahghuuuls"), "a dedicated server has no host");
        assertFalse(HostNameListener.shouldWarn(false, "Developer", "Developer"));
        assertFalse(HostNameListener.shouldWarn(false, "Steve", "Developer"), "a LAN guest");
        assertFalse(HostNameListener.shouldWarn(false, "Developer", null));
    }
}
