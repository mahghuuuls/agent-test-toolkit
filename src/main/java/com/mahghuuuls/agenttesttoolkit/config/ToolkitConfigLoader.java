package com.mahghuuuls.agenttesttoolkit.config;

import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads {@link ToolkitConfig} from disk.
 *
 * <p>Separated from the values themselves so the validation rules stay unit testable without
 * Forge. This is the only class here that needs a running mod environment.
 *
 * <p>Lives under {@code config/devtool/} rather than the conventional {@code config/<modid>.cfg},
 * so that the configuration file sits beside the {@code bundles/} directory the same feature
 * family uses. The directory name follows the root command, matching specification section 15.
 */
public final class ToolkitConfigLoader {

    /** Directory name under the game's config folder. Follows the command, not the mod id. */
    public static final String CONFIG_DIR_NAME = "devtool";
    private static final String CONFIG_FILE_NAME = "devtool.cfg";

    private static final String CATEGORY_ARENA = "arena";
    private static final String CATEGORY_DIAGNOSTICS = "diagnostics";
    private static final String CATEGORY_JOIN = "join";
    private static final String CATEGORY_CLIENT = "client";

    private static volatile ToolkitConfig current = ToolkitConfig.DEFAULTS;
    // Volatile for the same reason as `current`: written once during mod loading and read
    // later, potentially from a different thread, with no synchronisation to create an
    // ordering edge. Consistency here is free.
    private static volatile File configDirectory;
    /** The game config directory passed to the last load, so reload needs no path arithmetic. */
    private static volatile File gameConfigDir;

    private ToolkitConfigLoader() {
    }

    /** @return the configuration in effect. Never null; defaults apply before any load. */
    public static ToolkitConfig get() {
        return current;
    }

    /** @return the toolkit's configuration directory, or null before initialization. */
    public static File getConfigDirectory() {
        return configDirectory;
    }

    /**
     * Loads configuration from {@code <gameConfigDir>/devtool/devtool.cfg}, creating it with
     * defaults when absent.
     *
     * @param gameConfigDirectory the game's config directory, from the preInit event
     */
    public static List<String> load(File gameConfigDirectory) {
        gameConfigDir = gameConfigDirectory;
        List<String> problems = new ArrayList<String>();

        configDirectory = new File(gameConfigDirectory, CONFIG_DIR_NAME);
        if (!configDirectory.exists() && !configDirectory.mkdirs()) {
            problems.add("could not create configuration directory "
                    + configDirectory.getAbsolutePath());
            current = ToolkitConfig.DEFAULTS;
            return problems;
        }

        try {
            current = read(new Configuration(new File(configDirectory, CONFIG_FILE_NAME)), problems);
        } catch (RuntimeException e) {
            // A malformed configuration must not stop the toolkit loading. A toolkit that
            // refuses to start is useless for diagnosing whatever was actually being tested,
            // so the failure is reported and defaults are used, never silently swallowed.
            problems.add("failed to read configuration, using defaults: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            current = ToolkitConfig.DEFAULTS;
        }
        return problems;
    }

    /**
     * Reloads from the directory established by the last {@link #load}.
     *
     * <p>Exists so callers do not have to reconstruct the game configuration directory by
     * walking up from {@link #getConfigDirectory()}. That inversion happened to work, but it
     * silently depended on the toolkit directory being exactly one level deep, which nothing
     * in this class promises.
     *
     * @return problems encountered, for the caller to report
     */
    public static List<String> reload() {
        if (gameConfigDir == null) {
            return Collections.emptyList();
        }
        return load(gameConfigDir);
    }

    /** Writes each problem as its own record, never summarised away. */
    public static void reportProblems(List<String> problems) {
        for (String problem : problems) {
            ToolkitLog.error("Configuration problem", problem);
        }
    }

    private static ToolkitConfig read(Configuration cfg, List<String> problems) {
        try {
            cfg.load();

            ToolkitConfig d = ToolkitConfig.DEFAULTS;

            int maxDimension = cfg.getInt("maxArenaDimension", CATEGORY_ARENA,
                    d.getMaxArenaDimension(), ToolkitConfig.MIN_ARENA_DIMENSION,
                    ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION,
                    "Largest arena edge length accepted by 'arena create'. Exists so a mistyped "
                            + "dimension cannot stall the server while it places blocks.");

            int width = cfg.getInt("defaultWidth", CATEGORY_ARENA, d.getDefaultArenaWidth(),
                    ToolkitConfig.MIN_ARENA_DIMENSION, ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION,
                    "Arena width used when 'arena create' is given no dimensions.");
            int height = cfg.getInt("defaultHeight", CATEGORY_ARENA, d.getDefaultArenaHeight(),
                    ToolkitConfig.MIN_ARENA_DIMENSION, ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION,
                    "Arena height used when 'arena create' is given no dimensions.");
            int length = cfg.getInt("defaultLength", CATEGORY_ARENA, d.getDefaultArenaLength(),
                    ToolkitConfig.MIN_ARENA_DIMENSION, ToolkitConfig.ABSOLUTE_MAX_ARENA_DIMENSION,
                    "Arena length used when 'arena create' is given no dimensions.");

            String block = cfg.getString("constructionBlock", CATEGORY_ARENA,
                    d.getDefaultArenaBlock(),
                    "Registry id of the block used for arena floor, walls and ceiling.");

            boolean ceiling = cfg.getBoolean("ceiling", CATEGORY_ARENA, d.hasArenaCeiling(),
                    "Whether arenas are built with a ceiling.");

            int nbtLimit = cfg.getInt("maxNbtOutputLength", CATEGORY_DIAGNOSTICS,
                    d.getMaxNbtOutputLength(), 1, Integer.MAX_VALUE,
                    "Maximum characters of NBT written to the log before truncation. "
                            + "Truncation is always reported; it is never silent.");

            boolean clientDefaults = cfg.getBoolean("applyOnJoin", CATEGORY_CLIENT,
                    d.isClientDefaultsEnabled(),
                    "Whether the client sets brightness and music volume when joining a world. "
                            + "Off by default: these are your own application settings, not game "
                            + "state, and a diagnostic tool should not rewrite them unasked. "
                            + "Ignored on a dedicated server.");

            float brightness = (float) cfg.get(CATEGORY_CLIENT, "brightness",
                    d.getClientBrightness(),
                    "Gamma applied when applyOnJoin is enabled. 0 to 1.").getDouble();

            float musicVolume = (float) cfg.get(CATEGORY_CLIENT, "musicVolume",
                    d.getClientMusicVolume(),
                    "Music volume applied when applyOnJoin is enabled. 0 to 1.").getDouble();

            String joinBundle = cfg.getString("bundle", CATEGORY_JOIN, d.getJoinBundleName(),
                    "Bundle to run when an operator joins. Empty by default: a join hook that "
                            + "fires on installation would mean installing the toolkit changes "
                            + "your world before you ask it to.");

            boolean joinEnabled = cfg.getBoolean("enabled", CATEGORY_JOIN,
                    d.isJoinExecutionEnabled(),
                    "Whether configured commands run when an operator joins. Disabled by "
                            + "default so a leftover configuration cannot surprise anyone.");

            boolean setRespawn = cfg.getBoolean("setRespawnPoint", CATEGORY_ARENA,
                    d.doesArenaSetRespawn(),
                    "Whether arena create moves your respawn point into the arena. On by "
                            + "default: dying mid-test and respawning at world spawn is exactly "
                            + "the repetition the toolkit exists to remove.");

            boolean spawnItems = cfg.getBoolean("spawnIncludeItems", CATEGORY_DIAGNOSTICS,
                    d.isSpawnIncludingItems(),
                    "Whether entity_spawn records dropped items and experience orbs. Off by "
                            + "default: one mob death produces a burst of both, which would "
                            + "bury the spawn actually being investigated.");

            ToolkitConfig loaded = ToolkitConfig.builder()
                    .arenaSize(width, height, length)
                    .arenaBlock(block)
                    .arenaCeiling(ceiling)
                    .maxArenaDimension(maxDimension)
                    .maxNbtOutputLength(nbtLimit)
                    .joinExecutionEnabled(joinEnabled)
                    .joinBundleName(joinBundle)
                    .spawnIncludingItems(spawnItems)
                    .arenaSetsRespawn(setRespawn)
                    .clientDefaultsEnabled(clientDefaults)
                    .clientBrightness(brightness)
                    .clientMusicVolume(musicVolume)
                    .build();

            noteAdjustment(problems, "arena.defaultWidth", width, loaded.getDefaultArenaWidth());
            noteAdjustment(problems, "arena.defaultHeight", height, loaded.getDefaultArenaHeight());
            noteAdjustment(problems, "arena.defaultLength", length, loaded.getDefaultArenaLength());
            noteAdjustment(problems, "arena.maxArenaDimension", maxDimension, loaded.getMaxArenaDimension());

            return loaded;
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }

    /**
     * Notes a value that was clamped, so an adjustment is never silent even though it is not
     * fatal. Collected rather than logged, so the caller controls ordering.
     */
    private static void noteAdjustment(List<String> problems, String key,
                                       int requested, int effective) {
        if (ToolkitConfig.wasAdjusted(requested, effective)) {
            problems.add("value adjusted: " + key
                    + " requested=" + requested + " effective=" + effective);
        }
    }
}
