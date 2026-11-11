package com.mahghuuuls.agenttesttoolkit.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The block scan's limit, height clamp, empty-section skip and chunk counts. */
class BlockScanTest {

    /** A world of air with some diamonds, some chunks loaded. */
    private static final class World implements BlockScan.Source<String> {
        final Set<String> loaded = new HashSet<String>();
        final Map<String, String> blocks = new HashMap<String, String>();
        final List<String> read = new ArrayList<String>();

        World load(int cx, int cz) {
            loaded.add(cx + "," + cz);
            return this;
        }

        World put(int x, int y, int z, String block) {
            blocks.put(x + "," + y + "," + z, block);
            return this;
        }

        @Override
        public boolean isLoaded(int chunkX, int chunkZ) {
            return loaded.contains(chunkX + "," + chunkZ);
        }

        @Override
        public boolean isSectionEmpty(int chunkX, int chunkZ, int section) {
            for (String key : blocks.keySet()) {
                String[] p = key.split(",");
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int z = Integer.parseInt(p[2]);
                if (x >> 4 == chunkX && z >> 4 == chunkZ && y >> 4 == section) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String stateAt(int chunkX, int chunkZ, int x, int y, int z) {
            read.add(x + "," + y + "," + z);
            String block = blocks.get(x + "," + y + "," + z);
            return block == null ? "air" : block;
        }
    }

    private static final class Finds implements BlockScan.Visitor<String> {
        final String wanted;
        final List<String> found = new ArrayList<String>();

        Finds(String wanted) {
            this.wanted = wanted;
        }

        @Override
        public boolean matches(String state) {
            return wanted.equals(state);
        }

        @Override
        public void found(int x, int y, int z, String state) {
            found.add(x + "," + y + "," + z);
        }
    }

    @Test
    @DisplayName("three placed blocks, all found; a limit of one finds one and says it stopped")
    void limitAndTruncation() {
        World world = new World().load(0, 0).put(1, 5, 1, "diamond").put(2, 5, 2, "diamond").put(3, 5, 3, "diamond");
        Finds all = new Finds("diamond");
        BlockScan.Result result = BlockScan.scan(0, 0, 0, 15, 20, 15, false, 64, world, all);
        assertEquals(3, result.found);
        assertFalse(result.truncated);
        assertEquals(3, all.found.size());

        Finds one = new Finds("diamond");
        BlockScan.Result limited = BlockScan.scan(0, 0, 0, 15, 20, 15, false, 1, world, one);
        assertEquals(1, limited.found);
        assertTrue(limited.truncated, "a second match exists past the limit");
        assertEquals(1, one.found.size());

        BlockScan.Result exact = BlockScan.scan(0, 0, 0, 15, 20, 15, false, 3, world, new Finds("diamond"));
        assertFalse(exact.truncated, "exactly the limit is not truncated");
    }

    @Test
    @DisplayName("chunks not loaded are skipped and counted, loaded ones past the limit are not read")
    void chunkCounts() {
        World world = new World().load(0, 0).load(1, 0).put(1, 5, 1, "diamond").put(2, 5, 2, "diamond");
        BlockScan.Result result = BlockScan.scan(0, 0, 0, 47, 20, 15, false, 1, world, new Finds("diamond"));
        assertEquals(1, result.chunksSkipped, "chunk 2,0 is not loaded");
        assertEquals(1, result.chunksSearched);
        assertEquals(1, result.chunksNotRead, "chunk 1,0 is loaded but the limit was reached");
    }

    @Test
    @DisplayName("the height is clamped to 0..255, and an empty section is not read unless the search is for air")
    void clampAndSkip() {
        World world = new World().load(0, 0).put(0, 40, 0, "diamond");
        BlockScan.Result result = BlockScan.scan(0, -10, 0, 0, 300, 0, false, 64, world, new Finds("diamond"));
        assertEquals(0, result.minY);
        assertEquals(255, result.maxY);
        assertEquals(1, result.found);
        assertEquals(16, world.read.size(), "only the section holding the diamond is read");

        World air = new World().load(0, 0);
        BlockScan.Result airs = BlockScan.scan(0, 0, 0, 0, 3, 0, true, 64, air, new Finds("air"));
        assertEquals(4, airs.found, "air is found in an empty section");
    }
}
