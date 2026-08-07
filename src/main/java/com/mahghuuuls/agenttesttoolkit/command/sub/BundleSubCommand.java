package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleCommand;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleDefinition;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
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
 * Lists loaded bundles and shows what one contains.
 *
 * <p>REQ-021. {@code show} matters more than it looks: an agent that wrote a bundle file has no
 * other way to confirm the toolkit parsed it the way it intended, and a silently
 * misinterpreted delay or a dropped command would only surface as a confusing test result.
 */
public final class BundleSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("list", "show");

    @Override
    public String getName() {
        return "bundle";
    }

    @Override
    public String getDescription() {
        return "List loaded command bundles or show one's contents";
    }

    @Override
    public String getUsage() {
        return "bundle <list|show <name>>";
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
        } else {
            // Thrown rather than reported and returned. A command that returns normally counts
            // as a success, so from inside a bundle this would be invisible.
            ToolkitLog.error("Unknown bundle action", action);
            throw new CommandException(
                    "Unknown bundle action: " + action + ". Expected list or show.");
        }
    }

    private void list(ICommandSender sender) {
        int count = Bundles.registry().size();
        List<String> problems = Bundles.registry().getProblems();

        if (count == 0) {
            sender.sendMessage(new TextComponentString("[DevToolkit] No bundles loaded."));
        } else {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Loaded bundles (" + count + "):"));
            for (BundleDefinition bundle : Bundles.registry().all()) {
                String description = bundle.getDescription() == null ? "" : "  -  " + bundle.getDescription();
                String commandCount = bundle.size() + (bundle.size() == 1 ? " command" : " commands");
                sender.sendMessage(new TextComponentString(
                        "  " + bundle.getName() + " (" + commandCount + ")" + description));
            }
        }

        // Problems are surfaced alongside the list rather than only at load. A file that failed
        // to parse hours ago is exactly what an agent needs to know when its bundle is missing.
        if (!problems.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] " + problems.size() + " load problem"
                            + (problems.size() == 1 ? "" : "s") + ", see the log."));
        }
    }

    private void show(ICommandSender sender, String name) throws CommandException {
        BundleDefinition bundle = Bundles.registry().get(name);
        if (bundle == null) {
            ToolkitLog.error("Bundle not found", name);
            throw new CommandException("Bundle not found: " + name);
        }

        sender.sendMessage(new TextComponentString("[DevToolkit] " + bundle.getName()
                + "  stopOnFailure=" + bundle.isStopOnFailure()
                + "  commands=" + bundle.size()));
        if (bundle.getDescription() != null) {
            sender.sendMessage(new TextComponentString("  " + bundle.getDescription()));
        }
        int index = 0;
        for (BundleCommand command : bundle.getCommands()) {
            sender.sendMessage(new TextComponentString("  " + index + ": " + command));
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
        return Collections.emptyList();
    }
}
