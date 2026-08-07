package com.mahghuuuls.agenttesttoolkit.logging.filter;

/**
 * Supplies the current arena bounds for a dimension, read at evaluation time.
 *
 * <p>The whole seam in one interface: filters query the arena, and the arena knows nothing about
 * filters. This is the only seam between them, and it points in the permitted direction.
 *
 * <p>Read live rather than captured when the filter is applied. If the arena is recreated
 * mid-test, a filter holding stale bounds would keep admitting events from where the arena used
 * to be and dropping events from where it now is, which is the kind of wrongness that produces
 * a confidently empty log.
 */
public interface ArenaBounds {

    /**
     * @return the interior bounds for the dimension, or null when it has no arena
     */
    Box boundsFor(int dimension);

    /** Inclusive block bounds of an arena interior. */
    final class Box {

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        /**
         * Whether a continuous position lies inside.
         *
         * <p>Block maxima are inclusive but positions are not, so the upper edge extends to
         * {@code max + 1}. The same rule the arena itself uses for entity removal; getting it
         * wrong here would silently drop events happening on the arena's outermost row.
         */
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1
                    && y >= minY && y < maxY + 1
                    && z >= minZ && z < maxZ + 1;
        }

        @Override
        public String toString() {
            return minX + ".." + maxX + "," + minY + ".." + maxY + "," + minZ + ".." + maxZ;
        }
    }
}
