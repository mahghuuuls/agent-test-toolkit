package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The allow-list. Every refused form has its own case, because this is the boundary that
 * decides whether a file an agent wrote can change the world.
 */
class InboxRequestTest {

    private static InboxRequest parse(String content) {
        return InboxRequest.parse("a1", content);
    }

    private static void assertRefused(String content, String reason) {
        InboxRequest request = parse(content);
        assertFalse(request.isAccepted(), content);
        assertEquals(reason, request.getRefusal(), content);
        assertNull(request.getCommandLine(), content);
    }

    @Test
    @DisplayName("run names exactly one bundle and becomes devtool run")
    void runAccepted() {
        InboxRequest request = parse("run v27_tolerated\n");
        assertTrue(request.isAccepted());
        assertEquals("devtool run v27_tolerated", request.getCommandLine());
        assertEquals("run", request.getVerb());
        assertEquals("a1", request.getId());
    }

    @Test
    @DisplayName("reload and save take no argument")
    void noArgumentVerbs() {
        assertEquals("devtool reload", parse("reload").getCommandLine());
        assertEquals("devtool save", parse("save\r\n").getCommandLine());
        assertRefused("reload now", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("save all", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("mark, inspect, entities and nbt pass their arguments through")
    void argumentVerbs() {
        assertEquals("devtool mark CARD_3 READY", parse("mark CARD_3 READY").getCommandLine());
        assertEquals("devtool inspect player", parse("inspect player").getCommandLine());
        assertEquals("devtool entities nearby 12", parse("entities   nearby 12").getCommandLine());
        assertEquals("devtool nbt entity @e[name=probe]", parse("nbt entity @e[name=probe]").getCommandLine());
        assertRefused("mark", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("inspect", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("a leading slash is tolerated, and the verb is case-insensitive")
    void slashAndCase() {
        assertEquals("devtool run x", parse("/run x").getCommandLine());
        assertEquals("devtool reload", parse("RELOAD").getCommandLine());
    }

    @Test
    @DisplayName("vanilla commands are refused")
    void vanillaRefused() {
        assertRefused("gamemode creative", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("say hi", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("kill @e", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("op Someone", InboxRequest.REASON_VERB_NOT_ALLOWED);
    }

    @Test
    @DisplayName("toolkit commands outside the list are refused, including a devtool prefix")
    void otherToolkitCommandsRefused() {
        assertRefused("devtool arena reset", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("arena reset", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("inbox close", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("log all off", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("fixture clear all", InboxRequest.REASON_VERB_NOT_ALLOWED);
    }

    @Test
    @DisplayName("run refuses a missing, extra, or odd bundle name")
    void runArguments() {
        assertRefused("run", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("run a b", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("run a;b", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("run ../x", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("an empty file, two lines, a control character, or a long line is malformed")
    void malformed() {
        assertRefused("", InboxRequest.REASON_MALFORMED);
        assertRefused("   ", InboxRequest.REASON_MALFORMED);
        assertRefused("\n", InboxRequest.REASON_MALFORMED);
        assertRefused("run a\nrun b", InboxRequest.REASON_MALFORMED);
        assertRefused("run a\n\n", InboxRequest.REASON_MALFORMED);
        assertRefused("run\ta", InboxRequest.REASON_MALFORMED);
        assertRefused("run aé", InboxRequest.REASON_MALFORMED);
        StringBuilder longLine = new StringBuilder("mark ");
        for (int i = 0; i < InboxRequest.MAX_LINE_LENGTH; i++) {
            longLine.append('x');
        }
        assertRefused(longLine.toString(), InboxRequest.REASON_MALFORMED);
        assertRefused(null, InboxRequest.REASON_MALFORMED);
    }

    @Test
    @DisplayName("request ids are a short safe alphabet")
    void ids() {
        assertTrue(InboxRequest.isValidId("a1"));
        assertTrue(InboxRequest.isValidId("card-03_retry.2"));
        assertFalse(InboxRequest.isValidId(""));
        assertFalse(InboxRequest.isValidId("a b"));
        assertFalse(InboxRequest.isValidId(null));
    }

    @Test
    @DisplayName("screenshot takes a safe label and an optional delay, to the operator only")
    void screenshot() {
        assertEquals("devtool screenshot inv_open", parse("screenshot inv_open").getCommandLine());
        assertEquals("devtool screenshot inv_open 200", parse("screenshot inv_open 200").getCommandLine());
        assertRefused("screenshot", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot ../x", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot a.png", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot x 20 Developer", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot x -5", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot x 1000", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("screenshot x 601", InboxRequest.REASON_BAD_ARGUMENTS);
        assertEquals("devtool screenshot x 600", parse("screenshot x 600").getCommandLine());
        assertEquals("devtool screenshot x 0", parse("screenshot x 0").getCommandLine());
    }

    @Test
    @DisplayName("client takes reconnect with 0 to 60 seconds, respawn and close, to the operator only (REQ-208)")
    void client() {
        assertEquals("devtool client respawn", parse("client respawn").getCommandLine());
        assertEquals("devtool client close", parse("client close").getCommandLine());
        assertEquals("devtool client quit", parse("client quit").getCommandLine());
        assertRefused("client quit now", InboxRequest.REASON_BAD_ARGUMENTS);
        assertEquals("devtool client reconnect", parse("client reconnect").getCommandLine());
        assertEquals("devtool client reconnect 3", parse("client reconnect 3").getCommandLine());
        assertEquals("devtool client reconnect 60", parse("client reconnect 60").getCommandLine());
        assertRefused("client", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect 99", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect 61", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect -1", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client respawn Developer", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client close now", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client move", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("client reconnect takes logout as its last word, with or without an away time")
    void reconnectLogout() {
        assertEquals("devtool client reconnect logout", parse("client reconnect logout").getCommandLine());
        assertEquals("devtool client reconnect 3 logout", parse("client reconnect 3 logout").getCommandLine());
        assertRefused("client reconnect logout 3", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect 3 logout logout", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect Logout", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client reconnect 99 logout", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client quit logout", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("client takes the player actions select 0 to 8, use and attack, with nothing else")
    void playerActions() {
        assertEquals("devtool client select 0", parse("client select 0").getCommandLine());
        assertEquals("devtool client select 8", parse("client select 8").getCommandLine());
        assertEquals("devtool client use", parse("client use").getCommandLine());
        assertEquals("devtool client attack", parse("client attack").getCommandLine());
        assertRefused("client select", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client select 9", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client select -1", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client select 01", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client select 1 2", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client use now", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client attack Developer", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client Use", InboxRequest.REASON_BAD_ARGUMENTS);
        assertEquals("devtool client hold use 20", parse("client hold use 20").getCommandLine());
        assertEquals("devtool client drop all", parse("client drop all").getCommandLine());
        assertEquals("devtool client inventory", parse("client inventory").getCommandLine());
        assertRefused("client hold use 1201", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client drop 3", InboxRequest.REASON_BAD_ARGUMENTS);
        assertEquals("devtool client use screenshot=after_use", parse("client use screenshot=after_use").getCommandLine());
        assertEquals("devtool client attack delay=40", parse("client attack delay=40").getCommandLine());
        assertRefused("client use delay=1201", InboxRequest.REASON_BAD_ARGUMENTS);
        assertRefused("client use screenshotDelay=4", InboxRequest.REASON_BAD_ARGUMENTS);
        assertEquals("devtool client tooltip 0 screenshot=tip", parse("client tooltip 0 screenshot=tip").getCommandLine());
        assertEquals("devtool client tooltip 40 advanced showTicks=40", parse("client tooltip 40 advanced showTicks=40").getCommandLine());
        assertRefused("client tooltip 41", InboxRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("a line written with the devtool root is refused with the accepted line as detail")
    void devtoolPrefix() {
        InboxRequest withRoot = parse("devtool client respawn");
        assertFalse(withRoot.isAccepted());
        assertEquals(InboxRequest.REASON_VERB_NOT_ALLOWED, withRoot.getRefusal());
        assertEquals("use:client respawn", withRoot.getDetail());
        assertEquals("use:run v42_world_prepare", parse("/devtool run v42_world_prepare").getDetail());
        assertEquals(null, parse("devtool").getDetail(), "nothing to name");
        assertEquals(null, parse("teleport x").getDetail(), "another verb: no detail");
        assertEquals(null, parse("client respawn").getDetail(), "accepted: no detail");
    }

    @Test
    @DisplayName("a UTF-8 byte order mark at the start is ignored")
    void byteOrderMark() {
        assertEquals("devtool run x", parse("﻿run x\r\n").getCommandLine());
    }

    @Test
    @DisplayName("only one leading slash is tolerated, and a space after it changes nothing")
    void slashes() {
        assertRefused("//run x", InboxRequest.REASON_VERB_NOT_ALLOWED);
        assertEquals("devtool run x", parse("/ run x").getCommandLine());
    }
}
