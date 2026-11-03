package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client-action messages: only the four steps exist, and every field is bounded (ARC-022). */
class ClientActionMessagesTest {

    @Test
    @DisplayName("only reconnect, respawn and close are steps")
    void steps() {
        assertTrue(ClientActionRequest.isValidStep("reconnect"));
        assertTrue(ClientActionRequest.isValidStep("respawn"));
        assertTrue(ClientActionRequest.isValidStep("close"));
        for (String bad : new String[] {"", "Close", "open", "attack", "use", "move", null}) {
            assertFalse(ClientActionRequest.isValidStep(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("away time is 0 to 60 seconds, and a token is at most 64 letters and digits")
    void bounds() {
        assertTrue(new ClientActionRequest("reconnect", 0, "", 5L).isValid());
        assertTrue(new ClientActionRequest("reconnect", 60, "abc123", 5L).isValid());
        assertFalse(new ClientActionRequest("reconnect", 61, "", 5L).isValid());
        assertFalse(new ClientActionRequest("reconnect", -1, "", 5L).isValid());
        assertFalse(new ClientActionRequest("reconnect", 1, "a-b", 5L).isValid());
        StringBuilder long65 = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            long65.append('a');
        }
        assertFalse(new ClientActionRequest("reconnect", 1, long65.toString(), 5L).isValid());
    }

    @Test
    @DisplayName("a request survives the wire")
    void requestRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        new ClientActionRequest("reconnect", 7, "f00d", 1234L).toBytes(buf);
        ClientActionRequest read = new ClientActionRequest();
        read.fromBytes(buf);
        assertEquals("reconnect", read.getStep());
        assertEquals(7, read.getAwaySeconds());
        assertEquals("f00d", read.getToken());
        assertEquals(1234L, read.getRequestTick());
    }

    @Test
    @DisplayName("a report survives the wire, and an oversized or unknown one is not accepted")
    void report() {
        ByteBuf buf = Unpooled.buffer();
        new ClientActionReport("close", "done", null, "net.minecraft.client.gui.inventory.GuiInventory", "none", null, 9L).toBytes(buf);
        ClientActionReport read = new ClientActionReport();
        read.fromBytes(buf);
        assertTrue(read.isValid());
        assertEquals("", read.getReason());
        assertEquals("net.minecraft.client.gui.inventory.GuiInventory", read.getScreen());
        assertEquals("none", read.getShowing());
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            big.append('x');
        }
        assertFalse(new ClientActionReport("close", "done", big.toString(), "", "", "", 1L).isValid());
        assertFalse(new ClientActionReport("close", "done", "", big.toString(), "", "", 1L).isValid());
        assertFalse(new ClientActionReport("close", "done", "", "", big.toString(), "", 1L).isValid());
        assertFalse(new ClientActionReport("close", "done", "", "", "", "a-b", 1L).isValid());
        assertFalse(new ClientActionReport("close", "whatever", "", "", "", "", 1L).isValid());
        assertFalse(new ClientActionReport("teleport", "done", "", "", "", "", 1L).isValid());
    }

    @Test
    @DisplayName("a hello survives the wire")
    void hello() {
        ByteBuf buf = Unpooled.buffer();
        new ClientHello("1.4.0", true).toBytes(buf);
        ClientHello read = new ClientHello();
        read.fromBytes(buf);
        assertEquals("1.4.0", read.getVersion());
        assertTrue(read.isAllowActions());
    }

    @Test
    @DisplayName("the server says hello only to a client whose toolkit reads it, or in single player")
    void serverSpeaksActions() {
        java.util.Map<String, String> mods = new java.util.HashMap<String, String>();
        assertTrue(ToolkitChannel.speaksActions(mods, true), "single player: this very build");
        assertFalse(ToolkitChannel.speaksActions(mods, false), "no toolkit on the client");
        mods.put("agenttesttoolkit", "1.3.0");
        assertFalse(ToolkitChannel.speaksActions(mods, false), "1.3.0 would drop the connection");
        mods.put("agenttesttoolkit", "1.4.0");
        assertTrue(ToolkitChannel.speaksActions(mods, false));
        mods.put("agenttesttoolkit", "1.10.2");
        assertTrue(ToolkitChannel.speaksActions(mods, false));
        mods.put("agenttesttoolkit", "2.0");
        assertTrue(ToolkitChannel.speaksActions(mods, false));
        mods.put("agenttesttoolkit", "1.4");
        assertTrue(ToolkitChannel.speaksActions(mods, false));
        mods.put("agenttesttoolkit", "dev");
        assertFalse(ToolkitChannel.speaksActions(mods, false), "unreadable counts as older");
        assertFalse(ToolkitChannel.speaksActions(null, false));
    }

    @Test
    @DisplayName("a hello carries a bounded version")
    void helloValidation() {
        assertTrue(new ClientHello("1.4.0", false).isValid());
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            big.append('1');
        }
        assertFalse(new ClientHello(big.toString(), true).isValid());
    }

    @Test
    @DisplayName("only a report answering the request sent is accepted, once; a reconnect's 'left' keeps it open")
    void reportsAnswerRequests() {
        ClientCapabilities.resetForTesting();
        java.util.UUID player = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        ClientActionReport closeDone = new ClientActionReport("close", "done", "", "", "", "", 40L);
        assertFalse(ClientCapabilities.accept(player, closeDone), "nothing was asked");
        ClientCapabilities.expect(player, "close", 40L);
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("close", "done", "", "", "", "", 41L)), "another request tick");
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("respawn", "done", "", "", "", "", 40L)), "another step");
        assertTrue(ClientCapabilities.accept(player, closeDone));
        assertFalse(ClientCapabilities.accept(player, closeDone), "settled; a second report is dropped");

        long now = System.currentTimeMillis();
        ClientCapabilities.expect(player, "reconnect", 50L);
        assertTrue(ClientCapabilities.isBusy(player, now, 120000L), "no other step until the leave");
        assertTrue(ClientCapabilities.accept(player, new ClientActionReport("reconnect", "left", "", "", "", "", 50L)));
        assertTrue(ClientCapabilities.isBusy(player, now, 120000L), "said it leaves; not gone yet");
        // A close asked before the reconnect's answer cannot take its place.
        ClientCapabilities.expect(player, "close", 51L);
        assertTrue(ClientCapabilities.accept(player, new ClientActionReport("close", "done", "", "", "", "", 51L)));
        assertTrue(ClientCapabilities.accept(player, new ClientActionReport("reconnect", "done", "", "", "", "", 50L)));
        assertFalse(ClientCapabilities.accept(player, new ClientActionReport("reconnect", "done", "", "", "", "", 50L)));
        assertFalse(ClientCapabilities.isBusy(player, now, 120000L));

        ClientCapabilities.expect(player, "reconnect", 60L);
        assertTrue(ClientCapabilities.accept(player,
                new ClientActionReport("reconnect", "refused", "openToLan", "", "", "", 60L)));
        assertFalse(ClientCapabilities.isBusy(player, now, 120000L), "refused: no leave is coming");

        ClientCapabilities.expect(player, "reconnect", 70L);
        ClientCapabilities.loggedOut(player);
        assertTrue(ClientCapabilities.isBusy(player, now, 120000L), "away, not yet back");
        ClientCapabilities.loggedIn(player, now);
        assertTrue(ClientCapabilities.isBusy(player, now + 5000L, 120000L), "back, the report on its way");
        assertFalse(ClientCapabilities.isBusy(player, now + 11000L, 120000L), "back by hand, no report");
        assertFalse(ClientCapabilities.isBusy(player, now + 121000L, 120000L), "a failed rejoin stops counting");
        ClientCapabilities.expect(player, "reconnect", 80L);
        assertFalse(ClientCapabilities.isBusy(player, System.currentTimeMillis() + 121000L, 120000L),
                "a client that never answers stops counting too");
        ClientCapabilities.resetForTesting();
    }

    @Test
    @DisplayName("the server's hello survives the wire, and an oversized one is not acted on")
    void serverHello() {
        ByteBuf buf = Unpooled.buffer();
        new ServerHello("1.4.0").toBytes(buf);
        ServerHello read = new ServerHello();
        read.fromBytes(buf);
        assertEquals("1.4.0", read.getVersion());
        assertTrue(read.isValid());
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            big.append('1');
        }
        assertFalse(new ServerHello(big.toString()).isValid());
    }
}
