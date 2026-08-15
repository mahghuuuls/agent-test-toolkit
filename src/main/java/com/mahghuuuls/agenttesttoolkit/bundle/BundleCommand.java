package com.mahghuuuls.agenttesttoolkit.bundle;

/**
 * One command inside a bundle, with an optional delay before it runs.
 *
 * <p>The delay is measured in Minecraft ticks, relative to the completion of the
 * preceding command in the same bundle rather than from the bundle's start. Relative composes:
 * inserting a command shifts everything after it, which is what someone editing a list expects.
 *
 * <p>This is only the parsed form; the scheduler owns execution.
 */
public final class BundleCommand {

    private final String command;
    private final int delayTicks;
    private final java.util.Set<String> toleratedFailures;

    public BundleCommand(String command, int delayTicks) {
        this(command, delayTicks, java.util.Collections.<String>emptySet());
    }

    /**
     * @param toleratedFailures translation keys this command may fail with without failing the
     *                          bundle. Empty for almost every command
     */
    public BundleCommand(String command, int delayTicks,
                         java.util.Collection<String> toleratedFailures) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be null or blank");
        }
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must not be negative: " + delayTicks);
        }
        this.command = command.trim();
        this.delayTicks = delayTicks;
        this.toleratedFailures = toleratedFailures == null || toleratedFailures.isEmpty()
                ? java.util.Collections.<String>emptySet()
                : java.util.Collections.unmodifiableSet(
                        new java.util.LinkedHashSet<String>(toleratedFailures));
    }

    /**
     * Failures this command declares it expects.
     *
     * <p>Empty by default, which is the behaviour every bundle written before this existed
     * relies on. A key here tolerates only that key; any other failure still fails, so a typo
     * in this command is not silenced along with the expected outcome.
     */
    public java.util.Set<String> getToleratedFailures() {
        return toleratedFailures;
    }

    public String getCommand() {
        return command;
    }

    /** Ticks to wait after the preceding command completes. Zero means the same tick. */
    public int getDelayTicks() {
        return delayTicks;
    }

    @Override
    public String toString() {
        return delayTicks == 0 ? command : command + "  [+" + delayTicks + " ticks]";
    }
}
