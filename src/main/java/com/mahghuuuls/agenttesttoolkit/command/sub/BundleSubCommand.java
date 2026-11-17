package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lists loaded bundles, shows what one contains, and runs one as {@code devtool run} does.
 *
 * <p>{@code show} matters more than it looks: an agent that wrote a bundle file has no
 * other way to confirm the toolkit parsed it the way it intended, and a silently
 * misinterpreted delay or a dropped command would only surface as a confusing test result.
 */
public final class BundleSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("list", "show", "run");

    /** {@code bundle run} is {@code devtool run} under the bundle commands, with the same words. */
    private final RunSubCommand run = new RunSubCommand();

    @Override
    public String getName() {
        return "bundle";
    }

    @Override
    public String getDescription() {
        return "List loaded command bundles, show one's contents, or run one";
    }

    @Override
    public String getUsage() {
        return "bundle <list|show <name>|run <name> [quiet=true|false]>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            list(sender);
        } else if ("show".equals(action)) {
            if (args.length < 2) {
                throw new WrongUsageException("/devtool bundle show <name>");
            }
            show(sender, args[1]);
        } else if ("run".equals(action)) {
            run.execute(server, sender, Arrays.copyOfRange(args, 1, args.length));
        } else {
            // Thrown rather than reported and returned. A command that returns normally counts
            // as a success, so from inside a bundle this would be invisible.
            ToolkitLog.error("Unknown bundle action", action);
            throw new CommandException(
                    "Unknown bundle action: " + action + ". Expected list, show or run.");
        }
    }

    private void list(ICommandSender sender) {
        int count = Bundles.registry().size();
        List<String> problems = Bundles.registry().getProblems();

        if (count == 0) {
            Replies.send(sender, "[DevToolkit] No bundles loaded.");
        } else {
            Replies.send(sender,
                    "[DevToolkit] Loaded bundles (" + count + "):");
            for (BundleDefinition bundle : Bundles.registry().all()) {
                String description = bundle.getDescription() == null ? "" : "  -  " + bundle.getDescription();
                String commandCount = bundle.size() + (bundle.size() == 1 ? " command" : " commands");
                Replies.send(sender,
                        "  " + bundle.getName() + " (" + commandCount + ")" + description);
            }
        }

        // Problems are surfaced alongside the list rather than only at load. A file that failed
        // to parse hours ago is exactly what an agent needs to know when its bundle is missing.
        if (!problems.isEmpty()) {
            Replies.send(sender,
                    "[DevToolkit] " + problems.size() + " load problem"
                            + (problems.size() == 1 ? "" : "s") + ", see the log.");
        }
    }

    private void show(ICommandSender sender, String name) throws CommandException {
        BundleDefinition bundle = Bundles.registry().get(name);
        if (bundle == null) {
            ToolkitLog.error("Bundle not found", name);
            throw new CommandException("Bundle not found: " + name);
        }

        Replies.send(sender, "[DevToolkit] " + bundle.getName()
                + "  stopOnFailure=" + bundle.isStopOnFailure()
                + "  commands=" + bundle.size());
        if (bundle.getDescription() != null) {
            Replies.send(sender, "  " + bundle.getDescription());
        }
        int index = 0;
        for (BundleCommand command : bundle.getCommands()) {
            Replies.send(sender, "  " + index + ": " + command);
            index++;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2 && "show".equals(args[0].toLowerCase(Locale.ROOT))) {
            return CommandBase.getListOfStringsMatchingLastWord(
                    args, new ArrayList<String>(Bundles.registry().names()));
        }
        if (args.length >= 2 && "run".equals(args[0].toLowerCase(Locale.ROOT))) {
            return run.getTabCompletions(server, sender, Arrays.copyOfRange(args, 1, args.length));
        }
        return Collections.emptyList();
    }
}
