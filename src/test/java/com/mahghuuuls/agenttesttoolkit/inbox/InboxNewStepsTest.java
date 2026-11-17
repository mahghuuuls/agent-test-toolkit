package com.mahghuuuls.agenttesttoolkit.inbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The inbox's {@code client} verb takes the input and screen steps, and refuses them malformed. */
class InboxNewStepsTest {

    private static String accepted(String line) {
        InboxRequest request = InboxRequest.parse("a1", line);
        return request.isAccepted() ? request.getCommandLine() : "refused " + request.getRefusal();
    }

    @Test
    @DisplayName("each input and screen step passes through as written")
    void accepted() {
        for (String step : new String[] {"key key.jump press", "key key.forward hold 40 raw=true", "keys", "keys key.",
                "use route=queue", "attack route=event keepHold=true", "use route=binding", "look 90 0 over=20",
                "face @e[type=zombie,c=1]", "face block 1 64 2 up", "view back", "focus lost", "screen",
                "click slot 0 shift", "click button label=Save and Quit", "click at 10 20 right", "scroll -3",
                "tooltip container 5 advanced screenshot=tip", "click slot 2 key=3 expect=a.B"}) {
            assertEquals("devtool client " + step, accepted("client " + step), step);
        }
    }

    @Test
    @DisplayName("a malformed or out-of-bounds step is badArguments")
    void refused() {
        for (String step : new String[] {"key key.jump", "key key.jump hold 1201", "keys a b", "use route=mouse",
                "look 90 91", "look 0 0 over=201", "face Developer", "face block 1 300 2", "view third", "focus off",
                "screen now", "click slot 1000", "click button", "click at 1 2 shift", "scroll 0", "scroll 51",
                "tooltip container", "click slot 1 expect=a/b", "key key.jump press delay=1201"}) {
            InboxRequest request = InboxRequest.parse("a1", "client " + step);
            assertFalse(request.isAccepted(), step);
            assertEquals(InboxRequest.REASON_BAD_ARGUMENTS, request.getRefusal(), step);
        }
    }
}
