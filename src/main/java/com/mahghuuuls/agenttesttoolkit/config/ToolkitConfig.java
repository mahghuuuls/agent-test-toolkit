package com.mahghuuuls.agenttesttoolkit.config;

/**
 * Toolkit configuration values.
 *
 * <p>Supplies defaults only. Configuration deliberately carries no diagnostic state:
 * there is no setting here that enables a logging category, applies a filter, or
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
     * <p>A maximum exists so a mistyped dimension cannot stall the server. A
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
    private final boolean clientDefaultsEnabled;
    private final float clientBrightness;
    private final float clientMusicVolume;
    /** One level per {@link Volume}, or {@link #LEAVE_MASTER_VOLUME} to leave that category alone. */
    private final float[] clientVolumes;
    private final boolean clientNoPauseOnLostFocus;
    private final boolean inboxEnabled;

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
        private boolean clientDefaults;
        private float brightness = 1.0F;
        private float musicVolume;
        private final float[] volumes = leaveAll();
        private boolean noPauseOnLostFocus;
        private boolean inbox;

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

        public Builder clientDefaultsEnabled(boolean value) {
            this.clientDefaults = value;
            return this;
        }

        public Builder clientBrightness(float value) {
            this.brightness = value;
            return this;
        }

        public Builder clientMusicVolume(float value) {
            this.musicVolume = value;
            return this;
        }

        /** Master volume, 0 to 1, or any negative value to leave it alone. */
        public Builder clientMasterVolume(float value) {
            return clientVolume(Volume.MASTER, value);
        }

        /** Whether joining turns off vanilla's pause on lost focus. Off by default; never turns it on. */
        public Builder clientNoPauseOnLostFocus(boolean value) {
            this.noPauseOnLostFocus = value;
            return this;
        }

        /** One category's volume, 0 to 1, or any negative value to leave it alone. */
        public Builder clientVolume(Volume volume, float value) {
            this.volumes[volume.ordinal()] = value;
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

        /** The configuration half of the inbox gate. Off unless the file says true. */
        public Builder inboxEnabled(boolean value) {
            this.inbox = value;
            return this;
        }

        public ToolkitConfig build() {
            return new ToolkitConfig(width, height, length, block, ceiling, maxDimension,
                    nbtLimit, joinEnabled, spawnItems, setsRespawn, joinBundle,
                    clientDefaults, brightness, musicVolume, volumes, noPauseOnLostFocus, inbox);
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
                  String joinBundleName,
                  boolean clientDefaultsEnabled,
                  float clientBrightness,
                  float clientMusicVolume,
                  float[] clientVolumes,
                  boolean clientNoPauseOnLostFocus,
                  boolean inboxEnabled) {
        this.inboxEnabled = inboxEnabled;
        this.clientDefaultsEnabled = clientDefaultsEnabled;
        // Clamped rather than rejected: a bad value must not stop the toolkit loading.
        this.clientBrightness = clamp01(clientBrightness);
        this.clientMusicVolume = clamp01(clientMusicVolume);
        this.clientNoPauseOnLostFocus = clientNoPauseOnLostFocus;
        // Negative means "leave it alone"; anything else is a level. These are preferences, so
        // turning applyOnJoin on changes none of them unless a level is given.
        this.clientVolumes = new float[Volume.values().length];
        for (int i = 0; i < this.clientVolumes.length; i++) {
            float level = clientVolumes == null || i >= clientVolumes.length ? LEAVE_MASTER_VOLUME : clientVolumes[i];
            this.clientVolumes[i] = level < 0 ? LEAVE_MASTER_VOLUME : clamp01(level);
        }
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
     * <p>On by default. Dying mid-test and respawning at the world spawn is the
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

    static float clamp01(float v) {
        // Not (v >= 0) also catches NaN, which every comparison fails and Forge parses.
        if (!(v >= 0.0F)) {
            return 0.0F;
        }
        return v > 1.0F ? 1.0F : v;
    }

    /**
     * Whether the client applies display and audio defaults on joining a world.
     *
     * <p>Off by default. These are the operator own application settings rather than game
     * state, and a diagnostic tool rewriting them unasked would be surprising.
     */
    public boolean isClientDefaultsEnabled() {
        return clientDefaultsEnabled;
    }

    /** Gamma, 0 to 1. One is the brightest vanilla allows without a shader. */
    public float getClientBrightness() {
        return clientBrightness;
    }

    /** Music volume, 0 to 1. Zero is silent. */
    public float getClientMusicVolume() {
        return clientMusicVolume;
    }

    /** The configured value that means "leave this volume as it is". The default for every {@link Volume}. */
    public static final float LEAVE_MASTER_VOLUME = -1.0F;

    /**
     * The sound categories the client defaults may set besides music, each left alone unless a
     * level is configured (REQ-193, REQ-200). The vanilla category name is kept as text so this
     * package needs no client type; {@code friendly} is vanilla's {@code neutral}.
     */
    public enum Volume {
        MASTER("masterVolume", "master", "master"),
        WEATHER("weatherVolume", "weather", "weather"),
        HOSTILE("hostileVolume", "hostile", "hostile"),
        FRIENDLY("friendlyVolume", "neutral", "friendly"),
        AMBIENT("ambientVolume", "ambient", "ambient");

        private final String configKey;
        private final String categoryName;
        private final String recordPrefix;

        Volume(String configKey, String categoryName, String recordPrefix) {
            this.configKey = configKey;
            this.categoryName = categoryName;
            this.recordPrefix = recordPrefix;
        }

        /** The key under {@code client} in the configuration file. */
        public String getConfigKey() {
            return configKey;
        }

        /** Vanilla's {@code SoundCategory} name. */
        public String getCategoryName() {
            return categoryName;
        }

        /** The {@code <prefix>Was} and {@code <prefix>Now} fields of the report. */
        public String getRecordPrefix() {
            return recordPrefix;
        }
    }

    private static float[] leaveAll() {
        float[] all = new float[Volume.values().length];
        java.util.Arrays.fill(all, LEAVE_MASTER_VOLUME);
        return all;
    }

    /** Whether joining sets this category, when the client defaults apply at all. */
    public boolean isClientVolumeSet(Volume volume) {
        return clientVolumes[volume.ordinal()] >= 0;
    }

    /** This category's level, 0 to 1, or {@link #LEAVE_MASTER_VOLUME}. */
    public float getClientVolume(Volume volume) {
        return clientVolumes[volume.ordinal()];
    }

    /**
     * Whether joining turns off vanilla's pause on lost focus, when the client defaults apply at
     * all (REQ-201). A preference like the volumes, so off unless asked for; it is only ever
     * turned off, never on.
     */
    public boolean isClientNoPauseOnLostFocus() {
        return clientNoPauseOnLostFocus;
    }

    /** Whether joining sets master volume, when the client defaults apply at all. */
    public boolean isClientMasterVolumeEnabled() {
        return isClientVolumeSet(Volume.MASTER);
    }

    /** Master volume, 0 to 1, or {@link #LEAVE_MASTER_VOLUME}. */
    public float getClientMasterVolume() {
        return getClientVolume(Volume.MASTER);
    }

    /**
     * The configuration half of the inbox gate.
     *
     * <p>Off by default. Even when true the inbox listens only after an operator opens it in
     * the game, and closes again at every restart.
     */
    public boolean isInboxEnabled() {
        return inboxEnabled;
    }
}
