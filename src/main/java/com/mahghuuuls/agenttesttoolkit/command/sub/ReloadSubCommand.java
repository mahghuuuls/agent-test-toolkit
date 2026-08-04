package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleRegistry;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Reloads bundle files and toolkit configuration from disk.
 *
 * <p>REQ-102 is specific about what reload must <b>not</b> do: it must not terminate an active
 * session, and it must not reset enabled logging categories or their filters. The owner
 * specification allowed an exception for implementation limitations; the requirement removed
 * that hedge, and this command honours it by simply never touching diagnostic state.
 *
 * <p>That matters in use. An agent edits a bundle mid-test, reloads, and must not silently lose
 * the session grouping the evidence it has already collected.
 */
public final class ReloadSubCommand implements SubCommand {

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

        // Uses the loader's own remembered directory rather than deriving it by walking up
        // from the toolkit config directory. That inversion worked, but it silently assumed
        // the toolkit directory sits exactly one level below the game config directory, which
        // nothing in the loader's contract promises.
        java.util.List<String> configProblems = ToolkitConfigLoader.reload();
        ToolkitConfigLoader.reportProblems(configProblems);

        BundleRegistry.LoadReport report = Bundles.reload();
        BundleRegistry.reportProblems(report);

        int totalProblems = report.getProblems().size() + configProblems.size();
        sender.sendMessage(new TextComponentString("[DevToolkit] Reloaded. bundles="
                + report.getLoaded().size() + " problems=" + totalProblems));

        if (report.hasProblems()) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Load problems are recorded in the log."));
        }

        // REQ-102: state the preservation explicitly. An agent should not have to run a second
        // command to find out whether reloading cost it its session.
        sender.sendMessage(new TextComponentString("[DevToolkit] Session "
                + (sessionBefore == null ? "none" : "'" + sessionBefore.getName() + "' preserved")
                + ", logging categories preserved (" + categoriesBefore + ")."));
    }
}
