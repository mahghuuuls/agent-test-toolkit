package com.mahghuuuls.agenttesttoolkit.bundle;

import java.util.Collections;
import java.util.List;

/**
 * A named, ordered sequence of commands.
 *
 * <p>This is the toolkit's primary feature and its value proposition is specific. Minecraft
 * 1.12 already has functions, which are also ordered command lists, so bundles are not a novel
 * capability. What they add is concrete: they live in the toolkit's configuration directory
 * rather than inside a world save, so they survive making a fresh test world; they support
 * per-command tick delays, which functions cannot express; they support stop-on-failure; and
 * they emit execution boundaries into the log.
 *
 * <p>The public documentation states that difference rather than claiming bundles are
 * unavailable in vanilla.
 *
 * <p>Deliberately not a scripting language. There are no variables, expressions, conditionals,
 * loops or arithmetic, and that boundary is a project identity decision rather than a
 * simplification to be revisited when convenient. A fixed list of named placeholders is a
 * lookup, not a language, and is the one substitution the format allows.
 *
 * <p>A definition knows where it came from once the registry has attached that: the relative
 * source name and the raw hash of the file, plus its own normalized hash. The hashes are what
 * let a log line prove which text a bundle ran from, without the reader trusting a copy made
 * later.
 */
public final class BundleDefinition {

    private final String name;
    private final String description;
    private final boolean stopOnFailure;
    private final boolean traced;
    private final boolean resultRequested;
    private final List<BundleCommand> commands;

    /** Relative source identifier, or null before the registry attaches it. */
    private final String sourceName;
    /** SHA-256 of the source file's bytes, or null before the registry attaches it. */
    private final String sourceHash;
    /** Computed once, on first request. Depends only on this definition's content. */
    private String hash;

    public BundleDefinition(String name, String description, boolean stopOnFailure,
                            List<BundleCommand> commands) {
        this(name, description, stopOnFailure, false, commands);
    }

    /**
     * @param traced whether each command emits a step record as it runs. Off unless the file
     *               asks, because a record per command doubles a bundle's footprint in the log
     */
    public BundleDefinition(String name, String description, boolean stopOnFailure,
                            boolean traced, List<BundleCommand> commands) {
        this(name, description, stopOnFailure, traced, false, commands, null, null);
    }

    /**
     * @param resultRequested whether a result file is written when a run ends. Off unless the
     *                        file asks; the log is complete without it
     */
    public BundleDefinition(String name, String description, boolean stopOnFailure,
                            boolean traced, boolean resultRequested, List<BundleCommand> commands) {
        this(name, description, stopOnFailure, traced, resultRequested, commands, null, null);
    }

    private BundleDefinition(String name, String description, boolean stopOnFailure,
                             boolean traced, boolean resultRequested, List<BundleCommand> commands,
                             String sourceName, String sourceHash) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("bundle name must not be null or blank");
        }
        if (commands == null) {
            throw new IllegalArgumentException("commands must not be null");
        }
        this.name = name.trim();
        this.description = description;
        this.stopOnFailure = stopOnFailure;
        this.traced = traced;
        this.resultRequested = resultRequested;
        this.commands = Collections.unmodifiableList(commands);
        this.sourceName = sourceName;
        this.sourceHash = sourceHash;
    }

    /** A copy that knows its file. The registry calls this once per parsed definition. */
    public BundleDefinition withSource(String sourceName, String sourceHash) {
        return new BundleDefinition(name, description, stopOnFailure, traced, resultRequested,
                commands, sourceName, sourceHash);
    }

    public String getName() {
        return name;
    }

    /** Optional human-readable description, or null. */
    public String getDescription() {
        return description;
    }

    /**
     * Whether execution stops after the first failed command.
     *
     * <p>Defaults to enabled when the file omits it. Setup bundles build on each other, so
     * continuing past a failure usually produces a half-prepared environment that wastes the
     * human's next test run.
     */
    public boolean isStopOnFailure() {
        return stopOnFailure;
    }

    /**
     * Whether a step record is written before each command runs.
     *
     * <p>Exists because the timing of a delayed command is otherwise invisible: the log shows
     * when a command's effects appeared, never when the toolkit decided it was due. When a
     * bundle author misjudges where a delay sits, the step records are what show it.
     */
    public boolean isTraced() {
        return traced;
    }

    /** Whether a run of this bundle leaves a result file beside the configuration. */
    public boolean isResultRequested() {
        return resultRequested;
    }

    /** The relative file this came from, or null for a definition built in code. */
    public String getSourceName() {
        return sourceName;
    }

    /** SHA-256 of the whole source file, or null for a definition built in code. */
    public String getSourceHash() {
        return sourceHash;
    }

    /**
     * The normalized hash: the same for any two files that define the same bundle, different
     * for any change that would change what the bundle does. See {@link BundleHashes}.
     */
    public String getHash() {
        if (hash == null) {
            hash = BundleHashes.normalizedHash(this);
        }
        return hash;
    }

    public List<BundleCommand> getCommands() {
        return commands;
    }

    public int size() {
        return commands.size();
    }
}
