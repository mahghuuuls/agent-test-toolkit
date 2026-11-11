package com.mahghuuuls.agenttesttoolkit.wait;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A rectangle is decorated for good only when it and its -X, -Z ring are decorated. */
class ChunkPopulationTest {

    /** Loaded and decorated chunks, by "x,z". */
    private static final class Grid implements ChunkPopulation.Flags {
        final Set<String> loaded = new HashSet<String>();
        final Set<String> decorated = new HashSet<String>();

        Grid fill(int minX, int minZ, int maxX, int maxZ, boolean decorate) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    loaded.add(x + "," + z);
                    if (decorate) {
                        decorated.add(x + "," + z);
                    }
                }
            }
            return this;
        }

        @Override
        public boolean isLoaded(int chunkX, int chunkZ) {
            return loaded.contains(chunkX + "," + chunkZ);
        }

        @Override
        public boolean isDecorated(int chunkX, int chunkZ) {
            return decorated.contains(chunkX + "," + chunkZ);
        }
    }

    @Test
    @DisplayName("loaded but not decorated is not ready, as the plain wait would say it is")
    void loadedIsNotDecorated() {
        Grid grid = new Grid().fill(-1, -1, 2, 2, false);
        ChunkPopulation.Result result = ChunkPopulation.check(0, 0, 2, 2, grid);
        assertFalse(result.isReady());
        assertEquals("-1,-1", result.getFirstNotDecorated());
        assertEquals(0, result.getDecoratedCount());
    }

    @Test
    @DisplayName("every chunk of the rectangle decorated, but one -X neighbour not: not ready")
    void minusXNeighbourMatters() {
        Grid grid = new Grid().fill(-1, -1, 2, 2, true);
        grid.decorated.remove("-1,1");
        ChunkPopulation.Result result = ChunkPopulation.check(0, 0, 2, 2, grid);
        assertFalse(result.isReady());
        assertEquals("-1,1", result.getFirstNotDecorated());
        assertEquals(15, result.getDecoratedCount());
        assertEquals(16, result.getCheckedCount());
    }

    @Test
    @DisplayName("the rectangle and its -X, -Z ring decorated: ready; the +X side is not asked")
    void ready() {
        Grid grid = new Grid().fill(-1, -1, 2, 2, true);
        ChunkPopulation.Result result = ChunkPopulation.check(0, 0, 2, 2, grid);
        assertTrue(result.isReady());
        assertEquals(16, result.getDecoratedCount());
    }

    @Test
    @DisplayName("a chunk not loaded is not decorated, whatever its flag")
    void unloaded() {
        Grid grid = new Grid().fill(-1, -1, 0, 0, true);
        grid.loaded.remove("0,0");
        assertFalse(ChunkPopulation.check(0, 0, 0, 0, grid).isReady());
    }
}
