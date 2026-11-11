package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** hide on the screenshot line and screenshotHide on a player action, through the inbox. */
class ScreenshotHideOptionsTest {

    @Test
    @DisplayName("screenshot takes hide=chat or hide=hud last, and nothing else")
    void screenshotLine() {
        assertEquals("devtool screenshot s1 hide=chat", InboxRequest.parse("a1", "screenshot s1 hide=chat").getCommandLine());
        assertEquals("devtool screenshot s1 20 hide=hud", InboxRequest.parse("a1", "screenshot s1 20 hide=hud").getCommandLine());
        assertFalse(InboxRequest.parse("a1", "screenshot s1 hide=all").isAccepted());
        assertFalse(InboxRequest.parse("a1", "screenshot hide=chat").isAccepted(), "a label first");
        assertFalse(InboxRequest.parse("a1", "screenshot s1 hide=chat 20").isAccepted(), "hide last");
    }

    @Test
    @DisplayName("screenshotHide needs a screenshot and is chat or hud")
    void actionOption() {
        PlayerActionOptions action = PlayerActionOptions.parse("use screenshot=shot screenshotHide=hud".split(" "));
        assertNotNull(action);
        assertEquals("hud", action.getScreenshotHide());
        assertNull(PlayerActionOptions.parse("use screenshotHide=chat".split(" ")));
        assertNull(PlayerActionOptions.parse("use screenshot=shot screenshotHide=all".split(" ")));
        assertEquals("", PlayerActionOptions.parse("use screenshot=shot".split(" ")).getScreenshotHide());
    }
}
