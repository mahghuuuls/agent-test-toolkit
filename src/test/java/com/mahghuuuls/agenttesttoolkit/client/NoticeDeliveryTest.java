package com.mahghuuuls.agenttesttoolkit.client;

import com.mahghuuuls.agenttesttoolkit.net.PromptPanelMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A failure notice reaching the prompt panel: shown only while the chat history is hidden, and
 * never taking the place of a checkpoint the person may be waited on.
 */
class NoticeDeliveryTest {

    @Test
    @DisplayName("a notice is shown while the history is hidden and ignored while it is shown")
    void gateFollowsTheHistory() {
        ChatHistory history = new ChatHistory();
        PromptPanel panel = new PromptPanel();
        ChatHistoryListener.deliver(history, panel, "[DevToolkit] failed", 200);
        assertNull(panel.noticeText(), "the line is readable in the chat");

        history.toggleByKey();
        ChatHistoryListener.deliver(history, panel, "[DevToolkit] failed", 200);
        assertEquals("[DevToolkit] failed", panel.noticeText());
    }

    @Test
    @DisplayName("the work a received notice hands to the client thread delivers it")
    void receivedNoticeIsDelivered() {
        ChatHistory history = new ChatHistory();
        history.startFromConfig(true);
        PromptPanel panel = new PromptPanel();
        ChatHistoryListener.deliveryOf(history, panel, com.mahghuuuls.agenttesttoolkit.net.ToolkitNotice.failure("x")).run();
        assertEquals("x", panel.noticeText());
    }

    @Test
    @DisplayName("a notice keeps the checkpoint shown, and a newer checkpoint keeps the notice")
    void noticeAndCheckpointStack() {
        ChatHistory history = new ChatHistory();
        history.startFromConfig(true);
        PromptPanel panel = new PromptPanel();
        panel.apply(PromptPanelMessage.show("t1", "READY", Collections.singletonList("Type /devtool checkpoint ack"), 0));
        ChatHistoryListener.deliver(history, panel, "[DevToolkit] failed", 200);
        assertEquals("READY", panel.checkpointLabel(), "the notice did not wipe the checkpoint");
        assertEquals("[DevToolkit] failed", panel.noticeText());

        panel.apply(PromptPanelMessage.show("t2", "NEXT", Collections.<String>emptyList(), 0));
        assertEquals("NEXT", panel.checkpointLabel());
        assertEquals("[DevToolkit] failed", panel.noticeText(), "a checkpoint does not wipe the notice");
        panel.apply(PromptPanelMessage.hide("t2"));
        assertNull(panel.checkpointLabel());
        assertEquals("[DevToolkit] failed", panel.noticeText(), "nor does its hide");
    }
}
