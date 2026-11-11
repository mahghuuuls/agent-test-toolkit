package com.mahghuuuls.agenttesttoolkit.wait;

/**
 * Whether the chunks of a rectangle are decorated for good.
 *
 * <p>The world generator decorates a chunk only once its +X, +Z and +X+Z neighbours are loaded,
 * and the decoration of a chunk places features up to 23 blocks from its corner, so it also
 * writes into those three neighbours. A chunk's blocks are therefore final only when the chunk
 * itself and its -X, -Z and -X-Z neighbours are all decorated. The check covers the rectangle
 * widened by one chunk toward -X and -Z.
 *
 * <p>Pure: it reads the flags through {@link Flags}, so the rule is unit-tested without a world.
 */
public final class ChunkPopulation {

    /** The game's flags of one chunk, read without loading it. */
    public interface Flags {
        boolean isLoaded(int chunkX, int chunkZ);

        /** The generator has decorated it ({@code TerrainPopulated}). Only asked of a loaded chunk. */
        boolean isDecorated(int chunkX, int chunkZ);
    }

    /** The outcome of one check. */
    public static final class Result {
        private final boolean ready;
        private final long decoratedCount;
        private final long checkedCount;
        private final String firstNotDecorated;

        Result(boolean ready, long decoratedCount, long checkedCount, String firstNotDecorated) {
            this.ready = ready;
            this.decoratedCount = decoratedCount;
            this.checkedCount = checkedCount;
            this.firstNotDecorated = firstNotDecorated;
        }

        public boolean isReady() {
            return ready;
        }

        /** Chunks found loaded and decorated, in the widened rectangle. */
        public long getDecoratedCount() {
            return decoratedCount;
        }

        /** Chunks in the widened rectangle. */
        public long getCheckedCount() {
            return checkedCount;
        }

        /** {@code x,z} of the first chunk not loaded or not decorated; null when ready. */
        public String getFirstNotDecorated() {
            return firstNotDecorated;
        }
    }

    private ChunkPopulation() {
    }

    /** Checks the chunks {@code minX..maxX} by {@code minZ..maxZ}, widened by one toward -X and -Z. */
    public static Result check(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Flags flags) {
        long decorated = 0;
        long checked = 0;
        String first = null;
        for (int cx = minChunkX - 1; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ - 1; cz <= maxChunkZ; cz++) {
                checked++;
                if (flags.isLoaded(cx, cz) && flags.isDecorated(cx, cz)) {
                    decorated++;
                } else if (first == null) {
                    // Remembered rather than returned at once, so the count is complete.
                    first = cx + "," + cz;
                }
            }
        }
        return new Result(first == null, decorated, checked, first);
    }
}
