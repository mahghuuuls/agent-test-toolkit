package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCallStack;
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
 * advanced on the server tick, which is what lets per-command delays and nested bundles extend
 * the same machinery rather than replacing a loop written here.
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
            // Thrown, not reported and returned. A command that returns normally counts as a
            // success, so a bundle naming a bundle that does not exist would report failed=0
            // and carry on with a world that was never prepared.
            ToolkitLog.error("Bundle not found", name);
            throw new CommandException("Bundle not found: " + name);
        }

        // Non-null only when this command was dispatched by a bundle that is mid-advance,
        // which is the only way to tell a nested run from one the operator typed.
        BundleExecution parent = Bundles.scheduler().getCurrentExecution();

        BundleCallStack stack;
        if (parent == null) {
            stack = BundleCallStack.root(bundle.getName());
        } else {
            String rejection = parent.getCallStack().rejectionReason(bundle.getName());
            if (rejection != null) {
                // Thrown rather than reported quietly, so the command manager registers a
                // failure and the parent counts it as a failed command. A cycle or an
                // over-deep chain must fail explicitly, and nothing may recurse.
                ToolkitLog.error("Nested bundle refused", rejection);
                throw new CommandException(rejection);
            }
            stack = parent.getCallStack().push(bundle.getName());
        }

        BundleExecution execution = new BundleExecution(bundle,
                new ServerCommandDispatcher(server, sender),
                new SenderContextSource(sender));
        execution.setCallStack(stack);

        // Written before the first command dispatches, so events caused by the bundle fall
        // inside the boundary rather than before it.
        BundleRecorder.recordStart(execution);
        Bundles.scheduler().submit(execution);

        if (parent != null) {
            // The parent parks here. A child may itself contain delays, so it cannot be run to
            // completion inside this call; folding its outcome in when it finishes is what
            // makes a child's failure count as one failed command in the parent.
            parent.awaitChild(execution);
        }

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
