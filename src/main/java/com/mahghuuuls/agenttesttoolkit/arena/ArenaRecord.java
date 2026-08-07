package com.mahghuuuls.agenttesttoolkit.arena;

/**
 * The stored description of one arena.
 *
 * <p>Only the inputs are kept: origin, size, construction block, and whether it has a ceiling.
 * Bounds and start position are <b>derived</b> through {@link ArenaGeometry} on demand rather
 * than stored alongside them.
 *
 * <p>That is deliberate. Storing derived values would let them disagree with the geometry rules
 * after a version change, and a stored bound that no longer matches the built structure is
 * exactly the defect {@code arena info} would otherwise hide: the command reads the same
 * metadata creation wrote, so it can only be trusted if there is nothing for it to be
 * inconsistent with.
 *
 * <p>Immutable. Replacing an arena replaces the whole record, which matches the rule of
 * exactly one arena per dimension.
 */
public final class ArenaRecord {

    private final int originX;
    private final int originY;
    private final int originZ;
    private final int width;
    private final int height;
    private final int length;
    private final String blockId;
    private final boolean ceiling;

    public ArenaRecord(int originX, int originY, int originZ,
                       int width, int height, int length,
                       String blockId, boolean ceiling) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.width = width;
        this.height = height;
        this.length = length;
        this.blockId = blockId;
        this.ceiling = ceiling;
    }

    /** Rebuilds the geometry from the stored inputs. Never cached; see the class note. */
    public ArenaGeometry geometry() {
        return new ArenaGeometry(originX, originY, originZ, width, height, length);
    }

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

    public String getBlockId() {
        return blockId;
    }

    public boolean hasCeiling() {
        return ceiling;
    }

    public String getSizeText() {
        return width + "x" + height + "x" + length;
    }
}
