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

    public static final ToolkitConfig DEFAULTS = builder().build();

    private final int defaultArenaWidth;
    private final int defaultArenaHeight;
    private final int defaultArenaLength;
    private final String defaultArenaBlock;
    private final boolean arenaCeiling;
    private final int maxArenaDimension;
    private final int maxNbtOutputLength;
    private final boolean joinExecutionEnabled;
    private final boolean spawnIncludingItems;
    private final boolean arenaSetsRespawn;
    private final String joinBundleName;

    /**
     * A named-setter builder, because the positional constructor stopped being readable.
     *
     * <p>It grew from eight parameters to ten across three issues, ending as
     * {@code new ToolkitConfig(21, 11, 21, "...", true, 64, 8192, false, false, true)} where the
     * final three booleans are join execution, spawn item inclusion and arena respawn. Nothing
     * at the call site said so, and transposing two of them would compile silently and change
     * behaviour. Adding an eleventh value was the point to fix that rather than continue.
     *
     * <p>Every value keeps its default when not set, so a caller states only what it cares
     * about. Validation stays in the constructor, which the builder calls.
     */
    public static final class Builder {
        private int width = 21;
        private int height = 11;
        private int length = 21;
        private String block = "minecraft:quartz_block";
        private boolean ceiling = true;
        private int maxDimension = 64;
        private int nbtLimit = 8192;
        private boolean joinEnabled;
        private boolean spawnItems;
        private boolean setsRespawn = true;
        private String joinBundle = "";

        public Builder arenaSize(int w, int h, int l) {
            this.width = w;
            this.height = h;
            this.length = l;
            return this;
        }

        public Builder arenaBlock(String value) {
            this.block = value;
            return this;
        }

        public Builder arenaCeiling(boolean value) {
            this.ceiling = value;
            return this;
        }

        public Builder maxArenaDimension(int value) {
            this.maxDimension = value;
            return this;
        }

        public Builder maxNbtOutputLength(int value) {
            this.nbtLimit = value;
            return this;
        }

        public Builder joinExecutionEnabled(boolean value) {
            this.joinEnabled = value;
            return this;
        }

        public Builder joinBundleName(String value) {
            this.joinBundle = value == null ? "" : value.trim();
            return this;
        }

        public Builder spawnIncludingItems(boolean value) {
            this.spawnItems = value;
            return this;
        }

        public Builder arenaSetsRespawn(boolean value) {
            this.setsRespawn = value;
            return this;
        }

        public ToolkitConfig build() {
            return new ToolkitConfig(width, height, length, block, ceiling, maxDimension,
                    nbtLimit, joinEnabled, spawnItems, setsRespawn, joinBundle);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    ToolkitConfig(int defaultArenaWidth,
                  int defaultArenaHeight,
                  int defaultArenaLength,
                  String defaultArenaBlock,
                  boolean arenaCeiling,
                  int maxArenaDimension,
                  int maxNbtOutputLength,
                  boolean joinExecutionEnabled,
                  boolean spawnIncludingItems,
                  boolean arenaSetsRespawn,
                  String joinBundleName) {
        this.joinBundleName = joinBundleName == null ? "" : joinBundleName.trim();
        this.arenaSetsRespawn = arenaSetsRespawn;
        this.spawnIncludingItems = spawnIncludingItems;
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

    /**
     * Whether {@code entity_spawn} records dropped items and experience orbs.
     *
     * <p>Off by default. One mob death produces a burst of both, and they are almost never the
     * subject of a test, so recording them by default would bury the spawn actually being
     * investigated. The opt-in exists because item-drop timing is occasionally exactly what
     * someone is testing.
     */
    public boolean isSpawnIncludingItems() {
        return spawnIncludingItems;
    }

    /**
     * Whether {@code arena create} moves the invoking player's respawn point into the arena.
     *
     * <p>REQ-144, on by default. Dying mid-test and respawning at the world spawn is the
     * repetitive friction the toolkit exists to remove. Configurable rather than unconditional
     * because relocating someone's respawn is a real side effect, and the record states when it
     * happened so it is never silent.
     */
    public boolean doesArenaSetRespawn() {
        return arenaSetsRespawn;
    }

    /**
     * The bundle run when an operator joins, or empty for none.
     *
     * <p>Empty by default, and separately gated by {@link #isJoinExecutionEnabled()}. Two gates
     * rather than one, because installing the toolkit must change nothing until it is asked to
     * act: seeding example bundles and defaulting a join hook to one of them would combine into
     * "installing this alters your world on first login", which neither feature intends alone.
     */
    public String getJoinBundleName() {
        return joinBundleName;
    }
}
