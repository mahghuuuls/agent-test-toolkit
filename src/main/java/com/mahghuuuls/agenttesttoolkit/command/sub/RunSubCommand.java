package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.SenderContextSource;
import com.mahghuuuls.agenttesttoolkit.command.ServerCommandDispatcher;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs a loaded bundle's commands in order, as the caller.
 *
 * <p>The commands do not run inside this method. The execution is handed to the scheduler and
 * advanced on the server tick, per ARC-004, so that per-command delays in IMP-014 extend the
 * same machinery rather than replacing a loop written here.
 *
 * <p>Permission context is preserved by construction: the original sender is passed through to
 * the command manager. A bundle is a convenience for typing commands, never a way to run
 * commands the caller could not have typed.
 */
public final class RunSubCommand implements SubCommand {

    @Override
    public String getName() {
        return "run";
    }

    @Override
    public String getDescription() {
        return "Run a loaded command bundle";
    }

    @Override
    public String getUsage() {
        return "run <bundle>";
    }

    @Override
    public boolean requiresPlayer() {
        // The console must be able to run bundles: a headless setup routine is one of the
        // things they are for.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String name = args[0];
        BundleDefinition bundle = Bundles.registry().get(name);
        if (bundle == null) {
            ToolkitLog.error("Bundle not found", name);
            sender.sendMessage(new TextComponentString("[DevToolkit] Bundle not found: " + name));
            return;
        }

        BundleExecution execution = new BundleExecution(bundle,
                new ServerCommandDispatcher(server, sender),
                new SenderContextSource(sender));

        // Written before the first command dispatches, so events caused by the bundle fall
        // inside the boundary rather than before it.
        BundleRecorder.recordStart(execution);
        Bundles.scheduler().submit(execution);

        sender.sendMessage(new TextComponentString("[DevToolkit] Running bundle '"
                + bundle.getName() + "' (" + bundle.size()
                + (bundle.size() == 1 ? " command" : " commands") + ")."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(
                    args, new ArrayList<String>(Bundles.registry().names()));
        }
        return Collections.emptyList();
    }
}
