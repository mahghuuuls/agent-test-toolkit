package com.mahghuuuls.agenttesttoolkit.arena;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Persists the arena for one dimension, inside the world save.
 *
 * <p>Uses {@code World#getPerWorldStorage()} rather than the global map storage, which is what
 * makes per-dimension arenas work: each dimension keeps its own, and an arena in the Nether
 * does not overwrite the one in the Overworld.
 *
 * <p>Storing inside the world save, rather than beside the toolkit's configuration, is the
 * opposite choice from bundles and is right for the opposite reason. A bundle is a reusable
 * definition that must survive making a fresh test world; an arena is made of blocks that exist
 * only in one world, so metadata that outlived its world would describe nothing.
 */
public final class ArenaStorage extends WorldSavedData {

    /** Also the file name inside the save, so it is namespaced to avoid collision. */
    private static final String DATA_NAME = "devtool_arena";

    private static final String TAG_PRESENT = "present";
    private static final String TAG_ORIGIN_X = "originX";
    private static final String TAG_ORIGIN_Y = "originY";
    private static final String TAG_ORIGIN_Z = "originZ";
    private static final String TAG_WIDTH = "width";
    private static final String TAG_HEIGHT = "height";
    private static final String TAG_LENGTH = "length";
    private static final String TAG_BLOCK = "block";
    private static final String TAG_CEILING = "ceiling";

    private ArenaRecord arena;

    /** Required by Forge's reflective instantiation on load. */
    public ArenaStorage() {
        super(DATA_NAME);
    }

    /** Also required: Forge calls this constructor when loading by name. */
    public ArenaStorage(String name) {
        super(name);
    }

    /**
     * @return the storage for this world's dimension, creating it when absent
     */
    public static ArenaStorage get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        ArenaStorage data =
                (ArenaStorage) storage.getOrLoadData(ArenaStorage.class, DATA_NAME);
        if (data == null) {
            data = new ArenaStorage();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    /** @return the arena for this dimension, or null when none has been created. */
    public ArenaRecord getArena() {
        return arena;
    }

    /**
     * Replaces the stored arena.
     *
     * <p>{@code markDirty()} is not optional. Without it the change lives only in memory and is
     * lost on shutdown. That fails in the one way that is invisible until someone restarts and
     * finds their arena gone.
     */
    public void setArena(ArenaRecord record) {
        this.arena = record;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        if (!nbt.getBoolean(TAG_PRESENT)) {
            arena = null;
            return;
        }
        arena = new ArenaRecord(
                nbt.getInteger(TAG_ORIGIN_X),
                nbt.getInteger(TAG_ORIGIN_Y),
                nbt.getInteger(TAG_ORIGIN_Z),
                nbt.getInteger(TAG_WIDTH),
                nbt.getInteger(TAG_HEIGHT),
                nbt.getInteger(TAG_LENGTH),
                nbt.getString(TAG_BLOCK),
                nbt.getBoolean(TAG_CEILING));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        // An explicit present flag rather than inferring absence from zeroed fields. An arena
        // legitimately created at 0,0,0 with a stored size would otherwise be indistinguishable
        // from no arena at all.
        nbt.setBoolean(TAG_PRESENT, arena != null);
        if (arena == null) {
            return nbt;
        }
        nbt.setInteger(TAG_ORIGIN_X, arena.getOriginX());
        nbt.setInteger(TAG_ORIGIN_Y, arena.getOriginY());
        nbt.setInteger(TAG_ORIGIN_Z, arena.getOriginZ());
        nbt.setInteger(TAG_WIDTH, arena.getWidth());
        nbt.setInteger(TAG_HEIGHT, arena.getHeight());
        nbt.setInteger(TAG_LENGTH, arena.getLength());
        nbt.setString(TAG_BLOCK, arena.getBlockId() == null ? "" : arena.getBlockId());
        nbt.setBoolean(TAG_CEILING, arena.hasCeiling());
        return nbt;
    }
}
