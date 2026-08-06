package com.mahghuuuls.agenttesttoolkit.arena;

/**
 * Where an arena's blocks go, worked out from an origin and a size.
 *
 * <p>Free of Minecraft types on purpose. This is the bulk of the feature's logic and every part
 * of it is arithmetic that would otherwise only be checkable by building an arena and pacing it
 * out. An off-by-one in a wall is invisible until a mob walks through the gap during someone
 * else's test.
 *
 * <h2>What the numbers mean</h2>
 *
 * <p>Width, height and length describe the <b>interior</b>, the usable space. {@code create 20
 * 10 20} gives twenty blocks of room to walk in, not eighteen. The shell sits outside that, so
 * asking for a size and then measuring the inside agrees with what was asked for.
 *
 * <p>The floor sits one block <b>below</b> the origin, so the walkable surface is exactly at the
 * player's feet and creating an arena does not shift them vertically. REQ-060 calls for the
 * floor at foot level, and the alternative, placing floor blocks at the origin itself, would
 * push the player up a block every time.
 *
 * <p>Horizontal centering uses {@code (size - 1) / 2}, which puts the origin at the exact centre
 * for odd sizes and one block toward the lower coordinate for even ones. Deterministic either
 * way, which is what REQ-062 asks for; the alternative of rounding the other way is equally
 * valid and the choice only has to be stable.
 */
public final class ArenaGeometry {

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int width;
    private final int height;
    private final int length;

    public ArenaGeometry(int originX, int originY, int originZ,
                         int width, int height, int length) {
        if (width < 1 || height < 1 || length < 1) {
            throw new IllegalArgumentException(
                    "arena dimensions must be at least 1: " + width + "x" + height + "x" + length);
        }
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    // --- Interior, the usable space --------------------------------------------------

    public int getMinX() {
        return originX - (width - 1) / 2;
    }

    public int getMaxX() {
        return getMinX() + width - 1;
    }

    /** The walkable surface, level with the origin. */
    public int getMinY() {
        return originY;
    }

    public int getMaxY() {
        return originY + height - 1;
    }

    public int getMinZ() {
        return originZ - (length - 1) / 2;
    }

    public int getMaxZ() {
        return getMinZ() + length - 1;
    }

    // --- Shell, the blocks that enclose the interior ---------------------------------

    /** The floor layer, one below the walkable surface. */
    public int getFloorY() {
        return originY - 1;
    }

    /** The ceiling layer, one above the tallest interior block. */
    public int getCeilingY() {
        return getMaxY() + 1;
    }

    public int getShellMinX() {
        return getMinX() - 1;
    }

    public int getShellMaxX() {
        return getMaxX() + 1;
    }

    public int getShellMinZ() {
        return getMinZ() - 1;
    }

    public int getShellMaxZ() {
        return getMaxZ() + 1;
    }

    // --- Start position --------------------------------------------------------------

    /**
     * Where the player stands after creation.
     *
     * <p>Equal to the origin by construction, since the origin is what the interior is centred
     * on. Derived from the bounds rather than simply returning the origin, so that a change to
     * the centring rule cannot leave the start position pointing outside the arena.
     */
    public int getStartX() {
        return getMinX() + (width - 1) / 2;
    }

    public int getStartY() {
        return getMinY();
    }

    public int getStartZ() {
        return getMinZ() + (length - 1) / 2;
    }

    // --- Accessors -------------------------------------------------------------------

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    /**
     * Total blocks the shell and interior span, used to reason about the cost of building.
     *
     * <p>The size limit exists because placing blocks is synchronous and a mistyped dimension
     * would stall the server. Reporting the volume makes that cost visible rather than implied.
     */
    public long getTotalBlocks() {
        long shellWidth = (long) width + 2;
        long shellLength = (long) length + 2;
        long shellHeight = (long) height + 2;
        return shellWidth * shellLength * shellHeight;
    }

    /**
     * Whether a world position lies inside the interior.
     *
     * <p>Block coordinates are inclusive maxima but positions are continuous, so a mob standing
     * on the last block row occupies x from {@code maxX} to {@code maxX + 1}. Comparing its
     * position against {@code maxX} alone would leave the outermost row of the arena un-cleared
     * by reset, which looks like a stray mob rather than a bounds bug.
     *
     * <p>Used to filter the results of the world's box query, the same cheap-query-then-exact-
     * test shape the entity listing uses.
     */
    public boolean contains(double x, double y, double z) {
        return x >= getMinX() && x < getMaxX() + 1
                && y >= getMinY() && y < getMaxY() + 1
                && z >= getMinZ() && z < getMaxZ() + 1;
    }

    /**
     * Whether every dimension is within the configured maximum.
     *
     * <p>Checked before any block is modified, per REQ-063. A partial build followed by an error
     * would leave the world in a state neither the operator nor the toolkit can describe.
     */
    public static boolean withinLimit(int width, int height, int length, int maximum) {
        return width >= 1 && height >= 1 && length >= 1
                && width <= maximum && height <= maximum && length <= maximum;
    }
}
