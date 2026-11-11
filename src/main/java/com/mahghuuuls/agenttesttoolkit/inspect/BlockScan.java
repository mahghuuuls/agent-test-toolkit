package com.mahghuuuls.agenttesttoolkit.inspect;

/**
 * Walks a box chunk by chunk and section by section, for a block search.
 *
 * <p>Pure: the world is reached through {@link Source}, so the limit, the truncation, the
 * height clamp, the empty-section skip and the chunk counts are unit-tested without a game.
 * A chunk the source does not hold is counted as skipped and never read.
 */
public final class BlockScan {

    /** The lowest and highest block a 1.12.2 world holds. */
    public static final int MIN_Y = 0;
    public static final int MAX_Y = 255;

    /** The world, as the scan sees it. */
    public interface Source<S> {
        boolean isLoaded(int chunkX, int chunkZ);

        /** A 16-block section holding only air; only asked of a loaded chunk. */
        boolean isSectionEmpty(int chunkX, int chunkZ, int section);

        /** The state at a block of a loaded chunk. */
        S stateAt(int chunkX, int chunkZ, int x, int y, int z);
    }

    /** What the scan looks for, and where it hands what it found. */
    public interface Visitor<S> {
        boolean matches(S state);

        void found(int x, int y, int z, S state);
    }

    /** What a scan did. */
    public static final class Result {
        public final int found;
        public final boolean truncated;
        /** Loaded chunks read before the limit was reached. */
        public final int chunksSearched;
        /** Chunks not loaded, so not read. */
        public final int chunksSkipped;
        /** Loaded chunks left unread once the limit was reached. */
        public final int chunksNotRead;
        public final int minY;
        public final int maxY;

        Result(int found, boolean truncated, int chunksSearched, int chunksSkipped, int chunksNotRead,
               int minY, int maxY) {
            this.found = found;
            this.truncated = truncated;
            this.chunksSearched = chunksSearched;
            this.chunksSkipped = chunksSkipped;
            this.chunksNotRead = chunksNotRead;
            this.minY = minY;
            this.maxY = maxY;
        }
    }

    private BlockScan() {
    }

    /**
     * Scans the box (inclusive, the height clamped to {@link #MIN_Y} to {@link #MAX_Y}).
     *
     * @param searchesAir true when the block looked for is air, which an empty section holds
     */
    public static <S> Result scan(int x1, int y1, int z1, int x2, int y2, int z2, boolean searchesAir,
                                  int limit, Source<S> source, Visitor<S> visitor) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.max(MIN_Y, Math.min(y1, y2));
        int maxY = Math.min(MAX_Y, Math.max(y1, y2));
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int found = 0;
        boolean truncated = false;
        int searched = 0;
        int skipped = 0;
        int notRead = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!source.isLoaded(cx, cz)) {
                    skipped++;
                    continue;
                }
                if (truncated) {
                    notRead++;
                    continue;
                }
                searched++;
                int fromX = Math.max(minX, cx << 4);
                int toX = Math.min(maxX, (cx << 4) + 15);
                int fromZ = Math.max(minZ, cz << 4);
                int toZ = Math.min(maxZ, (cz << 4) + 15);
                for (int y = minY; y <= maxY && !truncated; y++) {
                    if (!searchesAir && source.isSectionEmpty(cx, cz, y >> 4)) {
                        // Only air there: on to the section's last level, then the next one.
                        y = ((y >> 4) << 4) + 15;
                        continue;
                    }
                    for (int x = fromX; x <= toX && !truncated; x++) {
                        for (int z = fromZ; z <= toZ; z++) {
                            S state = source.stateAt(cx, cz, x, y, z);
                            if (!visitor.matches(state)) {
                                continue;
                            }
                            if (found >= limit) {
                                truncated = true;
                                break;
                            }
                            found++;
                            visitor.found(x, y, z, state);
                        }
                    }
                }
            }
        }
        return new Result(found, truncated, searched, skipped, notRead, minY, maxY);
    }
}
