package com.mahghuuuls.agenttesttoolkit.wait;

/**
 * A rectangle of chunks, derived from two block-coordinate corners.
 *
 * <p>Pure, so the arithmetic that decides how many chunks a barrier will poll, and whether
 * that number is allowed, is checkable without a world. The cap exists because the region is
 * typed by a person: a slipped digit turns a small arena into a continent, and a per-tick
 * poll over a continent is exactly the kind of cost that should be refused at parse time
 * rather than discovered as lag.
 */
public final class ChunkRegion {

    /** The most chunks one barrier may watch. A 32 by 32 chunk square. */
    public static final int MAX_CHUNKS = 1024;

    private final int minChunkX;
    private final int minChunkZ;
    private final int maxChunkX;
    private final int maxChunkZ;

    private ChunkRegion(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.maxChunkX = maxChunkX;
        this.maxChunkZ = maxChunkZ;
    }

    /**
     * Builds the region covering every chunk that intersects the block rectangle. Corner order
     * does not matter.
     */
    public static ChunkRegion ofBlocks(int x1, int z1, int x2, int z2) {
        int cx1 = x1 >> 4;
        int cz1 = z1 >> 4;
        int cx2 = x2 >> 4;
        int cz2 = z2 >> 4;
        return new ChunkRegion(Math.min(cx1, cx2), Math.min(cz1, cz2),
                Math.max(cx1, cx2), Math.max(cz1, cz2));
    }

    public int getMinChunkX() {
        return minChunkX;
    }

    public int getMinChunkZ() {
        return minChunkZ;
    }

    public int getMaxChunkX() {
        return maxChunkX;
    }

    public int getMaxChunkZ() {
        return maxChunkZ;
    }

    /** Number of chunks in the region. Computed in long arithmetic so a huge region cannot wrap. */
    public long chunkCount() {
        return (long) (maxChunkX - minChunkX + 1) * (long) (maxChunkZ - minChunkZ + 1);
    }

    /** Whether the region is small enough to be polled every tick. */
    public boolean isWithinCap() {
        return chunkCount() <= MAX_CHUNKS;
    }

    /** {@code minX,minZ..maxX,maxZ} in chunk coordinates, for records and messages. */
    public String bounds() {
        return minChunkX + "," + minChunkZ + ".." + maxChunkX + "," + maxChunkZ;
    }
}
