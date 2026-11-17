package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Facts;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Refusal;
import com.mahghuuuls.agenttesttoolkit.command.sub.ClientRequestGate.Step;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Leaving the toasts out, or more than one thing, needs a 1.9.0 client; chat or hud alone still 1.8.0. */
class ClientRequestGateToastsTest {

    private static Facts client(String version) {
        return new Facts(false, false, true, Boolean.TRUE, version);
    }

    @Test
    @DisplayName("the version a hide set needs, the one owner of it for the command and the actions")
    void hideVersion() {
        assertNull(ClientRequestGate.hideVersion(""));
        assertNull(ClientRequestGate.hideVersion(null));
        assertArrayEquals(ToolkitChannel.VERSION_1_8_0, ClientRequestGate.hideVersion("chat"));
        assertArrayEquals(ToolkitChannel.VERSION_1_8_0, ClientRequestGate.hideVersion("hud"));
        assertArrayEquals(ToolkitChannel.VERSION_1_9_0, ClientRequestGate.hideVersion("toasts"));
        assertArrayEquals(ToolkitChannel.VERSION_1_9_0, ClientRequestGate.hideVersion("chat,hud"),
                "a list an older client cannot read");
        assertArrayEquals(ToolkitChannel.VERSION_1_9_0, ClientRequestGate.hideVersion("chat,toasts"));
    }

    @Test
    @DisplayName("an action's screenshotHide=toasts is refused to a 1.8.0 client and sent to a 1.9.0 one")
    void actionGate() {
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal(Step.playerAction("use", "toasts"), client("1.8.0")));
        assertNull(ClientRequestGate.refusal(Step.playerAction("use", "toasts"), client("1.9.0")));
        assertNull(ClientRequestGate.refusal(Step.playerAction("use", "chat"), client("1.8.0")), "1.8.0 values stay");
        assertEquals(Refusal.CLIENT_TOO_OLD, ClientRequestGate.refusal(Step.playerAction("use", "chat"), client("1.7.0")));
        assertNull(ClientRequestGate.refusal(Step.playerAction("use", ""), client("1.7.0")));
        assertArrayEquals(ToolkitChannel.VERSION_1_7_0, ClientRequestGate.neededVersion(Step.playerAction("use", "")));
    }

    @Test
    @DisplayName("the refusal names the client's version and the one needed, in the argument an older client shows")
    void wording() {
        Object[] step = ClientRequestGate.arguments(Refusal.CLIENT_TOO_OLD, "Dev", "1.8.0",
                Step.playerAction("use", "chat,toasts"));
        assertEquals("Dev (client toolkit 1.8.0, this step needs 1.9.0)", step[0]);
        assertEquals("1.8.0", step[1]);
        assertEquals("1.9.0", step[2]);
        Object[] picture = ClientRequestGate.versionArguments("Dev", "1.8.0", ClientRequestGate.hideVersion("toasts"),
                "this screenshot");
        assertEquals("Dev (client toolkit 1.8.0, this screenshot needs 1.9.0)", picture[0]);
        Object[] none = ClientRequestGate.versionArguments("Dev", null, ToolkitChannel.VERSION_1_8_0, "this screenshot");
        assertEquals("Dev (client toolkit none, this screenshot needs 1.8.0)", none[0]);
    }
}
