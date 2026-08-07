package com.mahghuuuls.agenttesttoolkit.arena;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Places the blocks an arena is made of.
 *
 * <p>The arena knows nothing about logging. This builds geometry into a world and returns;
 * recording is the caller's job.
 *
 * <h2>Illumination</h2>
 *
 * <p>The goal is an outcome, light sufficient to prevent hostile spawning throughout the
 * interior, rather than a particular mechanism. Light sources are embedded in the <b>floor</b> on
 * a fixed grid rather than in the ceiling, because ceiling lighting fails in exactly the case
 * that matters: light falls by one per block through air, so in a ten block tall arena a
 * ceiling light leaves the floor at light 5, and hostile mobs spawn at 7 or below. Floor
 * lighting is independent of height.
 *
 * <p>The grid spacing is chosen so the darkest reachable point stays above the spawning
 * threshold. See {@link #LIGHT_SPACING}.
 */
public final class ArenaBuilder {

    /**
     * Blocks between light sources in the floor, on both axes.
     *
     * <p><b>Verified threshold.</b> {@code EntityMob.isValidLightLevel} ends with
     * {@code return i <= this.rand.nextInt(8)}, and {@code nextInt(8)} yields 0 to 7, so a
     * block light level of 8 can never satisfy it. Light 8 is the first value at which hostile
     * spawning is impossible rather than merely unlikely.
     *
     * <p>Light decays by the opacity of each block it crosses, floored at one, so through the
     * air of an arena interior it is exactly one per block. Verified in {@code World.getRawLight},
     * which clamps opacity to a minimum of one before subtracting it.
     *
     * <p>The worst case is the point diagonally
     * furthest from four sources, at spacing/2 on each axis and one block up: with a spacing of
     * 6 that is 3 + 3 + 1 = 7 blocks of travel, leaving light 8. Hostile mobs require 7 or
     * below, so 8 clears it by one. Widening the spacing to 8 would put the worst case at
     * light 6 and let mobs spawn in the corners between lights, which is the kind of failure
     * that would only appear as an unexplained mob during someone else's test.
     */
    public static final int LIGHT_SPACING = 6;

    private ArenaBuilder() {
    }

    /**
     * Builds the arena described by the record.
     *
     * @return the number of blocks changed, so the caller can report the cost
     */
    public static int build(World world, ArenaRecord record) {
        ArenaGeometry geometry = record.geometry();
        IBlockState construction = resolveBlock(record.getBlockId());
        IBlockState air = Blocks.AIR.getDefaultState();
        IBlockState light = Blocks.GLOWSTONE.getDefaultState();

        int changed = 0;

        // Interior first. Clearing before building means the walls are placed into known empty
        // space rather than fighting whatever terrain was there.
        for (int x = geometry.getMinX(); x <= geometry.getMaxX(); x++) {
            for (int y = geometry.getMinY(); y <= geometry.getMaxY(); y++) {
                for (int z = geometry.getMinZ(); z <= geometry.getMaxZ(); z++) {
                    changed += set(world, x, y, z, air);
                }
            }
        }

        // Floor, with light sources on a grid. Anchored to the arena's own minimum rather than
        // to world coordinates, so two arenas of the same size are lit identically wherever
        // they are built.
        for (int x = geometry.getShellMinX(); x <= geometry.getShellMaxX(); x++) {
            for (int z = geometry.getShellMinZ(); z <= geometry.getShellMaxZ(); z++) {
                boolean lit = isLightPosition(geometry, x, z);
                changed += set(world, x, geometry.getFloorY(), z, lit ? light : construction);
            }
        }

        // Walls, one ring per interior level plus the floor and ceiling levels so the shell
        // has no seam at its corners.
        for (int y = geometry.getMinY(); y <= geometry.getMaxY(); y++) {
            for (int x = geometry.getShellMinX(); x <= geometry.getShellMaxX(); x++) {
                changed += set(world, x, y, geometry.getShellMinZ(), construction);
                changed += set(world, x, y, geometry.getShellMaxZ(), construction);
            }
            for (int z = geometry.getMinZ(); z <= geometry.getMaxZ(); z++) {
                changed += set(world, geometry.getShellMinX(), y, z, construction);
                changed += set(world, geometry.getShellMaxX(), y, z, construction);
            }
        }

        if (record.hasCeiling()) {
            for (int x = geometry.getShellMinX(); x <= geometry.getShellMaxX(); x++) {
                for (int z = geometry.getShellMinZ(); z <= geometry.getShellMaxZ(); z++) {
                    changed += set(world, x, geometry.getCeilingY(), z, construction);
                }
            }
        }

        return changed;
    }

    /**
     * Clears the interior to air, leaving the shell intact. Used by reset and clear.
     *
     * @return the number of blocks changed
     */
    public static int clearInterior(World world, ArenaRecord record) {
        ArenaGeometry geometry = record.geometry();
        IBlockState air = Blocks.AIR.getDefaultState();
        int changed = 0;
        for (int x = geometry.getMinX(); x <= geometry.getMaxX(); x++) {
            for (int y = geometry.getMinY(); y <= geometry.getMaxY(); y++) {
                for (int z = geometry.getMinZ(); z <= geometry.getMaxZ(); z++) {
                    changed += set(world, x, y, z, air);
                }
            }
        }
        return changed;
    }

    /** Grid anchored to the arena, offset so lights sit inside the floor rather than on its rim. */
    private static boolean isLightPosition(ArenaGeometry geometry, int x, int z) {
        if (x < geometry.getMinX() || x > geometry.getMaxX()
                || z < geometry.getMinZ() || z > geometry.getMaxZ()) {
            return false;
        }
        return (x - geometry.getMinX()) % LIGHT_SPACING == 0
                && (z - geometry.getMinZ()) % LIGHT_SPACING == 0;
    }

    /**
     * @return the configured block, or stone when the id names nothing in the registry
     */
    public static IBlockState resolveBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return Blocks.STONE.getDefaultState();
        }
        Block block = Block.REGISTRY.getObject(new ResourceLocation(blockId));
        // The registry returns air for an unknown name, which would build an invisible arena.
        // Falling back to stone keeps the structure real; the caller reports the substitution.
        return block == null || block == Blocks.AIR
                ? Blocks.STONE.getDefaultState() : block.getDefaultState();
    }

    /** @return true when the block is known to the registry, so a caller can warn before building. */
    public static boolean isKnownBlock(String blockId) {
        return blockId != null && !blockId.isEmpty()
                && Block.REGISTRY.getObject(new ResourceLocation(blockId)) != Blocks.AIR;
    }

    private static int set(World world, int x, int y, int z, IBlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.getBlockState(pos) == state) {
            return 0;
        }
        world.setBlockState(pos, state, 2);
        return 1;
    }
}
