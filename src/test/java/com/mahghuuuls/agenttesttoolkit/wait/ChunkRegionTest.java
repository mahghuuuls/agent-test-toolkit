package com.mahghuuuls.agenttesttoolkit.wait;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block rectangle to chunk rectangle, and the cap.
 *
 * <p>The arithmetic is small and the consequences of getting it wrong are not: an
 * off-by-one at a chunk border makes a barrier declare a region ready while its last
 * column is still missing, and a cap computed in int could wrap for a mistyped coordinate.
 */
class ChunkRegionTest {

    @Test
    @DisplayName("corners map to chunks and order does not matter")
    void cornersToChunks() {
        ChunkRegion region = ChunkRegion.ofBlocks(17, -1, 0, 33);
        assertEquals(0, region.getMinChunkX());
        assertEquals(1, region.getMaxChunkX());
        assertEquals(-1, region.getMinChunkZ());
        assertEquals(2, region.getMaxChunkZ());
        assertEquals(8, region.chunkCount());
        assertEquals("0,-1..1,2", region.bounds());
    }

    @Test
    @DisplayName("negative block coordinates floor toward negative infinity")
    void negativeCoordinatesFloor() {
        ChunkRegion region = ChunkRegion.ofBlocks(-1, -16, -1, -17);
        assertEquals(-1, region.getMinChunkX());
        assertEquals(-2, region.getMinChunkZ());
        assertEquals(-1, region.getMaxChunkZ());
        assertEquals(2, region.chunkCount());
    }

    @Test
    @DisplayName("one block is one chunk")
    void singleBlock() {
        assertEquals(1, ChunkRegion.ofBlocks(5, 5, 5, 5).chunkCount());
    }

    @Test
    @DisplayName("the cap admits 32 by 32 chunks and refuses one more")
    void capBoundary() {
        // 512 blocks is exactly 32 chunks per side when starting on a chunk boundary.
        assertTrue(ChunkRegion.ofBlocks(0, 0, 511, 511).isWithinCap());
        assertEquals(1024, ChunkRegion.ofBlocks(0, 0, 511, 511).chunkCount());
        assertFalse(ChunkRegion.ofBlocks(0, 0, 512, 511).isWithinCap());
    }

    @Test
    @DisplayName("a mistyped continent does not overflow the count")
    void hugeRegionDoesNotWrap() {
        ChunkRegion region = ChunkRegion.ofBlocks(-30000000, -30000000, 30000000, 30000000);
        assertTrue(region.chunkCount() > Integer.MAX_VALUE);
        assertFalse(region.isWithinCap());
    }
}
