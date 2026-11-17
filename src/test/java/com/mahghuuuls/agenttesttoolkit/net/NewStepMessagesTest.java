package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.inbox.InputOptions;
import com.mahghuuuls.agenttesttoolkit.inbox.ScreenOptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The three messages a 1.9.0 client reads, and the answers to their steps. */
class NewStepMessagesTest {

    @AfterEach
    void reset() {
        ClientCapabilities.resetForTesting();
    }

    private static InputRequest wireInput(InputRequest sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        InputRequest read = new InputRequest();
        read.fromBytes(buf);
        assertFalse(buf.isReadable(), "every byte read");
        return read;
    }

    private static ScreenRequest wireScreen(ScreenRequest sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        ScreenRequest read = new ScreenRequest();
        read.fromBytes(buf);
        assertFalse(buf.isReadable(), "every byte read");
        return read;
    }

    private static PromptPanelMessage wirePanel(PromptPanelMessage sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        PromptPanelMessage read = new PromptPanelMessage();
        read.fromBytes(buf);
        assertFalse(buf.isReadable(), "every byte read");
        return read;
    }

    @Test
    @DisplayName("an input step survives the wire with its options, token, tick and aim")
    void inputRoundTrip() {
        InputOptions options = InputOptions.parse("key key.forward hold 40 raw=true delay=2".split(" "));
        InputRequest read = wireInput(new InputRequest(options, "c0ffee", 99L));
        assertTrue(read.isValid());
        assertEquals("key", read.getStep());
        assertEquals("key.forward", read.getOptions().getName());
        assertEquals(40, read.getOptions().getTicks());
        assertTrue(read.getOptions().isRaw());
        assertEquals(2, read.getOptions().getDelay());
        assertEquals("c0ffee", read.getToken());
        assertEquals(99L, read.getRequestTick());
        assertFalse(read.isAimed());

        InputOptions face = InputOptions.parse("face @e[type=zombie,c=1]".split(" "));
        InputRequest aimed = wireInput(new InputRequest(face, "beef", 7L).withAim(1.5, 64.62, -3.5));
        assertTrue(aimed.isValid());
        assertTrue(aimed.isAimed());
        assertEquals(64.62, aimed.getAimY());
        assertEquals("@e[type=zombie,c=1]", aimed.getOptions().getName());
        assertFalse(new InputRequest(face, "beef", 7L).withAim(Double.NaN, 0, 0).isValid());
    }

    @Test
    @DisplayName("an input message whose words are not a step, or whose token is wrong, is not acted on")
    void inputInvalid() {
        assertFalse(wireInput(new InputRequest(new String[] {"key", "key.jump", "tap"}, "a", 1L)).isValid());
        assertFalse(wireInput(new InputRequest(new String[] {"reconnect"}, "a", 1L)).isValid());
        assertFalse(new InputRequest(new String[] {"view", "back"}, "a-b", 1L).isValid(), "a token is letters and digits");
        assertTrue(new InputRequest(new String[] {"view", "back"}, "a", 1L).isValid());
        // A word list longer than any step is not read at all.
        ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeVarInt(buf, InputRequest.MAX_WORDS + 1, 5);
        for (int i = 0; i <= InputRequest.MAX_WORDS; i++) {
            ByteBufUtils.writeUTF8String(buf, "view");
        }
        InputRequest flooded = new InputRequest();
        flooded.fromBytes(buf);
        assertFalse(flooded.isValid());
        assertNull(flooded.getOptions());
    }

    @Test
    @DisplayName("a screen step survives the wire, a label with spaces included")
    void screenRoundTrip() {
        ScreenOptions options = ScreenOptions.parse("click button label=Save and Quit expect=a.B".split(" "));
        ScreenRequest read = wireScreen(new ScreenRequest(options, "abc123", 5L));
        assertTrue(read.isValid());
        assertEquals("click", read.getStep());
        assertEquals("Save and Quit", read.getOptions().getLabel());
        assertEquals("a.B", read.getOptions().getExpect());
        assertEquals("abc123", read.getToken());
        assertEquals(5L, read.getRequestTick());
        ScreenRequest tooltip = wireScreen(new ScreenRequest(
                ScreenOptions.parse("tooltip container 3 advanced".split(" ")), "a", 1L));
        assertTrue(tooltip.isValid());
        assertEquals("tooltip", tooltip.getStep());
        assertFalse(wireScreen(new ScreenRequest(new String[] {"tooltip", "3"}, "a", 1L)).isValid(),
                "the inventory tooltip is not a screen step");
        assertFalse(wireScreen(new ScreenRequest(new String[] {"scroll", "0"}, "a", 1L)).isValid());
    }

    @Test
    @DisplayName("the prompt panel's show and hide survive the wire, and a panel out of bounds is not shown")
    void panelRoundTrip() {
        PromptPanelMessage show = wirePanel(PromptPanelMessage.show("t1", "open the chest",
                Arrays.asList("Then type", "/devtool checkpoint ack"), 600));
        assertTrue(show.isValid());
        assertTrue(show.isShow());
        assertEquals("t1", show.getToken());
        assertEquals("open the chest", show.getLabel());
        assertEquals(Arrays.asList("Then type", "/devtool checkpoint ack"), show.getLines());
        assertEquals(600, show.getTimeoutTicks());
        PromptPanelMessage hide = wirePanel(PromptPanelMessage.hide("t1"));
        assertTrue(hide.isValid());
        assertFalse(hide.isShow());
        assertEquals("t1", hide.getToken());

        assertFalse(PromptPanelMessage.show("", "x", null, 0).isValid(), "a token");
        assertFalse(PromptPanelMessage.show("t", "", null, 0).isValid(), "a label");
        assertFalse(PromptPanelMessage.show("t", "a\nb", null, 0).isValid(), "no line break in the label");
        assertFalse(PromptPanelMessage.show("t", "x", null, -1).isValid());
        assertFalse(PromptPanelMessage.show("t", "x", null, PromptPanelMessage.MAX_TIMEOUT_TICKS + 1).isValid());
        assertFalse(PromptPanelMessage.show("t", "x", Collections.nCopies(PromptPanelMessage.MAX_LINES + 1, "l"), 0)
                .isValid());
        assertTrue(PromptPanelMessage.show("t", "x", Collections.nCopies(PromptPanelMessage.MAX_LINES, "l"), 0).isValid());
        char[] longLine = new char[PromptPanelMessage.MAX_LINE + 1];
        Arrays.fill(longLine, 'x');
        assertFalse(PromptPanelMessage.show("t", "x", Collections.singletonList(new String(longLine)), 0).isValid());
        PromptPanelMessage flooded = wirePanelUnchecked(PromptPanelMessage.show("t", "x",
                Collections.nCopies(PromptPanelMessage.MAX_LINES + 1, "l"), 0));
        assertFalse(flooded.isValid());
    }

    /** Over the wire without the every-byte check, for a message the reader stops reading. */
    private static PromptPanelMessage wirePanelUnchecked(PromptPanelMessage sent) {
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        PromptPanelMessage read = new PromptPanelMessage();
        read.fromBytes(buf);
        return read;
    }

    @Test
    @DisplayName("each new step's answer is accepted once, by its token, and the leave's fields may be reported")
    void answers() {
        UUID player = UUID.randomUUID();
        for (String step : new String[] {"key", "keys", "look", "face", "view", "focus", "screen", "click", "scroll"}) {
            ClientCapabilities.expectAction(player, step, 10L, "tok");
            ClientActionReport report = new ClientActionReport(step, "refused", "notImplemented", "none", null, "tok", 10L);
            assertTrue(report.isValid(), step);
            assertTrue(ClientCapabilities.accept(player, report), step);
            assertFalse(ClientCapabilities.accept(player, report), "answered once: " + step);
        }
        assertFalse(ClientCapabilities.accept(player,
                new ClientActionReport("look", "refused", "notImplemented", "none", null, "other", 10L)));
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("leave", "paused");
        fields.put("leaveWaitMs", "120");
        assertTrue(new ClientActionReport("reconnect", "done", null, null, null, "tok", 1L).withFields(fields).isValid());
        assertFalse(new ClientActionReport("move", "done", null, null, null, "tok", 1L).isValid());
    }

    @Test
    @DisplayName("the return record carries how the paused leave went, when the client reported it")
    void returnRecordCarriesTheLeave() {
        ClientCapabilities.ReturnExpected expected = new ClientCapabilities.ReturnExpected("tok", "req1", true, 0L);
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("awayMs", "3000");
        fields.put("logout", "true");
        fields.put("leave", "paused");
        fields.put("leaveWaitMs", "85");
        ClientActionReport report = new ClientActionReport("reconnect", "done", null, null, null, "tok", 1L)
                .withFields(fields);
        String line = ClientCapabilities.returnRecord(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                com.mahghuuuls.agenttesttoolkit.logging.EventType.CLIENT_ACTION), expected, report, "Dev",
                UUID.randomUUID(), "local", Integer.valueOf(1), 5000L).render();
        assertTrue(line.contains("leave=paused"), line);
        assertTrue(line.contains("leaveWaitMs=85"), line);
        String without = ClientCapabilities.returnRecord(com.mahghuuuls.agenttesttoolkit.logging.LogRecord.of(
                com.mahghuuuls.agenttesttoolkit.logging.EventType.CLIENT_ACTION), expected,
                new ClientActionReport("reconnect", "done", null, null, null, "tok", 1L), "Dev",
                UUID.randomUUID(), "local", Integer.valueOf(1), 5000L).render();
        assertFalse(without.contains("leave="), without);
    }

    @Test
    @DisplayName("a panel whose bytes claim a huge line count is rejected at decode, without reading or keeping the lines")
    void panelHugeCountRejected() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBoolean(true);
        ByteBufUtils.writeUTF8String(buf, "t1");
        ByteBufUtils.writeUTF8String(buf, "label");
        ByteBufUtils.writeVarInt(buf, Integer.MAX_VALUE, 5);
        ByteBufUtils.writeUTF8String(buf, "one line only");
        PromptPanelMessage read = new PromptPanelMessage();
        read.fromBytes(buf);
        assertFalse(read.isValid());
        assertTrue(read.getLines().size() <= 1, "no line list of the claimed size");
    }

    @Test
    @DisplayName("an input or screen message whose bytes claim a huge word count is rejected at decode")
    void hugeWordCountRejected() {
        ByteBuf buf = Unpooled.buffer();
        ByteBufUtils.writeVarInt(buf, Integer.MAX_VALUE, 5);
        ByteBufUtils.writeUTF8String(buf, "view");
        InputRequest input = new InputRequest();
        input.fromBytes(buf);
        assertFalse(input.isValid());
        assertFalse(input.isAnswerable());
        ByteBuf screenBuf = Unpooled.buffer();
        ByteBufUtils.writeVarInt(screenBuf, InputRequest.MAX_WORDS + 1, 5);
        ScreenRequest screen = new ScreenRequest();
        screen.fromBytes(screenBuf);
        assertFalse(screen.isValid());
        assertFalse(screen.isAnswerable());
    }

    @Test
    @DisplayName("a step whose words do not parse is still answerable by its token, so the client can refuse it")
    void unparsableStepIsAnswerable() {
        InputRequest bad = wireInput(new InputRequest(new String[] {"key", "key.jump", "tap"}, "abc", 3L));
        assertFalse(bad.isValid());
        assertTrue(bad.isAnswerable());
        assertEquals("key", bad.getStep());
        ScreenRequest badScreen = wireScreen(new ScreenRequest(new String[] {"scroll", "0"}, "abc", 3L));
        assertTrue(badScreen.isAnswerable());
        assertEquals("scroll", badScreen.getStep());
        assertFalse(new InputRequest(new String[] {"view", "back"}, "a-b", 1L).isAnswerable(), "no usable token");
        char[] longWord = new char[100];
        java.util.Arrays.fill(longWord, 'k');
        assertEquals(64, new InputRequest(new String[] {new String(longWord)}, "a", 1L).getStep().length());
    }

    @Test
    @DisplayName("the channel sends the 1.9.0 messages only to a client that said hello with 1.9.0 or later")
    void channelSendRule() {
        assertTrue(ToolkitChannel.readsNewMessages("1.9.0"));
        assertTrue(ToolkitChannel.readsNewMessages("2.0.0"));
        assertFalse(ToolkitChannel.readsNewMessages("1.8.0"));
        assertFalse(ToolkitChannel.readsNewMessages("1.7.0"));
        assertFalse(ToolkitChannel.readsNewMessages(null), "no hello");
        assertFalse(ToolkitChannel.readsNewMessages("x"));
    }

    @Test
    @DisplayName("the version the new messages need reads as 1.9.0, and a 1.8.0 hello is older")
    void version() {
        assertEquals("1.9.0", ToolkitChannel.versionText(ToolkitChannel.VERSION_1_9_0));
        assertTrue(ToolkitChannel.atLeast("1.9.0", ToolkitChannel.VERSION_1_9_0));
        assertTrue(ToolkitChannel.atLeast("1.10.0", ToolkitChannel.VERSION_1_9_0));
        assertFalse(ToolkitChannel.atLeast("1.8.0", ToolkitChannel.VERSION_1_9_0));
        assertArrayEquals(new int[] {1, 9, 0}, ToolkitChannel.VERSION_1_9_0);
    }
}
