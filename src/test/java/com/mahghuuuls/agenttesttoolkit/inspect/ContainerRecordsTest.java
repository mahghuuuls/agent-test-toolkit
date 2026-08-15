package com.mahghuuuls.agenttesttoolkit.inspect;

import java.util.ArrayList;
import java.util.List;

import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a container's slots become records.
 *
 * <p>The shaping is tested rather than the reading, because reading a container needs a world
 * and the shaping is where a reader can be misled. Two properties matter more than the rest: an
 * empty container must say so rather than say nothing, and a truncated listing must not look
 * complete.
 */
class ContainerRecordsTest {

    private static final BlockPos POS = new BlockPos(10, 64, -3);
    private static final String BLOCK = "minecraft:chest";

    private static List<Inventories.Slot> slots(int count) {
        List<Inventories.Slot> slots = new ArrayList<Inventories.Slot>();
        for (int i = 0; i < count; i++) {
            slots.add(new Inventories.Slot(null, i, "minecraft:stone", 1, 0, false));
        }
        return slots;
    }

    @Test
    @DisplayName("an empty container reports zero rather than emitting nothing")
    void emptyContainerSaysSo() {
        List<LogRecord> records = Inventories.container(BLOCK, POS, slots(0));

        assertEquals(1, records.size(),
                "emitting nothing would be indistinguishable from the command not running");
        String rendered = records.get(0).render();
        assertTrue(rendered.contains("occupiedSlots=0"), rendered);
        assertTrue(rendered.contains("block=minecraft:chest"), rendered);
        assertTrue(rendered.contains("posX=10"), rendered);
    }

    @Test
    @DisplayName("one record per occupied slot, each carrying the total")
    void oneRecordPerSlot() {
        List<LogRecord> records = Inventories.container(BLOCK, POS, slots(3));

        assertEquals(3, records.size());
        for (LogRecord record : records) {
            // Carried on every record so a line read in isolation still says how many there
            // were, which is what makes a partial read detectable.
            assertTrue(record.render().contains("occupiedSlots=3"), record.render());
        }
    }

    @Test
    @DisplayName("slot index, item, count and metadata all reach the record")
    void slotDetailIsReported() {
        List<Inventories.Slot> one = new ArrayList<Inventories.Slot>();
        one.add(new Inventories.Slot(null, 7, "minecraft:diamond", 5, 3, true));

        String rendered = Inventories.container(BLOCK, POS, one).get(0).render();

        assertTrue(rendered.contains("slot=7"), rendered);
        assertTrue(rendered.contains("item=minecraft:diamond"), rendered);
        assertTrue(rendered.contains("count=5"), rendered);
        assertTrue(rendered.contains("meta=3"), rendered);
        assertTrue(rendered.contains("hasNbt=true"), rendered);
    }

    @Test
    @DisplayName("NBT presence is omitted rather than reported false")
    void noNbtIsOmitted() {
        String rendered = Inventories.container(BLOCK, POS, slots(1)).get(0).render();

        assertFalse(rendered.contains("hasNbt"),
                "an absent optional value is omitted, never rendered as a placeholder");
    }

    @Test
    @DisplayName("a container at the cap is not reported as truncated")
    void atTheCapIsNotTruncated() {
        List<LogRecord> records =
                Inventories.container(BLOCK, POS, slots(Inventories.MAX_CONTAINER_SLOTS));

        assertEquals(Inventories.MAX_CONTAINER_SLOTS, records.size());
        for (LogRecord record : records) {
            assertFalse(record.render().contains("truncated"), record.render());
        }
    }

    @Test
    @DisplayName("past the cap, every record says so and the true total is still reported")
    void pastTheCapIsTruncatedOnEveryRecord() {
        int actual = Inventories.MAX_CONTAINER_SLOTS + 20;
        List<LogRecord> records = Inventories.container(BLOCK, POS, slots(actual));

        assertEquals(Inventories.MAX_CONTAINER_SLOTS, records.size(),
                "output is bounded regardless of container size");
        for (LogRecord record : records) {
            String rendered = record.render();
            // Both facts on every line. A reader who sees only one record must still learn
            // that the listing is incomplete and how much was actually there.
            assertTrue(rendered.contains("truncated=true"), rendered);
            assertTrue(rendered.contains("occupiedSlots=" + actual), rendered);
            assertTrue(rendered.contains("reportedSlots=" + Inventories.MAX_CONTAINER_SLOTS),
                    rendered);
        }
    }
}
