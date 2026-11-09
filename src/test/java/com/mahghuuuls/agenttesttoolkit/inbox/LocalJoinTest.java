package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.ALREADY_OPEN;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.KEYS_OFF;
import static com.mahghuuuls.agenttesttoolkit.inbox.LocalJoin.Decision.NOT_HOST;
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
        assertEquals(OPEN, LocalJoin.decideDedicated(true, true, false, true, true), "secret returned");
        assertEquals(KEYS_OFF, LocalJoin.decideDedicated(false, true, false, true, true), "openOnLocalJoin off");
        assertEquals(KEYS_OFF, LocalJoin.decideDedicated(true, false, false, true, true), "inbox disabled");
        assertEquals(ALREADY_OPEN, LocalJoin.decideDedicated(true, true, true, true, true));
        assertEquals(NOT_LOCAL, LocalJoin.decideDedicated(true, true, false, false, true), "no proof, or a tunnel");
        assertEquals(NOT_OPERATOR, LocalJoin.decideDedicated(true, true, false, true, false));
    }

    @Test
    @DisplayName("the host is the in-process player whose name equals the owner ignoring case")
    void host() {
        assertTrue(LocalJoin.isHost(true, "Mahghuuuls", "mahghuuuls"),
                "the launch name as typed against the profile name, the case reported from a real launch");
        assertTrue(LocalJoin.isHost(true, "Developer", "Developer"));
        assertFalse(LocalJoin.isHost(true, "Mahghuuuls", "mahghuuul"), "a different name");
        assertFalse(LocalJoin.isHost(true, "Alex", "Developer"));
        assertFalse(LocalJoin.isHost(false, "Developer", "Developer"), "a network join is never the host");
        assertFalse(LocalJoin.isHost(true, null, "Developer"));
        assertFalse(LocalJoin.isHost(true, "Developer", null));
    }

    @Test
    @DisplayName("a single-player join opens for the host in any case, and an in-process player who is not the host is refused")
    void singlePlayerJoin() {
        assertEquals(OPEN, LocalJoin.decideSinglePlayer(true, true, false, true, "Mahghuuuls", "mahghuuuls", true),
                "the case reported from a real launch");
        assertEquals(NOT_HOST, LocalJoin.decideSinglePlayer(true, true, false, true, "Alex", "Developer", true));
        assertEquals(NOT_HOST, LocalJoin.decideSinglePlayer(true, true, false, true, "Alex", "Developer", false),
                "the name is checked before the permission");
        assertEquals(NOT_OPERATOR, LocalJoin.decideSinglePlayer(true, true, false, true, "Developer", "Developer", false),
                "cheats off");
        assertEquals(NOT_LOCAL, LocalJoin.decideSinglePlayer(true, true, false, false, "Developer", "Developer", true),
                "a LAN guest, even with the owner's name");
        assertEquals(KEYS_OFF, LocalJoin.decideSinglePlayer(false, true, false, true, "Alex", "Developer", true),
                "nothing written with a key off");
        assertEquals(KEYS_OFF, LocalJoin.decideSinglePlayer(true, false, false, true, "Alex", "Developer", true));
        assertEquals(ALREADY_OPEN, LocalJoin.decideSinglePlayer(true, true, true, true, "Alex", "Developer", true),
                "nothing changes while it is open");
    }

    @Test
    @DisplayName("the owner name matches ignoring case, the rule the stop and the join share")
    void ownerName() {
        assertTrue(LocalJoin.isOwnerName("Mahghuuuls", "mahghuuuls"));
        assertTrue(LocalJoin.isOwnerName("DEVELOPER", "Developer"));
        assertFalse(LocalJoin.isOwnerName("Developer2", "Developer"));
        assertFalse(LocalJoin.isOwnerName(null, "Developer"));
        assertFalse(LocalJoin.isOwnerName("Developer", null));
    }

    @Test
    @DisplayName("at the single-player stop the host's opening is the host's in any case; another name is a guest's")
    void hostAtStop() {
        assertTrue(LocalJoin.isHostAtStop("mahghuuuls", "Mahghuuuls"), "kept across a reconnect");
        assertTrue(LocalJoin.isHostAtStop("Developer", "Developer"));
        assertFalse(LocalJoin.isHostAtStop("Developer", "Alex"), "a LAN guest's opening closes");
        assertFalse(LocalJoin.isHostAtStop(null, "Developer"), "no owner: the opening closes");
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

    @Test
    @DisplayName("a warning for the same name in another case only: the reported pair, not equal names, not other names")
    void nameCase() {
        assertTrue(LocalJoin.nameCaseDiffers("Mahghuuuls", "mahghuuuls"));
        assertTrue(LocalJoin.nameCaseDiffers("mahghuuuls", "Mahghuuuls"));
        assertFalse(LocalJoin.nameCaseDiffers("Developer", "Developer"));
        assertFalse(LocalJoin.nameCaseDiffers("Steve", "Developer"));
        assertFalse(LocalJoin.nameCaseDiffers(null, "Developer"));
        assertFalse(LocalJoin.nameCaseDiffers("Developer", null));
    }

    @Test
    @DisplayName("operator rights only for a proven join with every key on, on an offline-mode server, to a player not in the operator list")
    void grant() {
        assertEquals(LocalJoin.Grant.GRANT, LocalJoin.decideGrant(true, true, true, true, false, false, false));
        assertEquals(LocalJoin.Grant.KEEP, LocalJoin.decideGrant(true, true, true, true, true, false, false),
                "in the list at any level: left as is");
        assertEquals(LocalJoin.Grant.KEEP, LocalJoin.decideGrant(true, true, true, true, true, false, true),
                "an operator already, on an online-mode server: nothing to give");
        assertEquals(LocalJoin.Grant.ONLINE_MODE, LocalJoin.decideGrant(true, true, true, true, false, false, true));
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(true, true, true, true, true, true, false),
                "given by the toolkit in this run: not kept, not given twice");
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(false, true, true, true, false, false, false), "grant key off");
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(true, false, true, true, false, false, false), "openOnLocalJoin off");
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(true, true, false, true, false, false, false), "inbox off");
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(true, true, true, false, false, false, false), "no proof");
        assertEquals(LocalJoin.Grant.NONE, LocalJoin.decideGrant(true, true, true, false, false, false, true), "no proof, online");
    }
}
