package com.mahghuuuls.agenttesttoolkit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Configuration validation.
 *
 * <p>This issue is Foundation work with no runtime behavior of its own, so these tests are
 * effectively all the evidence it can produce directly. Its real verification comes through
 * its consumers, principally the arena.
 */
class ToolkitConfigTest {

    @Test
    @DisplayName("defaults are self-consistent and usable")
    void defaultsAreUsable() {
        ToolkitConfig d = ToolkitConfig.DEFAULTS;
        assertTrue(d.getDefaultArenaWidth() >= ToolkitConfig.MIN_ARENA_DIMENSION);
        assertTrue(d.getDefaultArenaHeight() >= ToolkitConfig.MIN_ARENA_DIMENSION);
        assertTrue(d.getDefaultArenaLength() >= ToolkitConfig.MIN_ARENA_DIMENSION);
        assertTrue(d.getDefaultArenaWidth() <= d.getMaxArenaDimension());
        assertTrue(d.getDefaultArenaHeight() <= d.getMaxArenaDimension());
        assertTrue(d.getDefaultArenaLength() <= d.getMaxArenaDimension());
        assertTrue(d.getMaxNbtOutputLength() > 0);
        assertEquals(8192, d.getMaxNbtOutputLength(), "default NBT limit");
        assertFalse(d.isJoinExecutionEnabled(), "join execution is off by default");
        assertTrue(d.hasArenaCeiling(), "arenas have a ceiling by default");
    }

    @Test
    @DisplayName("an arena dimension below the minimum is raised to it")
    void tooSmallDimensionIsRaised() {
        // Below three there is no interior at all, since walls consume two of every axis.
        ToolkitConfig c = ToolkitConfig.builder().arenaSize(0, 1, 2).arenaBlock("minecraft:stone").build();
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaWidth());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaHeight());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaLength());
    }

    @Test
    @DisplayName("a default dimension above the configured maximum is clamped to it")
    void oversizeDefaultIsClampedToMax() {
        ToolkitConfig c = ToolkitConfig.builder().arenaSize(100, 100, 100).arenaBlock("minecraft:stone").maxArenaDimension(32).build();
        assertEquals(32, c.getMaxArenaDimension());
        assertEquals(32, c.getDefaultArenaWidth());
        assertEquals(32, c.getDefaultArenaHeight());
        assertEquals(32, c.getDefaultArenaLength());
    }

    @Test
    @DisplayName("the configured maximum cannot exceed the absolute ceiling")
    void configuredMaximumCannotEscapeAbsoluteCeiling() {
        // The ceiling exists so a mistyped dimension cannot stall the server. A purely
        // configurable limit would not achieve that, because the mistype could land in the
        // limit itself.
        ToolkitConfig c = ToolkitConfig.builder().arenaBlock("minecraft:stone").maxArenaDimension(Integer.MAX_VALUE).build();
        assertEquals(ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION, c.getMaxArenaDimension());
    }

    @Test
    @DisplayName("the configured maximum cannot fall below the minimum")
    void configuredMaximumCannotFallBelowMinimum() {
        ToolkitConfig c = ToolkitConfig.builder().arenaBlock("minecraft:stone").maxArenaDimension(-5).build();
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getMaxArenaDimension());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaWidth());
    }

    @Test
    @DisplayName("a non-positive NBT limit is raised to one")
    void nonPositiveNbtLimitIsRaised() {
        assertEquals(1, ToolkitConfig.builder().arenaBlock("s").maxNbtOutputLength(0).build().getMaxNbtOutputLength());
        assertEquals(1, ToolkitConfig.builder().arenaBlock("s").maxNbtOutputLength(-10).build().getMaxNbtOutputLength());
    }

    @Test
    @DisplayName("a blank construction block falls back to the default")
    void blankBlockFallsBack() {
        assertEquals("minecraft:quartz_block",
                ToolkitConfig.builder().arenaBlock("").build().getDefaultArenaBlock());
        assertEquals("minecraft:quartz_block",
                ToolkitConfig.builder().arenaBlock(null).build().getDefaultArenaBlock());
    }

    @Test
    @DisplayName("adjustment is detectable so it can be reported rather than applied silently")
    void adjustmentIsDetectable() {
        assertTrue(ToolkitConfig.wasAdjusted(100, 32));
        assertFalse(ToolkitConfig.wasAdjusted(32, 32));
    }

    @Test
    @DisplayName("valid values pass through unchanged")
    void validValuesPassThrough() {
        ToolkitConfig c = ToolkitConfig.builder().arenaSize(31, 15, 41).arenaBlock("minecraft:glass").arenaCeiling(false).maxNbtOutputLength(4096).joinExecutionEnabled(true).build();
        assertEquals(31, c.getDefaultArenaWidth());
        assertEquals(15, c.getDefaultArenaHeight());
        assertEquals(41, c.getDefaultArenaLength());
        assertEquals("minecraft:glass", c.getDefaultArenaBlock());
        assertFalse(c.hasArenaCeiling());
        assertEquals(64, c.getMaxArenaDimension());
        assertEquals(4096, c.getMaxNbtOutputLength());
        assertTrue(c.isJoinExecutionEnabled());
    }
}
