package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Enables, disables and reports generic event logging.
 *
 * <p>Status reports every enabled category together with any filter applied to it. Filters
 * arrive in IMP-011, so today every enabled category reports as unfiltered, but the output
 * shape is settled now so that adding filters does not change what an agent has already
 * learned to read.
 *
 * <p>REQ-038 exists because silence is ambiguous. An agent that enables a category, asks for
 * an action, and sees nothing needs to distinguish "the event did not occur" from "a filter
 * excluded it" from "the category was never actually on". Status answers the third directly
 * and, once filters exist, the second.
 */
public final class LogSubCommand implements SubCommand {

    private static final String ACTION_ON = "on";
    private static final String ACTION_OFF = "off";
    private static final String TARGET_ALL = "all";

    @Override
    public String getName() {
        return "log";
    }

    @Override
    public String getDescription() {
        return "Enable, disable or report generic event logging";
    }

    @Override
    public String getUsage() {
        return "log <<category> on|off | all off | status>";
    }

    @Override
    public boolean requiresPlayer() {
        // Logging state is process scoped, not player scoped, so the console and command
        // blocks may manage it. That is what lets a setup bundle enable diagnostics.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String first = args[0].toLowerCase(Locale.ROOT);

        if ("status".equals(first)) {
            status(sender);
            return;
        }

        if (TARGET_ALL.equals(first)) {
            if (args.length < 2 || !ACTION_OFF.equals(args[1].toLowerCase(Locale.ROOT))) {
                // Only 'all off' exists. There is deliberately no 'all on': enabling every
                // category at once is the fastest way to make the log unreadable, and the
                // signal-over-volume principle is a core project boundary.
                throw new WrongUsageException("/devtool log all off");
            }
            int disabled = ToolkitState.disableAll();
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Disabled " + disabled + " logging categor" + (disabled == 1 ? "y" : "ies") + "."));
            return;
        }

        LoggingCategory category = LoggingCategory.byName(first);
        if (category == null) {
            ToolkitLog.error("Unknown logging category", first);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown logging category: " + first + ". Try /devtool log status"));
            return;
        }

        if (args.length < 2) {
            throw new WrongUsageException("/devtool log " + category.getCategoryName() + " <on|off>");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (ACTION_ON.equals(action)) {
            boolean changed = ToolkitState.enable(category);
            sender.sendMessage(new TextComponentString("[DevToolkit] " + category.getCategoryName()
                    + (changed ? " enabled." : " was already enabled.")));
        } else if (ACTION_OFF.equals(action)) {
            boolean changed = ToolkitState.disable(category);
            sender.sendMessage(new TextComponentString("[DevToolkit] " + category.getCategoryName()
                    + (changed ? " disabled." : " was already disabled.")));
        } else {
            ToolkitLog.error("Unknown logging action", action);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown action: " + action + ". Expected on or off."));
        }
    }

    private void status(ICommandSender sender) {
        java.util.Set<LoggingCategory> enabled = ToolkitState.getEnabledCategories();
        if (enabled.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] No logging categories enabled."));
            return;
        }
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] Enabled categories (" + enabled.size() + "):"));
        for (LoggingCategory category : enabled) {
            // "filter=none" is stated rather than omitted. An agent must be able to tell an
            // unfiltered category from one whose filter it forgot about, and an absent field
            // would leave that ambiguous. Filters land in IMP-011.
            sender.sendMessage(new TextComponentString(
                    "  " + category.getCategoryName() + "  filter=none"));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> options = new ArrayList<String>();
            for (String name : LoggingCategory.allNames()) {
                options.add(name);
            }
            options.add("status");
            options.add(TARGET_ALL);
            return CommandBase.getListOfStringsMatchingLastWord(args, options);
        }
        if (args.length == 2) {
            if (TARGET_ALL.equals(args[0].toLowerCase(Locale.ROOT))) {
                return CommandBase.getListOfStringsMatchingLastWord(args, Collections.singletonList(ACTION_OFF));
            }
            if (LoggingCategory.byName(args[0]) != null) {
                return CommandBase.getListOfStringsMatchingLastWord(args, java.util.Arrays.asList(ACTION_ON, ACTION_OFF));
            }
        }
        return Collections.emptyList();
    }
}
