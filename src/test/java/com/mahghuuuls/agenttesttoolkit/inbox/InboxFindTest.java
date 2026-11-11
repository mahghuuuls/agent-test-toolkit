package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The read-only additions reach the inbox and the categories. */
class InboxFindTest {

    @Test
    @DisplayName("find passes through the inbox like inspect")
    void findThroughTheInbox() {
        assertEquals("devtool find block minecraft:diamond_block 0 0 0 10 10 10",
                InboxRequest.parse("a1", "find block minecraft:diamond_block 0 0 0 10 10 10").getCommandLine());
        assertFalse(InboxRequest.parse("a1", "fill 0 0 0 1 1 1 air").isAccepted(), "still no world change");
    }

    @Test
    @DisplayName("item_pickup is a category writing ITEM_PICKUP")
    void pickupCategory() {
        assertEquals(LoggingCategory.ITEM_PICKUP, LoggingCategory.byName("item_pickup"));
        assertEquals(EventType.ITEM_PICKUP, LoggingCategory.ITEM_PICKUP.getEventType());
    }
}
