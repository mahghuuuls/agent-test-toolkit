package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a screenshot leaves out is a set: chat, hud, toasts, alone or as a comma list; the 1.8.0 values read the same. */
class HideSetTest {

    @Test
    @DisplayName("one value or a comma list, written back in a fixed order; anything else is no set")
    void canonical() {
        assertEquals("chat", HideSet.canonical("chat"), "a 1.8.0 value stays as it was");
        assertEquals("hud", HideSet.canonical("hud"));
        assertEquals("toasts", HideSet.canonical("toasts"));
        assertEquals("chat,toasts", HideSet.canonical("chat,toasts"));
        assertEquals("chat,toasts", HideSet.canonical("toasts,chat"));
        assertEquals("chat,hud,toasts", HideSet.canonical("toasts,hud,chat"));
        assertEquals("", HideSet.canonical(""));
        assertNull(HideSet.canonical(null));
        assertNull(HideSet.canonical("all"));
        assertNull(HideSet.canonical("chat,chat"), "a value twice");
        assertNull(HideSet.canonical("chat,"), "an empty item");
        assertNull(HideSet.canonical(",chat"));
        assertNull(HideSet.canonical("Chat"), "exact names");
        assertNull(HideSet.canonical("chat toasts"));
    }

    @Test
    @DisplayName("a set names its values")
    void has() {
        assertTrue(HideSet.has("chat,toasts", "toasts"));
        assertTrue(HideSet.has("chat,toasts", "chat"));
        assertFalse(HideSet.has("chat,toasts", "hud"));
        assertFalse(HideSet.has("", "chat"));
        assertFalse(HideSet.has(null, "chat"));
        assertTrue(HideSet.isValid(""), "nothing left out is a set");
        assertTrue(HideSet.isValid("toasts,chat"));
        assertFalse(HideSet.isValid("chat,chat"));
        assertFalse(HideSet.isValid(null));
    }

    @Test
    @DisplayName("an action's screenshotHide takes the set, needs a screenshot, and is written one way")
    void action() {
        PlayerActionOptions action = PlayerActionOptions.parse("use screenshot=s screenshotHide=toasts,chat".split(" "));
        assertNotNull(action);
        assertEquals("chat,toasts", action.getScreenshotHide());
        assertNull(PlayerActionOptions.parse("use screenshotHide=toasts".split(" ")), "without a picture");
        assertNull(PlayerActionOptions.parse("use screenshot=s screenshotHide=toasts,toasts".split(" ")));
        InputOptions key = InputOptions.parse("key key.jump press screenshot=s screenshotHide=toasts".split(" "));
        assertNotNull(key);
        assertEquals("toasts", key.getScreenshotHide());
        ScreenOptions click = ScreenOptions.parse("click slot 0 screenshot=s screenshotHide=hud,toasts".split(" "));
        assertNotNull(click);
        assertEquals("hud,toasts", click.getScreenshotHide());
        assertTrue(click.toWords().contains("screenshotHide=hud,toasts"), "the words the client parses again");
    }

    @Test
    @DisplayName("the inbox's screenshot line takes the set as its last word")
    void inboxLine() {
        assertEquals("devtool screenshot s1 hide=chat,toasts",
                InboxRequest.parse("a1", "screenshot s1 hide=chat,toasts").getCommandLine());
        assertEquals("devtool screenshot s1 0 hide=toasts",
                InboxRequest.parse("a1", "screenshot s1 0 hide=toasts").getCommandLine());
        assertFalse(InboxRequest.parse("a1", "screenshot s1 hide=chat,all").isAccepted());
        assertFalse(InboxRequest.parse("a1", "screenshot s1 hide=").isAccepted());
        assertTrue(InboxRequest.parse("a1", "client use screenshot=s screenshotHide=chat,toasts").isAccepted());
    }
}
