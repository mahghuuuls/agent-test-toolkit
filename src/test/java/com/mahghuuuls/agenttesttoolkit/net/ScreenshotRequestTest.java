package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.command.CommandOutcomes;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screenshot message, its limits, and the send decision. */
class ScreenshotRequestTest {

    @Test
    @DisplayName("a label is letters, digits, '_' and '-', 1 to 48 of them; nothing that makes a path")
    void labels() {
        assertTrue(ScreenshotRequest.isValidLabel("inv_open"));
        assertTrue(ScreenshotRequest.isValidLabel("A-1"));
        for (String bad : new String[] {"", "a b", "a/b", "a\\b", "..", "a.png", "x:y", null,
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvw"}) {
            assertFalse(ScreenshotRequest.isValidLabel(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("a delay is 0 to 600 ticks")
    void delays() {
        assertTrue(ScreenshotRequest.isValidDelay(0));
        assertTrue(ScreenshotRequest.isValidDelay(600));
        assertFalse(ScreenshotRequest.isValidDelay(601));
        assertFalse(ScreenshotRequest.isValidDelay(-1));
    }

    @Test
    @DisplayName("the message survives the wire, and names its file after the label and tick")
    void roundTrip() {
        ScreenshotRequest sent = new ScreenshotRequest("inv_open", 200, 1234L);
        ByteBuf buf = Unpooled.buffer();
        sent.toBytes(buf);
        ScreenshotRequest read = new ScreenshotRequest();
        read.fromBytes(buf);

        assertEquals("inv_open", read.getLabel());
        assertEquals(200, read.getDelayTicks());
        assertEquals(1234L, read.getRequestTick());
        assertEquals("inv_open-1234.png", read.fileName());
        assertTrue(read.isValid());
        assertFalse(new ScreenshotRequest("../x", 0, 1L).isValid(), "the client re-checks, too");
    }

    @Test
    @DisplayName("only a client listing the toolkit, or the integrated server's own host, is sent anything")
    void sendDecision() {
        Map<String, String> withToolkit = new HashMap<String, String>();
        withToolkit.put("agenttesttoolkit", "1.3.0");
        withToolkit.put("forge", "14.23.5.2860");
        Map<String, String> without = Collections.singletonMap("forge", "14.23.5.2860");

        assertTrue(ToolkitChannel.hasToolkit(withToolkit, false));
        assertFalse(ToolkitChannel.hasToolkit(without, false));
        assertFalse(ToolkitChannel.hasToolkit(Collections.<String, String>emptyMap(), false), "a vanilla client");
        assertFalse(ToolkitChannel.hasToolkit(null, false));
        assertTrue(ToolkitChannel.hasToolkit(null, true), "the host of single player shares the process");
    }

    @Test
    @DisplayName("the no-client key is a failure, tolerable when declared")
    void key() {
        String key = CommandOutcomes.KEY_SCREENSHOT_NO_CLIENT;
        assertEquals("devtool.screenshot.noClient", key);
        assertFalse(CommandOutcomes.classify(0, key).succeeded());
        CommandOutcome declared = CommandOutcomes.classify(0, key, Collections.singleton(key));
        assertTrue(declared.succeeded());
    }
}
