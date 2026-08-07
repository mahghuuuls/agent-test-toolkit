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
 * loops, arithmetic or placeholder expansion, and that boundary is a project identity decision
 * rather than a simplification to be revisited when convenient.
 */
public final class BundleDefinition {

    private final String name;
    private final String description;
    private final boolean stopOnFailure;
    private final List<BundleCommand> commands;

    public BundleDefinition(String name, String description, boolean stopOnFailure,
                            List<BundleCommand> commands) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("bundle name must not be null or blank");
        }
        if (commands == null) {
            throw new IllegalArgumentException("commands must not be null");
        }
        this.name = name.trim();
        this.description = description;
        this.stopOnFailure = stopOnFailure;
        this.commands = Collections.unmodifiableList(commands);
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

    public List<BundleCommand> getCommands() {
        return commands;
    }

    public int size() {
        return commands.size();
    }
}
