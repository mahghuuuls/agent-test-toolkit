package com.mahghuuuls.agenttesttoolkit.inspect;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Refuses a position read whose chunk is not loaded.
 *
 * <p>An inspection is meant to observe. Reading a block or tile entity at a position whose
 * chunk is not loaded makes Minecraft load that chunk from the save, or generate and save it
 * when it was never made, so a "read-only" command would change the world it reports on. The
 * same rule as the chunk wait: observe, never load.
 *
 * <p>The check is {@link World#isBlockLoaded}, which on a server is one map lookup in the
 * chunk provider and never loads anything. The position is passed as asked, height included:
 * vanilla ignores the height, and a mod that loads the world in vertical sections then checks
 * the section that would be read.
 */
public final class LoadedPositions {

    /** Failure key for a read refused because its chunk is not loaded. Tolerable by declaration. */
    public static final String KEY_NOT_LOADED = "devtool.inspect.notLoaded";

    private LoadedPositions() {
    }

    /**
     * Throws a {@link #KEY_NOT_LOADED} command error, with an {@code ERROR} record, when the
     * chunk holding {@code pos} is not loaded. Returns normally when it is.
     */
    public static void require(World world, BlockPos pos) throws CommandException {
        boolean loaded = world != null && world.isBlockLoaded(pos);
        if (loaded) {
            return;
        }
        String where = describe(pos, world == null ? null : world.provider.getDimension());
        ToolkitLog.error("Position not loaded", where);
        throw new CommandException(KEY_NOT_LOADED, where);
    }

    /** {@code "x y z in dimension d"}, the text the chat line and the record carry. */
    public static String describe(BlockPos pos, Integer dimension) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + (dimension == null ? "" : " in dimension " + dimension);
    }
}
