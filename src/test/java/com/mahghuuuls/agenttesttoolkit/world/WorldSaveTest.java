package com.mahghuuuls.agenttesttoolkit.world;

import net.minecraft.world.MinecraftException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The save routine: order, the saving-disabled flag, and a failing world. */
class WorldSaveTest {

    /** Records the flag it saw while saving, and can be told to fail. */
    private static final class FakeWorld implements WorldSave.Target {
        boolean disabled;
        final boolean fails;
        final List<String> log;
        final String name;
        Boolean disabledDuringSave;
        final int dimension;

        FakeWorld(String name, boolean disabled, boolean fails, List<String> log) {
            this(name, "nether".equals(name) ? -1 : 0, disabled, fails, log);
        }

        FakeWorld(String name, int dimension, boolean disabled, boolean fails, List<String> log) {
            this.dimension = dimension;
            this.name = name;
            this.disabled = disabled;
            this.fails = fails;
            this.log = log;
        }

        @Override
        public int getDimension() {
            return dimension;
        }

        @Override
        public boolean isSavingDisabled() {
            return disabled;
        }

        @Override
        public void setSavingDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        @Override
        public void save() throws MinecraftException {
            disabledDuringSave = disabled;
            log.add(name);
            if (fails) {
                throw new MinecraftException("disk full");
            }
        }
    }

    @Test
    @DisplayName("players first, then each world saved with saving on, and each flag put back")
    void flagsRestored() throws Exception {
        final List<String> log = new ArrayList<String>();
        FakeWorld overworld = new FakeWorld("overworld", true, false, log);
        FakeWorld nether = new FakeWorld("nether", false, false, log);
        List<Integer> saved = WorldSave.saveAll(new Runnable() {
            @Override
            public void run() {
                log.add("players");
            }
        }, Arrays.<WorldSave.Target>asList(overworld, nether));

        assertEquals(Arrays.asList(0, -1), saved, "the dimensions saved, in order");
        assertEquals(Arrays.asList("players", "overworld", "nether"), log);
        assertFalse(overworld.disabledDuringSave, "saving is lifted for the save");
        assertTrue(overworld.disabled, "a save-off typed earlier still holds afterwards");
        assertFalse(nether.disabled);
    }

    @Test
    @DisplayName("a failing world stops the save and still gets its flag back")
    void failureRestoresFlag() {
        List<String> log = new ArrayList<String>();
        FakeWorld bad = new FakeWorld("overworld", true, true, log);
        FakeWorld after = new FakeWorld("nether", true, false, log);

        assertThrows(MinecraftException.class,
                () -> WorldSave.saveAll(null, Arrays.<WorldSave.Target>asList(bad, after)));
        assertTrue(bad.disabled, "saving is not left on by a failed save");
        assertEquals(Arrays.asList("overworld"), log, "later worlds are not saved, as in vanilla");
    }
}
