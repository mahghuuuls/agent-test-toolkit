package com.mahghuuuls.agenttesttoolkit.scenario;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resume rule and the stored form.
 *
 * <p>What matters: the right bundle with the right text is allowed and nothing else is; the
 * checkpoint survives a round trip through NBT with every field intact; and data from a
 * newer schema, or garbage, reads as corrupt rather than as a checkpoint.
 */
class ScenarioCheckpointTest {

    private static ScenarioCheckpoint sample() {
        return new ScenarioCheckpoint("imp002", "my session", "post_restart", "abc123",
                "31c4910d-9b69-4725-8969-9ed53ac8a7dc", "Developer", true, 4200L,
                Arrays.asList(new ScenarioCheckpoint.Fixture("source", "u1", -1, 10, 64, -20),
                        new ScenarioCheckpoint.Fixture("candidate", "u2", 0, 1, 2, 3)));
    }

    @Test
    @DisplayName("only the expected bundle with the expected hash may run")
    void refusal() {
        ScenarioCheckpoint checkpoint = sample();
        assertNull(checkpoint.refusalFor("post_restart", "abc123"));
        assertEquals("wrongBundle", checkpoint.refusalFor("setup", "abc123"));
        assertEquals("changedHash", checkpoint.refusalFor("post_restart", "abc124"));
    }

    @Test
    @DisplayName("every field survives the NBT round trip")
    void roundTrip() {
        NBTTagCompound nbt = ScenarioStorage.write(sample(), new NBTTagCompound());
        ScenarioCheckpoint back = ScenarioStorage.read(nbt);
        assertEquals("imp002", back.getScenarioId());
        assertEquals("my session", back.getSession());
        assertEquals("post_restart", back.getNextBundle());
        assertEquals("abc123", back.getNextHash());
        assertEquals("31c4910d-9b69-4725-8969-9ed53ac8a7dc", back.getOperatorUuid());
        assertEquals("Developer", back.getOperatorName());
        assertTrue(back.isExpectFatalStop());
        assertEquals(4200L, back.getCreatedTick());
        assertEquals(2, back.getFixtures().size());
        assertEquals("candidate", back.getFixtures().get(1).handle);
        assertEquals(-20, back.getFixtures().get(0).z);
        assertEquals(-1, back.getFixtures().get(0).dimension);
    }

    @Test
    @DisplayName("an absent checkpoint writes present=false and reads back as null")
    void absent() {
        NBTTagCompound nbt = ScenarioStorage.write(null, new NBTTagCompound());
        assertNull(ScenarioStorage.read(nbt));
    }

    @Test
    @DisplayName("a newer schema is refused rather than guessed at")
    void newerSchemaIsCorrupt() {
        NBTTagCompound nbt = ScenarioStorage.write(sample(), new NBTTagCompound());
        nbt.setInteger("schemaVersion", ScenarioCheckpoint.SCHEMA_VERSION + 1);
        assertThrows(IllegalStateException.class, () -> ScenarioStorage.read(nbt));

        ScenarioStorage storage = new ScenarioStorage();
        storage.readFromNBT(nbt);
        assertTrue(storage.isCorrupt());
        assertNull(storage.getCheckpoint());
        assertTrue(storage.getCorruptReason().contains("schema version"), storage.getCorruptReason());
    }

    @Test
    @DisplayName("a checkpoint with no operator and no fixtures round-trips with nulls, not blanks")
    void minimal() {
        ScenarioCheckpoint minimal = new ScenarioCheckpoint("s", null, "b", "h", null, null, false, 0L,
                Collections.<ScenarioCheckpoint.Fixture>emptyList());
        ScenarioCheckpoint back = ScenarioStorage.read(ScenarioStorage.write(minimal, new NBTTagCompound()));
        assertNull(back.getSession());
        assertNull(back.getOperatorUuid());
        assertNull(back.getOperatorName());
        assertTrue(back.getFixtures().isEmpty());
    }
}
