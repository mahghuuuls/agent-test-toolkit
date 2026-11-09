package com.mahghuuuls.agenttesttoolkit.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player-action message, the report's extra fields, and the 1.7.0 gate. */
class PlayerActionMessagesTest {

    @Test
    @DisplayName("a player action survives the wire, and an invalid one is not acted on")
    void requestRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        new PlayerActionRequest("select", 4, "c0ffee", 99L).toBytes(buf);
        PlayerActionRequest read = new PlayerActionRequest();
        read.fromBytes(buf);
        assertEquals("select", read.getStep());
        assertEquals(4, read.getSlot());
        assertEquals("c0ffee", read.getToken());
        assertEquals(99L, read.getRequestTick());
        assertTrue(read.isValid());
        assertTrue(read.isSelect());
        assertFalse(read.isUse());
        assertFalse(new PlayerActionRequest("select", 9, "a", 1L).isValid());
        assertFalse(new PlayerActionRequest("use", 2, "a", 1L).isValid());
        assertFalse(new PlayerActionRequest("reconnect", -1, "a", 1L).isValid());
        assertFalse(new PlayerActionRequest("use", -1, "a-b", 1L).isValid(), "a token is letters and digits");
        assertFalse(new PlayerActionRequest("use", -1, null, 1L).isValid());
    }

    @Test
    @DisplayName("player actions go only to a client that said hello with 1.7.0 or later")
    void versionGate() {
        assertFalse(ToolkitChannel.atLeast("1.6.0", ToolkitChannel.VERSION_1_7_0));
        assertFalse(ToolkitChannel.atLeast("1.6.9", ToolkitChannel.VERSION_1_7_0));
        assertTrue(ToolkitChannel.atLeast("1.7.0", ToolkitChannel.VERSION_1_7_0));
        assertTrue(ToolkitChannel.atLeast("1.10.0", ToolkitChannel.VERSION_1_7_0));
        assertFalse(ClientCapabilities.saidHelloAtLeast(null, ToolkitChannel.VERSION_1_7_0), "no hello");
    }

    private static Map<String, String> clickFields() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("target", "block");
        fields.put("targetX", "1");
        fields.put("targetY", "64");
        fields.put("targetZ", "-3");
        fields.put("face", "up");
        fields.put("targetBlock", "minecraft:stone");
        fields.put("held", "minecraft:dirt");
        fields.put("heldCount", "12");
        return fields;
    }

    @Test
    @DisplayName("a report's extra fields survive the wire in order")
    void reportFieldsRoundTrip() {
        ByteBuf buf = Unpooled.buffer();
        new ClientActionReport("use", "done", "", "none", "", "", 7L).withFields(clickFields()).toBytes(buf);
        ClientActionReport read = new ClientActionReport();
        read.fromBytes(buf);
        assertTrue(read.isValid());
        assertEquals(clickFields(), read.getFields());
        assertEquals("[target, targetX, targetY, targetZ, face, targetBlock, held, heldCount]",
                read.getFields().keySet().toString());
    }

    @Test
    @DisplayName("a report without fields keeps the layout every older server reads")
    void reportWithoutFieldsUnchanged() {
        ByteBuf plain = Unpooled.buffer();
        new ClientActionReport("close", "done", "", "x", "y", "", 7L).toBytes(plain);
        // The layout before 1.7.0: six strings and a long, nothing after.
        ByteBuf old = Unpooled.buffer();
        for (String text : new String[] {"close", "done", "", "x", "y", ""}) {
            ByteBufUtils.writeUTF8String(old, text);
        }
        old.writeLong(7L);
        assertEquals(old, plain);

        ClientActionReport read = new ClientActionReport();
        read.fromBytes(old);
        assertTrue(read.isValid());
        assertTrue(read.getFields().isEmpty());
    }

    @Test
    @DisplayName("an unknown field name, a line break in a value, or too many fields make a report invalid")
    void reportFieldBounds() {
        Map<String, String> unknown = new LinkedHashMap<String, String>();
        unknown.put("player", "someoneElse");
        assertFalse(new ClientActionReport("use", "done", "", "", "", "", 1L).withFields(unknown).isValid(),
                "a field the server writes itself");

        Map<String, String> broken = new LinkedHashMap<String, String>();
        broken.put("held", "minecraft:dirt\n[DevToolkit] FAKE");
        assertFalse(new ClientActionReport("use", "done", "", "", "", "", 1L).withFields(broken).isValid());

        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i <= ClientActionReport.MAX_FIELD_VALUE; i++) {
            longValue.append('a');
        }
        Map<String, String> tooLong = new LinkedHashMap<String, String>();
        tooLong.put("held", longValue.toString());
        assertFalse(new ClientActionReport("use", "done", "", "", "", "", 1L).withFields(tooLong).isValid());

        ByteBuf buf = Unpooled.buffer();
        new ClientActionReport("use", "done", "", "", "", "", 1L).toBytes(buf);
        buf.writeByte(ClientActionReport.MAX_FIELDS + 1);
        ClientActionReport flood = new ClientActionReport();
        flood.fromBytes(buf);
        assertFalse(flood.isValid());
    }

    @Test
    @DisplayName("a reconnect's logout is one byte after the old layout, and only when asked")
    void reconnectLogout() {
        ByteBuf asked = Unpooled.buffer();
        new ClientActionRequest("reconnect", 3, "ab12", 9L, true).toBytes(asked);
        ClientActionRequest read = new ClientActionRequest();
        read.fromBytes(asked);
        assertTrue(read.isLogout());
        assertEquals(3, read.getAwaySeconds());

        // Without it, the bytes are the ones a 1.6.0 client reads, and it reads no logout.
        ByteBuf plain = Unpooled.buffer();
        new ClientActionRequest("reconnect", 3, "ab12", 9L, false).toBytes(plain);
        ByteBuf old = Unpooled.buffer();
        new ClientActionRequest("reconnect", 3, "ab12", 9L).toBytes(old);
        assertEquals(old, plain);
        ByteBuf oldLayout = Unpooled.buffer();
        ByteBufUtils.writeUTF8String(oldLayout, "reconnect");
        oldLayout.writeInt(3);
        ByteBufUtils.writeUTF8String(oldLayout, "ab12");
        oldLayout.writeLong(9L);
        assertEquals(oldLayout, plain);
        ClientActionRequest readPlain = new ClientActionRequest();
        readPlain.fromBytes(plain);
        assertFalse(readPlain.isLogout());
    }

    @Test
    @DisplayName("a return is recognised only by the token the reconnect was sent with")
    void returnNeedsTheToken() {
        ClientCapabilities.ReturnExpected expected = new ClientCapabilities.ReturnExpected("f00d", "req-1", true, 0L);
        assertTrue(ClientCapabilities.isReturnOf(expected, "f00d"));
        assertFalse(ClientCapabilities.isReturnOf(expected, "beef"));
        assertFalse(ClientCapabilities.isReturnOf(expected, ""));
        assertFalse(ClientCapabilities.isReturnOf(null, "f00d"), "no reconnect was sent");
        assertFalse(ClientCapabilities.isReturnOf(new ClientCapabilities.ReturnExpected("", null, false, 0L), ""),
                "an empty token proves nothing");
    }

    @Test
    @DisplayName("a reconnect's awayMs and logout may be reported; other fields of the server's own may not")
    void reconnectFields() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("awayMs", "4123");
        fields.put("logout", "true");
        assertTrue(new ClientActionReport("reconnect", "done", "", "", "", "f00d", 1L).withFields(fields).isValid());
        Map<String, String> forged = new LinkedHashMap<String, String>();
        forged.put("requestId", "someone-elses");
        assertFalse(new ClientActionReport("reconnect", "done", "", "", "", "f00d", 1L).withFields(forged).isValid());
    }

    @Test
    @DisplayName("several actions asked in a row, even in one tick, are each answered once, by their own report")
    void severalActionsOutstanding() {
        ClientCapabilities.resetForTesting();
        UUID player = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ClientActionReport selectA = new ClientActionReport("select", "done", "", "", "", "aaaa", 10L);
        ClientActionReport useB = new ClientActionReport("use", "done", "", "", "", "bbbb", 10L);
        ClientActionReport useC = new ClientActionReport("use", "done", "", "", "", "cccc", 10L);
        assertFalse(ClientCapabilities.accept(player, selectA), "nothing was asked");
        ClientCapabilities.expectAction(player, "select", 10L, "aaaa");
        ClientCapabilities.expectAction(player, "use", 10L, "bbbb");
        ClientCapabilities.expectAction(player, "use", 10L, "cccc");
        assertTrue(ClientCapabilities.accept(player, useC), "the later one answers first");
        assertTrue(ClientCapabilities.accept(player, useB), "the same step in the same tick is still owed");
        assertTrue(ClientCapabilities.accept(player, selectA));
        assertFalse(ClientCapabilities.accept(player, selectA), "settled");
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("use", "done", "", "", "", "", 10L)), "no token proves nothing");
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("attack", "done", "", "", "", "dddd", 10L)), "never asked");

        for (long tick = 0; tick < ClientCapabilities.MAX_OUTSTANDING_ACTIONS + 1; tick++) {
            ClientCapabilities.expectAction(player, "attack", tick, "t" + tick);
        }
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("attack", "done", "", "", "", "t0", 0L)), "the oldest was forgotten");
        assertTrue(ClientCapabilities.accept(player,
                new ClientActionReport("attack", "done", "", "", "", "t1", 1L)));
        // An action does not take the place of a close.
        ClientCapabilities.expect(player, "close", 20L);
        ClientCapabilities.expectAction(player, "use", 20L, "eeee");
        assertTrue(ClientCapabilities.accept(player, new ClientActionReport("close", "done", "", "", "", "", 20L)));
        ClientCapabilities.resetForTesting();
    }

    @Test
    @DisplayName("the return record names the player, the request, the time away, the logout and the connection")
    void returnRecord() {
        UUID uuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        ClientCapabilities.ReturnExpected expected = new ClientCapabilities.ReturnExpected("f00d", "req-7", true, 1000L);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("awayMs", "4123");
        fields.put("logout", "true");
        ClientActionReport back = new ClientActionReport("reconnect", "done", "", "", "", "f00d", 55L).withFields(fields);
        String line = ClientCapabilities.returnRecord(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                com.mahghuuuls.agenttesttoolkit.logging.EventType.CLIENT_ACTION), expected, back, "Developer", uuid,
                "local:E:1", Integer.valueOf(3), 9000L).render();
        for (String part : new String[] {"action=returned", "player=Developer", "uuid=" + uuid, "requestId=req-7",
                "awayMs=4123", "logout=true", "remote=local:E:1", "connection=3", "requestTick=55"}) {
            assertTrue(line.contains(part), part + " in " + line);
        }

        // A client too old to measure its time away: the server's, from the request.
        ClientActionReport old = new ClientActionReport("reconnect", "done", "", "", "", "f00d", 55L);
        String fallback = ClientCapabilities.returnRecord(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                com.mahghuuuls.agenttesttoolkit.logging.EventType.CLIENT_ACTION), expected, old, "Developer", uuid,
                "local:E:1", null, 9000L).render();
        assertTrue(fallback.contains("awayMs=8000"), fallback);
        assertFalse(fallback.contains("connection="), "no hello on this connection: omitted");

        // Another token: no record at all.
        ClientActionReport wrong = new ClientActionReport("reconnect", "done", "", "", "", "beef", 55L);
        assertEquals(null, ClientCapabilities.returnRecord(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                com.mahghuuuls.agenttesttoolkit.logging.EventType.CLIENT_ACTION), expected, wrong, "Developer", uuid,
                "x", null, 9000L));
    }
}
