package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.scenario.ScenarioCheckpoint;
import com.mahghuuuls.agenttesttoolkit.scenario.Scenarios;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Writes, reports, and clears the scenario checkpoint.
 *
 * <p>A checkpoint says which bundle comes next, across a restart, and refuses everything
 * else until the operator says the scenario is done or abandoned. The toolkit does not
 * restart anything; it only makes the contract between two processes visible and enforced.
 */
public final class ScenarioSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("checkpoint", "status", "complete", "abandon");

    @Override
    public String getName() {
        return "scenario";
    }

    @Override
    public String getDescription() {
        return "Record which bundle must run next across a restart, and refuse others";
    }

    @Override
    public String getUsage() {
        return "scenario <checkpoint <id> next <bundle> [clean|fatal] | status | complete <id> | abandon <id>>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        RecordContext.Snapshot context = RecordContext.snapshot(sender);
        if ("checkpoint".equals(action)) {
            checkpoint(server, sender, args, context);
        } else if ("status".equals(action)) {
            status(server, sender);
        } else if ("complete".equals(action) || "abandon".equals(action)) {
            clear(server, sender, args, "complete".equals(action) ? "completed" : "abandoned", context);
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private void checkpoint(MinecraftServer server, ICommandSender sender, String[] args,
                            RecordContext.Snapshot context) throws CommandException {
        if (args.length < 4 || !"next".equalsIgnoreCase(args[2])) {
            throw new WrongUsageException("/devtool scenario checkpoint <id> next <bundle> [clean|fatal]");
        }
        String id = args[1];
        BundleDefinition next = Bundles.registry().get(args[3]);
        if (next == null) {
            // The checkpoint would be refusing every run in favour of a bundle that does not
            // exist. Better to refuse the checkpoint now.
            ToolkitLog.error("Scenario checkpoint names an unknown bundle", args[3]);
            throw new CommandException("[DevToolkit] Bundle not found: " + args[3]);
        }
        boolean fatal = false;
        if (args.length > 4) {
            String stop = args[4].toLowerCase(Locale.ROOT);
            if ("fatal".equals(stop)) {
                fatal = true;
            } else if (!"clean".equals(stop)) {
                throw new WrongUsageException("/devtool scenario checkpoint <id> next <bundle> [clean|fatal]");
            }
        }
        ScenarioCheckpoint written = Scenarios.checkpoint(server, id, next, fatal, context);
        sender.sendMessage(new TextComponentString("[DevToolkit] Scenario '" + id
                + "' expects '" + written.getNextBundle() + "' next after a "
                + (fatal ? "fatal" : "clean") + " stop, with " + written.getFixtures().size()
                + " fixture(s). Any other bundle is refused until it runs, or the scenario is completed or abandoned."));
    }

    private void status(MinecraftServer server, ICommandSender sender) {
        if (Scenarios.isCorrupt(server)) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] The stored scenario checkpoint is corrupt and enforces nothing. Abandon it to clear."));
            return;
        }
        ScenarioCheckpoint current = Scenarios.current(server);
        if (current == null) {
            sender.sendMessage(new TextComponentString("[DevToolkit] No scenario checkpoint."));
            return;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Scenario '" + current.getScenarioId()
                + "' expects '" + current.getNextBundle() + "' (hash " + Scenarios.abbrev(current.getNextHash())
                + "...), " + current.getFixtures().size() + " fixture(s), operator "
                + (current.getOperatorName() == null ? "none" : current.getOperatorName())
                + ", stop " + (current.isExpectFatalStop() ? "fatal" : "clean") + "."));
    }

    private void clear(MinecraftServer server, ICommandSender sender, String[] args, String action,
                       RecordContext.Snapshot context) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool scenario " + args[0] + " <id>");
        }
        ScenarioCheckpoint current = Scenarios.current(server);
        if (current != null && !current.getScenarioId().equals(args[1])) {
            // Naming the wrong scenario is the kind of slip a resume test exists to catch.
            throw new CommandException("[DevToolkit] The standing scenario is '"
                    + current.getScenarioId() + "', not '" + args[1] + "'.");
        }
        ScenarioCheckpoint cleared = Scenarios.clear(server, args[1], action, context);
        if (cleared == null && !Scenarios.isCorrupt(server)) {
            sender.sendMessage(new TextComponentString("[DevToolkit] No scenario checkpoint to clear."));
            return;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Scenario '" + args[1] + "' " + action + "."));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 4 && "checkpoint".equalsIgnoreCase(args[0])) {
            return CommandBase.getListOfStringsMatchingLastWord(args,
                    new ArrayList<String>(Bundles.registry().names()));
        }
        return Collections.emptyList();
    }
}
