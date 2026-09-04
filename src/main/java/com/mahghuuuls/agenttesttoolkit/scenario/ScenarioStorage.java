package com.mahghuuuls.agenttesttoolkit.scenario;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists the scenario checkpoint inside the world save.
 *
 * <p>Global map storage rather than per dimension, because a scenario spans a world, and
 * inside the save rather than beside the configuration, because a checkpoint read against a
 * different world would be the exact mistake it exists to prevent. The save carries it, so
 * the wrong world simply has none.
 *
 * <p>Corruption is a state, not an exception. Data that cannot be read, or that was written
 * by a newer schema, marks the store corrupt; the load record says so, and nothing is refused
 * on the strength of a checkpoint nobody can read.
 */
public final class ScenarioStorage extends WorldSavedData {

    /** Also the file name inside the save's data directory, namespaced to avoid collision. */
    public static final String DATA_NAME = "devtool_scenario";

    private static final String TAG_VERSION = "schemaVersion";
    private static final String TAG_PRESENT = "present";
    private static final String TAG_ID = "scenarioId";
    private static final String TAG_SESSION = "session";
    private static final String TAG_NEXT = "nextBundle";
    private static final String TAG_HASH = "nextHash";
    private static final String TAG_OP_UUID = "operatorUuid";
    private static final String TAG_OP_NAME = "operatorName";
    private static final String TAG_FATAL = "expectFatalStop";
    private static final String TAG_CREATED = "createdTick";
    private static final String TAG_FIXTURES = "fixtures";

    private ScenarioCheckpoint checkpoint;
    private boolean corrupt;
    private String corruptReason;

    /** Required by Forge's reflective instantiation on load. */
    public ScenarioStorage() {
        super(DATA_NAME);
    }

    /** Also required: Forge calls this constructor when loading by name. */
    public ScenarioStorage(String name) {
        super(name);
    }

    /** The store for this world's save, created empty when absent. Any dimension's world will do. */
    public static ScenarioStorage get(World world) {
        MapStorage storage = world.getMapStorage();
        ScenarioStorage data = (ScenarioStorage) storage.getOrLoadData(ScenarioStorage.class, DATA_NAME);
        if (data == null) {
            data = new ScenarioStorage();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** The stored checkpoint, or null when none, or when the store is corrupt. */
    public ScenarioCheckpoint getCheckpoint() {
        return corrupt ? null : checkpoint;
    }

    public boolean isCorrupt() {
        return corrupt;
    }

    public String getCorruptReason() {
        return corruptReason;
    }

    /** Stores a checkpoint. {@code markDirty} is what makes it reach the disk on the next save. */
    public void setCheckpoint(ScenarioCheckpoint next) {
        this.checkpoint = next;
        this.corrupt = false;
        this.corruptReason = null;
        markDirty();
    }

    /** Package-visible so a test can hand in a compound directly. */
    static ScenarioCheckpoint read(NBTTagCompound nbt) {
        if (!nbt.getBoolean(TAG_PRESENT)) {
            return null;
        }
        int version = nbt.getInteger(TAG_VERSION);
        if (version != ScenarioCheckpoint.SCHEMA_VERSION) {
            throw new IllegalStateException("schema version " + version + ", expected "
                    + ScenarioCheckpoint.SCHEMA_VERSION);
        }
        List<ScenarioCheckpoint.Fixture> fixtures = new ArrayList<ScenarioCheckpoint.Fixture>();
        NBTTagList list = nbt.getTagList(TAG_FIXTURES, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound f = list.getCompoundTagAt(i);
            fixtures.add(new ScenarioCheckpoint.Fixture(f.getString("handle"), f.getString("uuid"),
                    f.getInteger("dimension"), f.getInteger("x"), f.getInteger("y"), f.getInteger("z")));
        }
        return new ScenarioCheckpoint(nbt.getString(TAG_ID), emptyToNull(nbt.getString(TAG_SESSION)),
                nbt.getString(TAG_NEXT), nbt.getString(TAG_HASH),
                emptyToNull(nbt.getString(TAG_OP_UUID)), emptyToNull(nbt.getString(TAG_OP_NAME)),
                nbt.getBoolean(TAG_FATAL), nbt.getLong(TAG_CREATED), fixtures);
    }

    static NBTTagCompound write(ScenarioCheckpoint checkpoint, NBTTagCompound nbt) {
        nbt.setInteger(TAG_VERSION, ScenarioCheckpoint.SCHEMA_VERSION);
        nbt.setBoolean(TAG_PRESENT, checkpoint != null);
        if (checkpoint == null) {
            return nbt;
        }
        nbt.setString(TAG_ID, checkpoint.getScenarioId());
        nbt.setString(TAG_SESSION, nullToEmpty(checkpoint.getSession()));
        nbt.setString(TAG_NEXT, checkpoint.getNextBundle());
        nbt.setString(TAG_HASH, checkpoint.getNextHash());
        nbt.setString(TAG_OP_UUID, nullToEmpty(checkpoint.getOperatorUuid()));
        nbt.setString(TAG_OP_NAME, nullToEmpty(checkpoint.getOperatorName()));
        nbt.setBoolean(TAG_FATAL, checkpoint.isExpectFatalStop());
        nbt.setLong(TAG_CREATED, checkpoint.getCreatedTick());
        NBTTagList list = new NBTTagList();
        for (ScenarioCheckpoint.Fixture fixture : checkpoint.getFixtures()) {
            NBTTagCompound f = new NBTTagCompound();
            f.setString("handle", fixture.handle);
            f.setString("uuid", fixture.uuid);
            f.setInteger("dimension", fixture.dimension);
            f.setInteger("x", fixture.x);
            f.setInteger("y", fixture.y);
            f.setInteger("z", fixture.z);
            list.appendTag(f);
        }
        nbt.setTag(TAG_FIXTURES, list);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        try {
            checkpoint = read(nbt);
            corrupt = false;
            corruptReason = null;
        } catch (RuntimeException e) {
            // Kept as a fact rather than thrown. A save with an unreadable checkpoint must
            // still open, and the load record must say why nothing is being enforced.
            checkpoint = null;
            corrupt = true;
            corruptReason = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        return write(checkpoint, nbt);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
