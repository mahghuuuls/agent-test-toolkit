package com.mahghuuuls.agenttesttoolkit.logging.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filter evaluation.
 *
 * <p>This is the feature whose failures are hardest to see from inside the game, because an
 * excluded event and an event that never happened leave the log looking identical. A filter
 * that wrongly excludes everything passes any check that only looks for absence. Exercised
 * directly against coordinates instead.
 */
class FilterTest {

    private static final int OVERWORLD = 0;
    private static final int NETHER = -1;

    /** Fixed bounds, so the filter logic is tested rather than the arena lookup. */
    private static ArenaBounds boundsOf(final ArenaBounds.Box box) {
        return new ArenaBounds() {
            @Override
            public Box boundsFor(int dimension) {
                return dimension == OVERWORLD ? box : null;
            }
        };
    }

    private static ArenaBounds.Box box() {
        return new ArenaBounds.Box(0, 64, 0, 9, 73, 9);
    }

    // --- Radius ----------------------------------------------------------------------

    @Test
    @DisplayName("a radius filter admits inside and excludes outside")
    void radiusAdmitsInsideOnly() {
        Filter filter = new RadiusFilter(OVERWORLD, 0, 64, 0, 20);
        assertTrue(filter.admits(OVERWORLD, 10, 64, 10));
        assertFalse(filter.admits(OVERWORLD, 50, 64, 0),
                "an event 50 blocks away must not be recorded under a radius of 20");
    }

    @Test
    @DisplayName("the radius boundary is inclusive and spherical")
    void radiusBoundary() {
        Filter filter = new RadiusFilter(OVERWORLD, 0, 0, 0, 5);
        assertTrue(filter.admits(OVERWORLD, 3, 0, 4), "3-4-5 triangle, exactly on the boundary");
        assertFalse(filter.admits(OVERWORLD, 5, 0, 5), "corner of the enclosing cube");
        assertFalse(filter.admits(OVERWORLD, 5.001, 0, 0));
    }

    @Test
    @DisplayName("a radius filter does not leak across dimensions")
    void radiusIsDimensionScoped() {
        // The same coordinates exist in every dimension. A filter that ignored this would
        // record Nether events while the operator watched the Overworld.
        Filter filter = new RadiusFilter(OVERWORLD, 0, 64, 0, 20);
        assertTrue(filter.admits(OVERWORLD, 1, 64, 1));
        assertFalse(filter.admits(NETHER, 1, 64, 1));
    }

    @Test
    @DisplayName("a negative radius admits nothing rather than behaving like its absolute value")
    void negativeRadiusAdmitsNothing() {
        assertFalse(new RadiusFilter(OVERWORLD, 0, 0, 0, -5).admits(OVERWORLD, 1, 0, 0));
    }

    @Test
    @DisplayName("the radius description names the anchor, not just the distance")
    void radiusDescribesAnchor() {
        // "radius=20" alone would leave an agent unable to tell an event outside the region
        // from one in the wrong dimension.
        String described = new RadiusFilter(NETHER, 1.5, 64, -2.5, 20).describe();
        assertTrue(described.contains("radius=20.0"), described);
        assertTrue(described.contains("dim=-1"), described);
        assertTrue(described.contains("1.5"), described);
    }

    // --- Arena -----------------------------------------------------------------------

    @Test
    @DisplayName("an arena filter admits inside and excludes outside")
    void arenaAdmitsInsideOnly() {
        Filter filter = new ArenaFilter(OVERWORLD, boundsOf(box()));
        assertTrue(filter.admits(OVERWORLD, 5, 65, 5));
        assertFalse(filter.admits(OVERWORLD, 40, 65, 40));
    }

    @Test
    @DisplayName("the arena boundary covers the whole of the outermost block")
    void arenaBoundaryCoversWholeBlocks() {
        // Block maxima are inclusive but positions are continuous. Comparing against maxX
        // alone would silently drop events on the arena's outermost row.
        Filter filter = new ArenaFilter(OVERWORLD, boundsOf(box()));
        assertTrue(filter.admits(OVERWORLD, 9.0, 64, 9.0));
        assertTrue(filter.admits(OVERWORLD, 9.9, 64, 9.9));
        assertFalse(filter.admits(OVERWORLD, 10.0, 64, 9.0));
        assertFalse(filter.admits(OVERWORLD, 5, 63.5, 5), "below the floor");
    }

    @Test
    @DisplayName("an arena filter does not leak across dimensions")
    void arenaIsDimensionScoped() {
        Filter filter = new ArenaFilter(OVERWORLD, boundsOf(box()));
        assertFalse(filter.admits(NETHER, 5, 65, 5));
    }

    @Test
    @DisplayName("bounds are read live, so recreating the arena moves the filtered region")
    void arenaBoundsAreLive() {
        // Captured bounds would keep admitting events where the arena used to be, and drop
        // events where it now is, producing a confidently empty log.
        final ArenaBounds.Box[] current = {box()};
        Filter filter = new ArenaFilter(OVERWORLD, new ArenaBounds() {
            @Override
            public Box boundsFor(int dimension) {
                return current[0];
            }
        });

        assertTrue(filter.admits(OVERWORLD, 5, 65, 5));
        current[0] = new ArenaBounds.Box(100, 64, 100, 109, 73, 109);
        assertFalse(filter.admits(OVERWORLD, 5, 65, 5), "old bounds must not linger");
        assertTrue(filter.admits(OVERWORLD, 105, 65, 105));
    }

    @Test
    @DisplayName("an arena filter whose arena is gone admits nothing and says so")
    void arenaFilterWithoutArena() {
        // The safe direction: recording everything would flood a log the operator explicitly
        // narrowed. Status has to make the silence explicable, so describe says it outright.
        Filter filter = new ArenaFilter(OVERWORLD, boundsOf(null));
        assertFalse(filter.admits(OVERWORLD, 5, 65, 5));
        assertTrue(filter.describe().contains("NO ARENA"), filter.describe());
    }

    @Test
    @DisplayName("an arena filter requires a bounds source")
    void arenaFilterRequiresSource() {
        assertThrows(IllegalArgumentException.class, () -> new ArenaFilter(OVERWORLD, null));
    }
}
