package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Reloads bundle files and toolkit configuration from disk.
 *
 * <p>What reload must <b>not</b> do is the important part: it must not terminate an active
 * session, and it must not reset enabled logging categories or their filters. This command
 * honours that by simply never touching diagnostic state.
 *
 * <p>That matters in use. An agent edits a bundle mid-test, reloads, and must not silently lose
 * the session grouping the evidence it has already collected.
 *
 * <p>The same reload also runs by itself at every server start (REQ-199), so a world opened
 * after files changed runs them as they are on disk. One method serves both, so the two can
 * never differ; the record's {@code trigger} says which it was.
 */
public final class ReloadSubCommand implements SubCommand {

    public static final String TRIGGER_COMMAND = "command";
    public static final String TRIGGER_SERVER_START = "serverStart";

    /** What one reload found, for the caller's report. */
    public static final class Outcome {
        private final int bundles;
        private final int problems;
        private final boolean bundleProblems;

        Outcome(int bundles, int problems, boolean bundleProblems) {
            this.bundles = bundles;
            this.problems = problems;
            this.bundleProblems = bundleProblems;
        }

        public int getBundles() {
            return bundles;
        }

        public int getProblems() {
            return problems;
        }

        public boolean hasBundleProblems() {
            return bundleProblems;
        }
    }

    /**
     * Re-reads configuration and bundles from disk and writes the {@code RELOAD} record. Never
     * touches the session, its operator, fixtures, or logging categories.
     */
    public static Outcome reloadFromDisk(RecordContext.Snapshot context, String trigger) {
        // Uses the loader's own remembered directory rather than deriving it by walking up
        // from the toolkit config directory. That inversion worked, but it silently assumed
        // the toolkit directory sits exactly one level below the game config directory, which
        // nothing in the loader's contract promises.
        java.util.List<String> configProblems = ToolkitConfigLoader.reload();
        ToolkitConfigLoader.reportProblems(configProblems);

        BundleRegistry.LoadReport report = Bundles.reload();
        BundleRegistry.reportProblems(report);
        BundleRegistry.reportSources(report);

        int totalProblems = report.getProblems().size() + configProblems.size();
        ToolkitLog.write(record(context, report.getLoaded().size(), totalProblems, trigger));
        return new Outcome(report.getLoaded().size(), totalProblems, report.hasProblems());
    }

    /** The {@code RELOAD} record, apart from the disk so it is tested without one. */
    static LogRecord record(RecordContext.Snapshot context, int bundles, int problems, String trigger) {
        // A record beside the chat reply, so a reload is confirmed by its type rather than by
        // matching chat text through a different logger on each side.
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.RELOAD), context);
        SessionStamp.apply(record);
        record.add("bundles", bundles);
        record.add("problems", problems);
        record.add("trigger", trigger);
        return record;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reload bundle files and configuration from disk";
    }

    @Override
    public String getUsage() {
        return "reload";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        // Captured before reloading so the report can state plainly that they survived, rather
        // than leaving the operator to verify it separately.
        DiagnosticSession sessionBefore = ToolkitState.getActiveSession();
        int categoriesBefore = ToolkitState.getEnabledCategories().size();

        Outcome outcome = reloadFromDisk(RecordContext.snapshot(sender), TRIGGER_COMMAND);
        sender.sendMessage(new TextComponentString("[DevToolkit] Reloaded. bundles="
                + outcome.getBundles() + " problems=" + outcome.getProblems()));

        if (outcome.hasBundleProblems()) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Load problems are recorded in the log."));
        }

        // State the preservation explicitly. An agent should not have to run a second
        // command to find out whether reloading cost it its session.
        sender.sendMessage(new TextComponentString("[DevToolkit] Session "
                + (sessionBefore == null ? "none" : "'" + sessionBefore.getName() + "' preserved")
                + ", logging categories preserved (" + categoriesBefore + ")."));
    }
}
