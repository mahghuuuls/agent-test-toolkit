package com.mahghuuuls.agenttesttoolkit.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The single writer for every toolkit record.
 *
 * <p>ARC-005: no other class in this project may call a logger directly. Routing everything
 * through one place is what makes the format rules in REQ-033 and the closed vocabulary in
 * REQ-034 enforceable rather than merely documented. Format consistency is a functional
 * requirement here, not a style preference, because an agent parses the output.
 *
 * <p>Records go to the normal Forge logging system so they land in {@code latest.log}
 * alongside Forge output and target mod output, per REQ-030. The toolkit deliberately does
 * not create a log file of its own: the combined context is the point.
 */
public final class ToolkitLog {

    private static final Logger LOGGER = LogManager.getLogger("DevToolkit");

    private ToolkitLog() {
    }

    /**
     * Writes a completed record.
     */
    public static void write(LogRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        LOGGER.info(record.render());
    }

    /**
     * Writes an error record. Errors are never silently swallowed, per REQ-110, and they
     * carry enough context to identify the cause.
     *
     * @param message a short human and machine readable description
     * @param detail  optional additional context, omitted when absent
     */
    public static void error(String message, String detail) {
        write(LogRecord.of(EventType.ERROR)
                .add("message", message)
                .add("detail", detail));
    }

    public static void error(String message) {
        error(message, null);
    }
}
