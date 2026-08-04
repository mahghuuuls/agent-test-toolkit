package com.mahghuuuls.agenttesttoolkit.env;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;

import java.util.Collection;

/**
 * Reports what this build actually supports.
 *
 * <p>REQ-082 exists because an agent otherwise assumes the documentation matches the runtime.
 * That assumption fails exactly when it matters most: an old jar in a modpack, or a feature
 * described in a README that was never built.
 *
 * <p><b>Everything here is derived, never written out by hand.</b> Categories come from the
 * {@link LoggingCategory} enum the logging gate itself consults, and the command and inspection
 * lists are passed in from the live command registry. A hand-maintained capabilities string
 * would be worse than no command at all, because it would be confidently wrong: it would keep
 * claiming a feature after the feature was removed, and an agent has no way to check.
 *
 * <p>Takes plain collections rather than reading the command registry itself, because the
 * dependency rules forbid anything depending on {@code command}. The caller lives there and
 * passes what it has.
 */
public final class Capabilities {

    private Capabilities() {
    }

    /**
     * Fills a record the caller has already stamped with context.
     *
     * <p>Takes the record rather than creating one so that {@code side} and {@code worldTick}
     * still lead the output, per REQ-033's stable field order, without this class needing to
     * know about command senders. Keeping it free of Minecraft types is what lets the
     * no-drift property be tested at all.
     *
     * @param record           a record of type {@link EventType#CAPABILITIES}, already stamped
     * @param toolkitVersion   the running build's version
     * @param commandNames     names of the registered subcommands, from the live registry
     * @param inspectionTypes  inspection targets the inspect command actually accepts
     */
    public static LogRecord record(LogRecord record, String toolkitVersion,
                                   Collection<String> commandNames,
                                   Collection<String> inspectionTypes) {
        record.add("toolkitVersion", toolkitVersion);
        record.add("commands", join(commandNames));
        record.add("commandCount", commandNames == null ? 0 : commandNames.size());
        record.add("inspectionTypes", join(inspectionTypes));
        record.add("loggingCategories", categoryNames());
        record.add("loggingCategoryCount", LoggingCategory.values().length);
        return record;
    }

    /** Every category the build knows, in declaration order. */
    public static String categoryNames() {
        StringBuilder out = new StringBuilder();
        for (LoggingCategory category : LoggingCategory.values()) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(category.getCategoryName());
        }
        return out.toString();
    }

    /**
     * Comma separated, no spaces, so the whole list stays a single unquoted field.
     *
     * <p>Same convention as potion effects in {@code Inspectors}. Were a name ever to contain
     * whitespace the record builder would quote the entire value, which stays parseable but
     * moves where the delimiters sit.
     */
    private static String join(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(value);
        }
        return out.toString();
    }
}
