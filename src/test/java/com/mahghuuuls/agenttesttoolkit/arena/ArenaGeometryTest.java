package com.mahghuuuls.agenttesttoolkit.arena;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Arena geometry.
 *
 * <p>All of this is arithmetic that would otherwise only be checkable by building an arena and
 * pacing it out. An off-by-one in a wall does not announce itself: it shows up later as a mob
 * inside a supposedly sealed space, during someone else's test, looking like a bug in whatever
 * they were actually testing.
 */
class ArenaGeometryTest {

    private static ArenaGeometry at(int x, int y, int z, int w, int h, int l) {
        return new ArenaGeometry(x, y, z, w, h, l);
    }

    @Test
    @DisplayName("the interior is exactly the size that was asked for")
    void interiorMatchesRequestedSize() {
        // create 20 10 20 must give twenty blocks to walk in, not eighteen. Sizing the shell
        // instead would make every arena quietly smaller than requested.
        ArenaGeometry arena = at(0, 64, 0, 20, 10, 20);
        assertEquals(20, arena.getMaxX() - arena.getMinX() + 1);
        assertEquals(10, arena.getMaxY() - arena.getMinY() + 1);
        assertEquals(20, arena.getMaxZ() - arena.getMinZ() + 1);
    }

    @Test
    @DisplayName("the walkable surface is level with the origin, so the player is not shifted")
    void floorIsAtFootLevel() {
        // REQ-060. Placing floor blocks at the origin itself would push the player up one
        // block on every creation, which compounds if an arena is made inside an arena.
        ArenaGeometry arena = at(0, 64, 0, 9, 5, 9);
        assertEquals(64, arena.getMinY(), "interior starts at foot level");
        assertEquals(63, arena.getFloorY(), "floor blocks sit one below");
        assertEquals(64, arena.getStartY());
    }

    @Test
    @DisplayName("the ceiling clears the tallest interior block")
    void ceilingClearsInterior() {
        ArenaGeometry arena = at(0, 64, 0, 9, 5, 9);
        assertEquals(68, arena.getMaxY());
        assertEquals(69, arena.getCeilingY());
        assertEquals(5, arena.getMaxY() - arena.getMinY() + 1);
    }

    @Test
    @DisplayName("an odd size centres the origin exactly")
    void oddSizeCentresExactly() {
        ArenaGeometry arena = at(100, 64, -50, 21, 5, 21);
        assertEquals(90, arena.getMinX());
        assertEquals(110, arena.getMaxX());
        assertEquals(-60, arena.getMinZ());
        assertEquals(-40, arena.getMaxZ());
        assertEquals(100, arena.getStartX());
        assertEquals(-50, arena.getStartZ());
    }

    @Test
    @DisplayName("an even size is centred deterministically rather than rounding at random")
    void evenSizeIsDeterministic() {
        // REQ-062 requires determinism, not a particular rounding direction. Pinned so the
        // choice cannot drift silently between versions.
        ArenaGeometry arena = at(0, 64, 0, 20, 5, 20);
        assertEquals(-9, arena.getMinX());
        assertEquals(10, arena.getMaxX());
        assertEquals(20, arena.getMaxX() - arena.getMinX() + 1);
    }

    @Test
    @DisplayName("negative coordinates centre the same way as positive ones")
    void negativeCoordinatesBehave() {
        // Integer division truncates toward zero, so a naive centring calculation behaves
        // differently either side of the origin. This is the case that would expose it.
        ArenaGeometry positive = at(50, 64, 50, 11, 5, 11);
        ArenaGeometry negative = at(-50, 64, -50, 11, 5, 11);

        assertEquals(45, positive.getMinX());
        assertEquals(55, positive.getMaxX());
        assertEquals(-55, negative.getMinX());
        assertEquals(-45, negative.getMaxX());
        assertEquals(11, negative.getMaxX() - negative.getMinX() + 1);
    }

    @Test
    @DisplayName("the start position is always inside the interior")
    void startPositionIsInside() {
        // Derived from the bounds rather than returned as the raw origin, so a change to the
        // centring rule cannot leave the player standing in a wall.
        int[][] sizes = {{1, 1, 1}, {2, 3, 4}, {20, 10, 20}, {21, 5, 7}, {256, 256, 256}};
        for (int[] size : sizes) {
            ArenaGeometry arena = at(7, 64, -13, size[0], size[1], size[2]);
            assertTrue(arena.getStartX() >= arena.getMinX() && arena.getStartX() <= arena.getMaxX(),
                    "startX outside for " + size[0]);
            assertTrue(arena.getStartZ() >= arena.getMinZ() && arena.getStartZ() <= arena.getMaxZ(),
                    "startZ outside for " + size[2]);
            assertTrue(arena.getStartY() >= arena.getMinY() && arena.getStartY() <= arena.getMaxY(),
                    "startY outside for " + size[1]);
        }
    }

    @Test
    @DisplayName("the shell sits immediately outside the interior on every side")
    void shellEnclosesInterior() {
        ArenaGeometry arena = at(0, 64, 0, 9, 5, 9);
        assertEquals(arena.getMinX() - 1, arena.getShellMinX());
        assertEquals(arena.getMaxX() + 1, arena.getShellMaxX());
        assertEquals(arena.getMinZ() - 1, arena.getShellMinZ());
        assertEquals(arena.getMaxZ() + 1, arena.getShellMaxZ());
        assertEquals(arena.getMinY() - 1, arena.getFloorY());
        assertEquals(arena.getMaxY() + 1, arena.getCeilingY());
    }

    @Test
    @DisplayName("a one block arena is still coherent")
    void smallestArena() {
        ArenaGeometry arena = at(5, 64, 5, 1, 1, 1);
        assertEquals(5, arena.getMinX());
        assertEquals(5, arena.getMaxX());
        assertEquals(5, arena.getStartX());
        assertEquals(64, arena.getMinY());
        assertEquals(64, arena.getMaxY());
        assertEquals(63, arena.getFloorY());
        assertEquals(65, arena.getCeilingY());
    }

    @Test
    @DisplayName("a zero or negative dimension is rejected at construction")
    void invalidDimensionsRejected() {
        assertThrows(IllegalArgumentException.class, () -> at(0, 64, 0, 0, 5, 5));
        assertThrows(IllegalArgumentException.class, () -> at(0, 64, 0, 5, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> at(0, 64, 0, 5, 5, -1));
    }

    @Test
    @DisplayName("the size limit is checked on every dimension independently")
    void limitCheckedPerDimension() {
        // REQ-063. Checked before any block is placed, so a rejected arena leaves the world
        // untouched rather than half built.
        assertTrue(ArenaGeometry.withinLimit(64, 64, 64, 64));
        assertFalse(ArenaGeometry.withinLimit(65, 10, 10, 64));
        assertFalse(ArenaGeometry.withinLimit(10, 65, 10, 64));
        assertFalse(ArenaGeometry.withinLimit(10, 10, 65, 64));
        assertFalse(ArenaGeometry.withinLimit(0, 10, 10, 64));
    }

    @Test
    @DisplayName("containment covers the whole of the outermost block, not just its corner")
    void containmentCoversWholeBlocks() {
        // The trap IMP-010 asks about. Block maxima are inclusive, but positions are
        // continuous: a mob standing on the last block row sits anywhere from maxX to maxX+1.
        // Comparing against maxX alone would leave the outer ring of the arena un-cleared by
        // reset, which reads as a stray mob rather than a bounds bug.
        ArenaGeometry arena = at(0, 64, 0, 9, 5, 9);
        assertEquals(4, arena.getMaxX());

        assertTrue(arena.contains(4.0, 64.0, 4.0), "corner of the last block");
        assertTrue(arena.contains(4.9, 64.0, 4.9), "far side of the last block");
        assertFalse(arena.contains(5.0, 64.0, 4.0), "first position outside");
    }

    @Test
    @DisplayName("containment excludes the shell in every direction")
    void containmentExcludesShell() {
        ArenaGeometry arena = at(0, 64, 0, 9, 5, 9);
        assertTrue(arena.contains(0.5, 64.0, 0.5));

        assertFalse(arena.contains(-4.5, 64.0, 0.5), "inside the west wall");
        assertFalse(arena.contains(0.5, 63.5, 0.5), "standing on the floor layer");
        assertFalse(arena.contains(0.5, 69.0, 0.5), "at the ceiling layer");
        assertTrue(arena.contains(0.5, 68.9, 0.5), "just below the ceiling");
    }

    @Test
    @DisplayName("an entity at the exact start position is inside")
    void startPositionIsContained() {
        // Reset teleports the player here, so if this were outside the bounds the player would
        // land somewhere reset does not consider part of the arena.
        ArenaGeometry arena = at(13, 70, -7, 20, 10, 20);
        assertTrue(arena.contains(arena.getStartX() + 0.5, arena.getStartY(),
                arena.getStartZ() + 0.5));
    }

    @Test
    @DisplayName("the block count includes the shell, since that is what gets placed")
    void blockCountIncludesShell() {
        // The limit exists because placing blocks is synchronous and a mistyped dimension
        // would stall the server. Counting only the interior would understate the cost.
        ArenaGeometry arena = at(0, 64, 0, 1, 1, 1);
        assertEquals(27L, arena.getTotalBlocks());

        // 64^3 interior is over a quarter of a million blocks once the shell is included,
        // which is the scale the maximum is guarding against.
        assertEquals(66L * 66L * 66L, at(0, 64, 0, 64, 64, 64).getTotalBlocks());
    }
}
