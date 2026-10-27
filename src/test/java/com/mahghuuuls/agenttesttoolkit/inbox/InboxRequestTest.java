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
