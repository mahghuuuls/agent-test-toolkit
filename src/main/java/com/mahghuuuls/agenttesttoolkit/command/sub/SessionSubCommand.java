package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.session.SessionManager;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Starts, stops, and reports the diagnostic session.
 *
 * <p>Sessions group records; they never judge them. There is deliberately no command here
 * that accepts an expected value or reports a result.
 */
public final class SessionSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("start", "stop", "status");

    @Override
    public String getName() {
        return "session";
    }

    @Override
    public String getDescription() {
        return "Group log records under a named test attempt";
    }

    @Override
    public String getUsage() {
        return "session <start <name>|stop|status>";
    }

    @Override
    public boolean requiresPlayer() {
        // Session state is process scoped, not player scoped. The console and command blocks
        // may manage it, which is what lets a setup bundle open a session.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if ("start".equals(action)) {
            start(sender, args);
        } else if ("stop".equals(action)) {
            stop(sender);
        } else if ("status".equals(action)) {
            status(sender);
        } else {
            ToolkitLog.error("Unknown session action", action);
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Unknown session action: " + action + ". Expected start, stop or status."));
        }
    }

    private void start(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool session start <name>");
        }
        // Join so a multi word session name survives; the record builder quotes it if needed.
        StringBuilder name = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i++) {
            name.append(' ').append(args[i]);
        }

        String replaced = SessionManager.start(name.toString(), RecordContext.snapshot(sender));
        if (replaced != null) {
            sender.sendMessage(new TextComponentString(
                    "[DevToolkit] Replaced active session '" + replaced + "'."));
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Session started: " + name));
    }

    private void stop(ICommandSender sender) {
        String stopped = SessionManager.stop(RecordContext.snapshot(sender));
        if (stopped == null) {
            sender.sendMessage(new TextComponentString("[DevToolkit] No active session."));
            return;
        }
        sender.sendMessage(new TextComponentString("[DevToolkit] Session stopped: " + stopped));
    }

    private void status(ICommandSender sender) {
        DiagnosticSession session = ToolkitState.getActiveSession();
        if (session == null) {
            sender.sendMessage(new TextComponentString("[DevToolkit] No active session."));
            return;
        }
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] Active session: " + session.getName() + " (tick " + session.getTick() + ")"));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        return Collections.emptyList();
    }
}
