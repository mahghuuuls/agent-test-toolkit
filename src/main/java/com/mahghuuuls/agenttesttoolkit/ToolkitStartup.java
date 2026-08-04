package com.mahghuuuls.agenttesttoolkit;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;

import java.util.List;

/**
 * Emits the startup output, in the order REQ-111 requires.
 *
 * <p>Separated from {@link AgentTestToolkitMod} for one reason: the ordering rule is the thing
 * most likely to be broken by a later edit, and inside a Forge lifecycle handler nothing can
 * observe it. An earlier version wrote two STARTUP records, one before loading and one after,
 * and a full green suite had nothing to say about it. Independent review caught it instead.
 *
 * <p>The rule has two halves that pull against each other. The startup record must be a single
 * record carrying the bundle count, so it cannot be written until loading has finished; and
 * configuration and parsing errors must appear as their own records after it, so loading must
 * not report as it goes. Both loaders therefore return their problems and stay silent, and this
 * is the only place that decides when they are released.
 */
public final class ToolkitStartup {

    private ToolkitStartup() {
    }

    /**
     * Writes exactly one STARTUP record, then every problem as its own record.
     *
     * @param version         the mod version, for the record
     * @param bundles         the outcome of the bundle load
     * @param configProblems  problems from the configuration load
     */
    public static void announce(String version, BundleRegistry.LoadReport bundles,
                                List<String> configProblems) {
        // Configuration contents are deliberately not dumped, only the summary.
        // loggingCategoriesEnabled is genuinely zero rather than a placeholder: REQ-035
        // requires every category to start disabled.
        ToolkitLog.write(LogRecord.of(EventType.STARTUP)
                .add("version", version)
                .add("bundlesLoaded", bundles.getLoaded().size())
                .add("bundleProblems", bundles.getProblems().size())
                .add("loggingCategoriesEnabled", 0));

        ToolkitConfigLoader.reportProblems(configProblems);
        BundleRegistry.reportProblems(bundles);
    }
}
