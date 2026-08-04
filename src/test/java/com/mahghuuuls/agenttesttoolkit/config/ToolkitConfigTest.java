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
 * its consumers, principally the arena in IMP-009.
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
        assertEquals(8192, d.getMaxNbtOutputLength(), "REQ-076 fixes the default NBT limit");
        assertFalse(d.isJoinExecutionEnabled(), "REQ-090 requires join execution off by default");
        assertTrue(d.hasArenaCeiling(), "REQ-061 requires a ceiling by default");
    }

    @Test
    @DisplayName("an arena dimension below the minimum is raised to it")
    void tooSmallDimensionIsRaised() {
        // Below three there is no interior at all, since walls consume two of every axis.
        ToolkitConfig c = new ToolkitConfig(0, 1, 2, "minecraft:stone", true, 64, 8192, false);
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaWidth());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaHeight());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaLength());
    }

    @Test
    @DisplayName("a default dimension above the configured maximum is clamped to it")
    void oversizeDefaultIsClampedToMax() {
        ToolkitConfig c = new ToolkitConfig(100, 100, 100, "minecraft:stone", true, 32, 8192, false);
        assertEquals(32, c.getMaxArenaDimension());
        assertEquals(32, c.getDefaultArenaWidth());
        assertEquals(32, c.getDefaultArenaHeight());
        assertEquals(32, c.getDefaultArenaLength());
    }

    @Test
    @DisplayName("the configured maximum cannot exceed the absolute ceiling")
    void configuredMaximumCannotEscapeAbsoluteCeiling() {
        // REQ-063 exists so a mistyped dimension cannot stall the server. A purely
        // configurable limit would not achieve that, because the mistype could land in the
        // limit itself.
        ToolkitConfig c = new ToolkitConfig(21, 11, 21, "minecraft:stone", true,
                Integer.MAX_VALUE, 8192, false);
        assertEquals(ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION, c.getMaxArenaDimension());
    }

    @Test
    @DisplayName("the configured maximum cannot fall below the minimum")
    void configuredMaximumCannotFallBelowMinimum() {
        ToolkitConfig c = new ToolkitConfig(21, 11, 21, "minecraft:stone", true, -5, 8192, false);
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getMaxArenaDimension());
        assertEquals(ToolkitConfig.MIN_ARENA_DIMENSION, c.getDefaultArenaWidth());
    }

    @Test
    @DisplayName("a non-positive NBT limit is raised to one")
    void nonPositiveNbtLimitIsRaised() {
        assertEquals(1, new ToolkitConfig(21, 11, 21, "s", true, 64, 0, false).getMaxNbtOutputLength());
        assertEquals(1, new ToolkitConfig(21, 11, 21, "s", true, 64, -10, false).getMaxNbtOutputLength());
    }

    @Test
    @DisplayName("a blank construction block falls back to the default")
    void blankBlockFallsBack() {
        assertEquals("minecraft:quartz_block",
                new ToolkitConfig(21, 11, 21, "", true, 64, 8192, false).getDefaultArenaBlock());
        assertEquals("minecraft:quartz_block",
                new ToolkitConfig(21, 11, 21, null, true, 64, 8192, false).getDefaultArenaBlock());
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
        ToolkitConfig c = new ToolkitConfig(31, 15, 41, "minecraft:glass", false, 64, 4096, true);
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
