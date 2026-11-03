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

    /**
     * The local-join secret's file name in the configuration folder (ARC-029): written by a
     * dedicated server, read by a client that shares the folder.
     */
    public static final String LOCAL_JOIN_SECRET_FILE = "local-join.secret";

    /** Directory name under the game's config folder. Follows the command, not the mod id. */
    public static final String CONFIG_DIR_NAME = "devtool";
    private static final String CONFIG_FILE_NAME = "devtool.cfg";

    private static final String CATEGORY_ARENA = "arena";
    private static final String CATEGORY_DIAGNOSTICS = "diagnostics";
    private static final String CATEGORY_JOIN = "join";
    private static final String CATEGORY_CLIENT = "client";
    private static final String CATEGORY_INBOX = "inbox";

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
                    "Whether the client sets brightness and music volume, and any of master, weather, hostile, friendly and ambient volume given a level, when joining a world. "
                            + "Off by default: these are your own application settings, not game "
                            + "state, and a diagnostic tool should not rewrite them unasked. "
                            + "Ignored on a dedicated server.");

            float brightness = (float) cfg.get(CATEGORY_CLIENT, "brightness",
                    d.getClientBrightness(),
                    "Gamma applied when applyOnJoin is enabled. 0 to 1.").getDouble();

            float musicVolume = (float) cfg.get(CATEGORY_CLIENT, "musicVolume",
                    d.getClientMusicVolume(),
                    "Music volume applied when applyOnJoin is enabled. 0 to 1.").getDouble();

            boolean noPause = cfg.getBoolean("noPauseOnLostFocus", CATEGORY_CLIENT,
                    d.isClientNoPauseOnLostFocus(),
                    "Whether applyOnJoin also turns off pausing when the game window loses focus, so "
                            + "a test keeps running while you look at another window. Off by default: "
                            + "it is your own setting. Saved in options.txt and kept if you turn this "
                            + "off again; press F3+P in game to turn the pause back on. Esc still opens "
                            + "the menu.");

            boolean allowActions = cfg.getBoolean("allowActions", CATEGORY_CLIENT,
                    d.isClientAllowActions(),
                    "Whether a server with the toolkit may ask this client to leave and come back, "
                            + "respawn, or close the open screen, for the player who asked. Off by "
                            + "default. Independent of applyOnJoin; takes effect at the next world "
                            + "join. Use it in a development or test instance only.");

            // One key per category; -1, the default, leaves it as it is: these are personal
            // preferences, not test settings, and enabling applyOnJoin must not change them.
            float[] volumes = new float[ToolkitConfig.Volume.values().length];
            for (ToolkitConfig.Volume volume : ToolkitConfig.Volume.values()) {
                volumes[volume.ordinal()] = (float) cfg.get(CATEGORY_CLIENT, volume.getConfigKey(),
                        d.getClientVolume(volume),
                        "Volume of the " + volume.getRecordPrefix() + " sound category applied when "
                                + "applyOnJoin is enabled, 0 to 1. -1, the default, leaves it as it is: "
                                + "volume is a personal preference, not a test setting.")
                        .getDouble();
            }

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

            boolean inbox = cfg.getBoolean("enabled", CATEGORY_INBOX, d.isInboxEnabled(),
                    "Whether the command inbox may be opened. The inbox lets an agent start "
                            + "named bundles and read-only toolkit commands by writing files into "
                            + "config/devtool/inbox, run as the player who opened it. Off by "
                            + "default. Even when true, it listens only after an operator types "
                            + "'devtool inbox open' in the game, or, with openOnLocalJoin, after "
                            + "you join from this computer, and closes when that player leaves "
                            + "or the game restarts. Use it in a development or test instance "
                            + "only.");

            boolean requestChannel = cfg.getBoolean("requestChannel", CATEGORY_CLIENT,
                    d.isClientRequestChannel(),
                    "Whether this client reads menu-step requests from config/devtool/client-inbox "
                            + "while no world is open: create or open a single-player world, connect "
                            + "to a server on this computer or the local network, quit the game. Off "
                            + "by default. When true, it listens from game start, with no click. Use "
                            + "it in a development or test instance only.");

            boolean openOnLocalJoin = cfg.getBoolean("openOnLocalJoin", CATEGORY_INBOX,
                    d.isInboxOpenOnLocalJoin(),
                    "Whether the command inbox opens by itself, without 'devtool inbox open', when "
                            + "you join from this computer: the host of single player, with cheats "
                            + "on, or an operator on a development server whose client shares this "
                            + "server's folder and connects through localhost. Needs enabled=true too. A dedicated server reads it "
                            + "at start: change it, then restart the server. Off by default.");

            int rejoinWindow = cfg.getInt("rejoinWindowSeconds", CATEGORY_INBOX,
                    d.getInboxRejoinWindowSeconds(), ToolkitConfig.MIN_REJOIN_WINDOW_SECONDS,
                    ToolkitConfig.MAX_REJOIN_WINDOW_SECONDS,
                    "How long the inbox waits, in seconds, for its operator to come back after a "
                            + "'devtool client reconnect' the toolkit itself started. While it waits, "
                            + "requests are kept and run on return; after it, the inbox closes.");

            ToolkitConfig.Builder builder = ToolkitConfig.builder()
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
                    .clientNoPauseOnLostFocus(noPause)
                    .clientAllowActions(allowActions)
                    .inboxEnabled(inbox)
                    .inboxRejoinWindowSeconds(rejoinWindow)
                    .clientRequestChannel(requestChannel)
                    .inboxOpenOnLocalJoin(openOnLocalJoin);
            for (ToolkitConfig.Volume volume : ToolkitConfig.Volume.values()) {
                builder.clientVolume(volume, volumes[volume.ordinal()]);
            }
            ToolkitConfig loaded = builder.build();

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
