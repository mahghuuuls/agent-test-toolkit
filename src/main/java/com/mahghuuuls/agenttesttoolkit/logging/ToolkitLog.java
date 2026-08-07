package com.mahghuuuls.agenttesttoolkit.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The single writer for every toolkit record.
 *
 * <p>No other class in this project may call a logger directly. Routing everything through one
 * place is what makes the field format and the closed set of event types enforceable rather
 * than merely documented. Format consistency is a functional requirement here, not a style
 * preference, because an agent parses the output.
 *
 * <p>Records go to the normal Forge logging system so they land in {@code latest.log}
 * alongside Forge output and target mod output. The toolkit deliberately does not create a
 * log file of its own: the combined context is the point.
 */
public final class ToolkitLog {

    private static final Logger LOGGER = LogManager.getLogger("DevToolkit");

    /**
     * When non-null, rendered records are captured here instead of being logged.
     *
     * <p>Exists because record <i>content</i> was previously untestable. Every component built
     * its record privately and handed it straight to a static logger, so a full green suite
     * could still miss a required field going absent. That is not hypothetical: independent
     * review found {@code side} missing from two event types while 33 tests passed, precisely
     * because nothing could observe a rendered record.
     */
    private static List<String> captureSink;

    private ToolkitLog() {
    }

    /**
     * Writes a completed record.
     */
    public static void write(LogRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        String rendered = record.render();
        if (captureSink != null) {
            captureSink.add(rendered);
            return;
        }
        LOGGER.info(rendered);
    }

    /**
     * Writes an error record. Errors are never silently swallowed, and they carry enough
     * context to identify the cause.
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

    /**
     * Begins capturing rendered records instead of logging them. Tests only.
     */
    public static void startCaptureForTesting() {
        captureSink = new ArrayList<String>();
    }

    /**
     * @return the records captured since {@link #startCaptureForTesting()}, in order.
     */
    public static List<String> capturedForTesting() {
        return captureSink == null ? Collections.<String>emptyList() : new ArrayList<String>(captureSink);
    }

    /**
     * Stops capturing and restores normal logging. Tests only.
     */
    public static void stopCaptureForTesting() {
        captureSink = null;
    }
}
