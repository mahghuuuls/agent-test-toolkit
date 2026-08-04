package com.mahghuuuls.agenttesttoolkit.config;

/**
 * Toolkit configuration values.
 *
 * <p>Supplies defaults only. REQ-101 forbids configuration from carrying diagnostic state:
 * there is deliberately no setting here that enables a logging category, applies a filter, or
 * starts a session. That closes off a whole failure mode, where a stale file left behind by an
 * earlier test silently turns diagnostics on in a later one.
 *
 * <p>Immutable and free of Minecraft types, so the validation rules are unit testable without
 * a game. Loading from disk lives in {@link ToolkitConfigLoader}, which is the part that needs
 * Forge.
 */
public final class ToolkitConfig {

    /**
     * Smallest arena that can exist. Two of every axis are consumed by the walls, floor and
     * ceiling, so anything under three leaves no interior at all.
     */
    public static final int MIN_ARENA_DIMENSION = 3;

    /**
     * Hard ceiling on the configurable maximum, independent of what the file asks for.
     *
     * <p>REQ-063 requires a maximum so a mistyped dimension cannot stall the server. A
     * configurable limit alone would not achieve that, since the mistype could just as easily
     * land in the limit itself. 512 cubed is already far past any plausible test arena.
     */
    public static final int ABSOLUTE_MAX_ARENA_DIMENSION = 512;

    public static final ToolkitConfig DEFAULTS = new ToolkitConfig(
            21, 11, 21, "minecraft:quartz_block", true, 64, 8192, false);

    private final int defaultArenaWidth;
    private final int defaultArenaHeight;
    private final int defaultArenaLength;
    private final String defaultArenaBlock;
    private final boolean arenaCeiling;
    private final int maxArenaDimension;
    private final int maxNbtOutputLength;
    private final boolean joinExecutionEnabled;

    public ToolkitConfig(int defaultArenaWidth,
                         int defaultArenaHeight,
                         int defaultArenaLength,
                         String defaultArenaBlock,
                         boolean arenaCeiling,
                         int maxArenaDimension,
                         int maxNbtOutputLength,
                         boolean joinExecutionEnabled) {
        this.maxArenaDimension = clampMaxDimension(maxArenaDimension);
        this.defaultArenaWidth = clampDimension(defaultArenaWidth, this.maxArenaDimension);
        this.defaultArenaHeight = clampDimension(defaultArenaHeight, this.maxArenaDimension);
        this.defaultArenaLength = clampDimension(defaultArenaLength, this.maxArenaDimension);
        this.defaultArenaBlock = (defaultArenaBlock == null || defaultArenaBlock.isEmpty())
                ? "minecraft:quartz_block" : defaultArenaBlock;
        this.arenaCeiling = arenaCeiling;
        this.maxNbtOutputLength = maxNbtOutputLength < 1 ? 1 : maxNbtOutputLength;
        this.joinExecutionEnabled = joinExecutionEnabled;
    }

    /**
     * Clamps a configured maximum into a usable range.
     *
     * <p>Clamping rather than throwing is deliberate. A bad value in a config file should not
     * prevent the toolkit from loading, because a toolkit that refuses to start is useless for
     * diagnosing the thing that was actually being tested. The out-of-range value is reported
     * by the loader instead.
     */
    static int clampMaxDimension(int requested) {
        if (requested < MIN_ARENA_DIMENSION) {
            return MIN_ARENA_DIMENSION;
        }
        return Math.min(requested, ABSOLUTE_MAX_ARENA_DIMENSION);
    }

    static int clampDimension(int requested, int max) {
        if (requested < MIN_ARENA_DIMENSION) {
            return MIN_ARENA_DIMENSION;
        }
        return Math.min(requested, max);
    }

    /** True when the value the file asked for is not the value in effect. */
    public static boolean wasAdjusted(int requested, int effective) {
        return requested != effective;
    }

    public int getDefaultArenaWidth() {
        return defaultArenaWidth;
    }

    public int getDefaultArenaHeight() {
        return defaultArenaHeight;
    }

    public int getDefaultArenaLength() {
        return defaultArenaLength;
    }

    public String getDefaultArenaBlock() {
        return defaultArenaBlock;
    }

    public boolean hasArenaCeiling() {
        return arenaCeiling;
    }

    public int getMaxArenaDimension() {
        return maxArenaDimension;
    }

    public int getMaxNbtOutputLength() {
        return maxNbtOutputLength;
    }

    public boolean isJoinExecutionEnabled() {
        return joinExecutionEnabled;
    }
}
