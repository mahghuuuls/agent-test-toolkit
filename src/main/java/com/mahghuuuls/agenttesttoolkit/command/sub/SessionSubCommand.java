package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.session.SessionManager;
import com.mahghuuuls.agenttesttoolkit.state.DiagnosticSession;
import com.mahghuuuls.agenttesttoolkit.state.OperatorBinding;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Starts, stops, and reports the diagnostic session, and binds its operator.
 *
 * <p>Sessions group records; they never judge them. There is deliberately no command here
 * that accepts an expected value or reports a result.
 *
 * <p>The operator is one online player the session names as the human in the loop. Binding
 * it is what lets a bundle say {@code ${operator.name}} instead of hardcoding a profile that
 * differs between an integrated and a dedicated run.
 */
public final class SessionSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("start", "stop", "status", "operator");

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
        return "session <start <name>|stop|status|operator <player>>";
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
        } else if ("operator".equals(action)) {
            operator(server, sender, args);
        } else {
            // Thrown rather than reported quietly. Falling out of this chain would let the
            // command manager count a typo as a success, so a bundle would carry on as though
            // the session had started.
            ToolkitLog.error("Unknown session action", action);
            throw new CommandException("[DevToolkit] Unknown session action: " + action
                    + ". Expected start, stop, status or operator.");
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
        // Checkpoints belong to the session in spirit, but the session package may not
        // depend on the wait package, so the command layer forgets them here.
        com.mahghuuuls.agenttesttoolkit.wait.OperatorCheckpoints.reset();
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
        OperatorBinding operator = ToolkitState.getOperator();
        sender.sendMessage(new TextComponentString(
                "[DevToolkit] Active session: " + session.getName() + " (tick " + session.getTick()
                        + ")" + (operator == null ? ", no operator bound"
                        : ", operator " + operator.getName())));
    }

    /**
     * Binds exactly one online player as the session's operator.
     *
     * <p>Zero or several matches are refused rather than resolved. The binding exists to make
     * a bundle target the right human on any server, and guessing between two players would
     * defeat that on the one occasion it matters.
     */
    private void operator(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/devtool session operator <player>");
        }
        if (!ToolkitState.hasActiveSession()) {
            throw new CommandException(
                    "[DevToolkit] No active session. Start one before binding an operator.");
        }

        List<EntityPlayerMP> matched = onlinePlayers(server, sender, args[1]);
        if (matched.isEmpty()) {
            throw new CommandException("[DevToolkit] No online player matches " + args[1] + ".");
        }
        if (matched.size() > 1) {
            throw new CommandException("[DevToolkit] " + matched.size()
                    + " online players match " + args[1] + "; exactly one is required.");
        }

        EntityPlayerMP player = matched.get(0);
        SessionManager.bindOperator(player.getUniqueID(), player.getName(),
                RecordContext.snapshot(sender));
        sender.sendMessage(new TextComponentString("[DevToolkit] Operator bound: "
                + player.getName() + " (" + player.getUniqueID() + ")."));
    }

    /** A selector is matched by the vanilla engine, restricted to players; anything else is a name. */
    private static List<EntityPlayerMP> onlinePlayers(MinecraftServer server, ICommandSender sender,
                                                      String argument) throws CommandException {
        List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        if (argument.startsWith("@")) {
            players.addAll(EntitySelector.matchEntities(sender, argument, EntityPlayerMP.class));
            return players;
        }
        EntityPlayerMP byName = server.getPlayerList().getPlayerByUsername(argument);
        if (byName != null) {
            players.add(byName);
        }
        return players;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        if (args.length == 2 && "operator".equalsIgnoreCase(args[0])) {
            return CommandBase.getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }
}
