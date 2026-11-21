package com.mahghuuuls.agenttesttoolkit.integration.baubles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which equip calls a slot change makes. */
class BaubleEquipCallsTest {

    /** oldEmpty, newEmpty, oldEqualsNew, eventsBlocked, then the expected unequipOld and equipNew. */
    private static final String[] TABLE = {
            "FFFF TT",
            "FTFF TF",
            "TFFF FT",
            "TTFF FF",
            "FFTF FF",
            "FTTF FF",
            "TFTF FF",
            "TTTF FF",
            "FFFT FF",
            "FTFT FF",
            "TFFT FF",
            "TTFT FF",
            "FFTT FF",
            "FTTT FF",
            "TFTT FF",
            "TTTT FF",
    };

    @Test
    @DisplayName("every combination of the four facts gives the calls the slot screen would make")
    void truthTable() {
        assertEquals(16, TABLE.length);
        for (String row : TABLE) {
            BaubleEquipCalls calls = BaubleEquipCalls.decide(row.charAt(0) == 'T', row.charAt(1) == 'T',
                    row.charAt(2) == 'T', row.charAt(3) == 'T');
            assertEquals(row.charAt(5) == 'T', calls.unequipOld(), row);
            assertEquals(row.charAt(6) == 'T', calls.equipNew(), row);
        }
    }

    /**
     * A slot holding text stacks ("" is empty) whose bauble calls are written down in order,
     * with the slot changes between them, so a call made at the wrong moment shows.
     */
    private static final class CountingSlot implements BaubleEquipCalls.Slot<String> {
        private String held = "";
        final List<String> events = new ArrayList<String>();

        @Override
        public String get() {
            return held;
        }

        @Override
        public void put(String stack) {
            held = stack;
            events.add("put " + stack);
        }

        @Override
        public boolean isEmpty(String stack) {
            return stack.isEmpty();
        }

        @Override
        public boolean equal(String a, String b) {
            return a.equals(b);
        }

        @Override
        public void unequipped(String stack) {
            events.add("unequip " + stack);
        }

        @Override
        public void equipped(String stack) {
            events.add("equip " + stack);
        }

        List<String> take() {
            List<String> out = new ArrayList<String>(events);
            events.clear();
            return out;
        }
    }

    @Test
    @DisplayName("through the slot change: set equips once, an equal set calls nothing, clear unequips once")
    void setSetAgainClear() {
        CountingSlot slot = new CountingSlot();
        BaubleEquipCalls.apply(slot, "ring", false);
        assertEquals(Arrays.asList("put ring", "equip ring"), slot.take(), "into an empty slot");
        BaubleEquipCalls.apply(slot, new String("ring"), false);
        assertEquals(Arrays.asList("put ring"), slot.take(), "an equal stack: no call");
        BaubleEquipCalls.apply(slot, "", false);
        assertEquals(Arrays.asList("unequip ring", "put "), slot.take(), "cleared");
        BaubleEquipCalls.apply(slot, "", false);
        assertEquals(Arrays.asList("put "), slot.take(), "clearing an empty slot calls nothing");
    }

    @Test
    @DisplayName("through the slot change: replacing unequips the old before the change and equips the new after it")
    void replace() {
        CountingSlot slot = new CountingSlot();
        BaubleEquipCalls.apply(slot, "ring", false);
        slot.take();
        BaubleEquipCalls.apply(slot, "amulet", false);
        assertEquals(Arrays.asList("unequip ring", "put amulet", "equip amulet"), slot.take());
    }

    @Test
    @DisplayName("through the slot change: nothing is called while the events are blocked, and the slot still changes")
    void blocked() {
        CountingSlot slot = new CountingSlot();
        BaubleEquipCalls.apply(slot, "ring", true);
        assertEquals(Arrays.asList("put ring"), slot.take());
        BaubleEquipCalls.apply(slot, "amulet", true);
        assertEquals(Arrays.asList("put amulet"), slot.take());
        BaubleEquipCalls.apply(slot, "", true);
        assertEquals(Arrays.asList("put "), slot.take());
    }
}
