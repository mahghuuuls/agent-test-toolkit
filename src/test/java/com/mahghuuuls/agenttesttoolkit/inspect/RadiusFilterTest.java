package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Radius containment for nearby entity listing.
 *
 * <p>Looks trivial and hides two decisions that a manual test would not reliably catch: whether
 * the boundary is inclusive, and whether the radius means a sphere or the cube used to fetch
 * candidates cheaply. Both are invisible by eye and would surface only as an entity that
 * "should have been listed", which is the kind of doubt that makes a diagnostic tool useless.
 */
class RadiusFilterTest {

    private static boolean at(double x, double y, double z, double radius) {
        return RadiusFilter.within(0, 0, 0, x, y, z, radius);
    }

    @Test
    @DisplayName("a point inside the radius is included")
    void insideIncluded() {
        assertTrue(at(1, 0, 0, 5));
        assertTrue(at(0, 0, 0, 5));
        assertTrue(at(3, 0, 4, 5.1));
    }

    @Test
    @DisplayName("a point exactly on the boundary is included")
    void boundaryIncluded() {
        // 3-4-5 triangle: exactly 5 blocks away. Inclusive, because an entity standing at
        // exactly the stated distance is within it in ordinary language.
        assertTrue(at(3, 0, 4, 5));
        assertTrue(at(5, 0, 0, 5));
    }

    @Test
    @DisplayName("a point beyond the radius is excluded")
    void outsideExcluded() {
        assertFalse(at(5.001, 0, 0, 5));
        assertFalse(at(3, 1, 4, 5));
    }

    @Test
    @DisplayName("the radius is spherical, not the bounding cube used to fetch candidates")
    void sphericalNotCubic() {
        // A corner of the enclosing cube sits about 1.73 times the radius away. Listing it
        // would report entities up to 73 percent further than asked for, which is exactly the
        // silent inaccuracy this filter exists to prevent.
        assertFalse(at(5, 5, 5, 5));
        assertFalse(at(4, 4, 0, 5));
    }

    @Test
    @DisplayName("distance is measured in three dimensions, including vertically")
    void verticalCounts() {
        assertTrue(at(0, 5, 0, 5));
        assertFalse(at(0, 6, 0, 5));
    }

    @Test
    @DisplayName("a zero radius matches only the exact point")
    void zeroRadius() {
        assertTrue(at(0, 0, 0, 0));
        assertFalse(at(0.001, 0, 0, 0));
    }

    @Test
    @DisplayName("a negative radius matches nothing rather than throwing")
    void negativeRadiusMatchesNothing() {
        // Squaring a negative would otherwise make it behave like its absolute value, which
        // would silently accept a nonsensical argument.
        assertFalse(at(0, 0, 0, -1));
        assertFalse(at(1, 0, 0, -5));
    }
}
