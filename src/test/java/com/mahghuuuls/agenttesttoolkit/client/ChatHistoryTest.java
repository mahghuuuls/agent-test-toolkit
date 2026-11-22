package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hidden chat history's state as the configuration and the key change it, the record each
 * change writes, when a failure notice is shown, and which opening screens are swapped.
 */
class ChatHistoryTest {

    @Test
    @DisplayName("a game starts with the history shown; the option hides it and records by=config")
    void configAtStart() {
        ChatHistory shown = new ChatHistory();
        assertFalse(shown.isHidden(), "shown before any start");
        assertNull(shown.startFromConfig(false), "the default is no change, so nothing is recorded");
        assertFalse(shown.isHidden());

        ChatHistory hidden = new ChatHistory();
        LogRecord record = hidden.startFromConfig(true);
        assertTrue(hidden.isHidden());
        assertEquals("[DevToolkit][CLIENT_OPTION] side=CLIENT option=hideChatHistory value=true by=config",
                record.render());
    }

    @Test
    @DisplayName("each key press flips the state and records the new value with by=key")
    void keyToggles() {
        ChatHistory history = new ChatHistory();
        history.startFromConfig(true);
        LogRecord first = history.toggleByKey();
        assertFalse(history.isHidden(), "a press after the option shows the history again");
        assertEquals("false", first.getFields().get("value"));
        assertEquals("key", first.getFields().get("by"));
        assertEquals("hideChatHistory", first.getFields().get("option"));

        LogRecord second = history.toggleByKey();
        assertTrue(history.isHidden());
        assertEquals("true", second.getFields().get("value"));
        assertEquals("key", second.getFields().get("by"));
    }

    @Test
    @DisplayName("a failure notice is shown only while the history is hidden")
    void noticeGate() {
        ChatHistory history = new ChatHistory();
        assertFalse(history.showsNotice(), "with the chat readable, the line is already in sight");
        history.toggleByKey();
        assertTrue(history.showsNotice());
        history.toggleByKey();
        assertFalse(history.showsNotice());
    }

    @Test
    @DisplayName("only exactly the vanilla chat screen is swapped, and only while the history is hidden")
    void screenSwap() {
        String vanilla = net.minecraft.client.gui.GuiChat.class.getName();
        assertTrue(ChatHistory.replacesScreen(vanilla, true), "the name compared is the real class's");
        assertFalse(ChatHistory.replacesScreen(vanilla, false), "with the history shown, nothing is swapped");
        assertFalse(ChatHistory.replacesScreen(net.minecraft.client.gui.GuiSleepMP.class.getName(), true),
                "the bed screen's chat is a subclass with its own leave button");
        assertFalse(ChatHistory.replacesScreen(HistoryHiddenChat.class.getName(), true),
                "the swapped screen is not swapped again");
        assertFalse(ChatHistory.replacesScreen("some.other.mod.FancyChat", true), "another mod's chat screen");
        assertFalse(ChatHistory.replacesScreen(null, true), "a screen closing");
    }
}
