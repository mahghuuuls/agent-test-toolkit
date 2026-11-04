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
        if (server != null && !server.isCallingFromMinecraftThread()) {
            // RCON runs commands on its own thread. The scheduler and the run channel are
            // server-thread state, so a bundle started from there could be taken for a nested
            // run of whatever the server thread is advancing. Refused, with a way forward.
            ToolkitLog.error("Bundle refused off the server thread", args[0]);
            throw new CommandException("[DevToolkit] devtool run must be called on the server "
                    + "thread. RCON runs commands on its own thread; use the console or the inbox.");
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

        // A standing scenario checkpoint decides whether this bundle may run at all. Asked
        // before anything is submitted, so a refused run leaves no start record behind. A
        // nested run is not asked: its parent already was, and a resume bundle built from
        // sub-bundles must be able to run them.
        if (parent == null) {
            com.mahghuuuls.agenttesttoolkit.logging.RecordContext.Snapshot context =
                    com.mahghuuuls.agenttesttoolkit.logging.RecordContext.snapshot(sender);
            com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.Decision decision =
                    com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.decide(server, bundle);
            if (decision != null && decision.isRefused()) {
                com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.record(decision, bundle, 0, context);
                throw new CommandException(com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.KEY_MISMATCH,
                        decision.refusal + ": expected '" + decision.checkpoint.getNextBundle() + "' ("
                                + com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.abbrev(decision.checkpoint.getNextHash())
                                + "...), offered '" + bundle.getName() + "' ("
                                + com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.abbrev(bundle.getHash()) + "...)");
            }
            if (decision != null) {
                int restored = com.mahghuuuls.agenttesttoolkit.command.ScenarioResume.restore(decision.checkpoint, context);
                com.mahghuuuls.agenttesttoolkit.scenario.Scenarios.record(decision, bundle, restored, context);
            }
        }

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
        // A nested run answers to the same inbox request as its parent, so its result file can
        // be matched to that request too.
        String requestId = com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin.requestIdFor(parent != null,
                parent == null ? null : parent.getOrigin(),
                com.mahghuuuls.agenttesttoolkit.command.RunChannel.requestId());
        execution.setOrigin(new com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin(sender.getName(),
                parent == null ? com.mahghuuuls.agenttesttoolkit.command.RunChannel.of(sender)
                        : com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin.NESTED,
                com.mahghuuuls.agenttesttoolkit.command.DevToolCommand.PERMISSION_LEVEL, requestId));

        if (bundle.isResultRequested()) {
            // Attached before the start record, so the file covers the whole run. The
            // directory sits beside the configuration, like the bundles themselves, so a
            // fresh test world does not lose it.
            java.io.File configDir = com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader.getConfigDirectory();
            com.mahghuuuls.agenttesttoolkit.state.OperatorBinding operator =
                    com.mahghuuuls.agenttesttoolkit.state.ToolkitState.getOperator();
            // The wall-clock start and the save folder say which launch and world the file came
            // from; two runs of a bundle at the same tick still share its name, the later
            // overwriting the earlier.
            execution.attachSink(new com.mahghuuuls.agenttesttoolkit.results.BundleResultCollector(
                    configDir == null ? null : new java.io.File(configDir, "results"),
                    operator == null ? null : operator.getName(),
                    System.currentTimeMillis(), server == null ? null : server.getFolderName()));
        }

        // Written before the first command dispatches, so events caused by the bundle fall
        // inside the boundary rather than before it.
        BundleRecorder.recordStart(execution);
        Bundles.scheduler().submit(execution);
        if (parent == null) {
            // Taken only once the run is submitted, so a caller that offered a listener, the
            // inbox, can treat "taken" as "this run exists and will end".
            execution.addCompletionListener(com.mahghuuuls.agenttesttoolkit.command.RunCompletion.take());
        }

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
