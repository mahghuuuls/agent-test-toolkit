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

    public BundleCommand(String command, int delayTicks) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("command must not be null or blank");
        }
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks must not be negative: " + delayTicks);
        }
        this.command = command.trim();
        this.delayTicks = delayTicks;
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
