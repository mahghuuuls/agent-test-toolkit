package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxFiles;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxPoller;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Opens, closes and reports the command inbox.
 *
 * <p>Opening needs two things: the configuration key, off by default, and a player typing it
 * after the current launch. The player who opens it is the caller of every request. Closing
 * and status work from anywhere, the console included.
 */
public final class InboxSubCommand implements SubCommand {

    private static final List<String> ACTIONS = Arrays.asList("open", "close", "status");

    @Override
    public String getName() {
        return "inbox";
    }

    @Override
    public String getDescription() {
        return "Open, close or report the command inbox the agent writes requests into";
    }

    @Override
    public String getUsage() {
        return "inbox <open|close|status>";
    }

    @Override
    public boolean requiresPlayer() {
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if ("open".equals(action)) {
            open(server, sender);
        } else if ("close".equals(action)) {
            if (InboxGate.close("closed", RecordContext.snapshot(sender))) {
                sender.sendMessage(new TextComponentString("[DevToolkit] Inbox closed."));
            } else {
                sender.sendMessage(new TextComponentString("[DevToolkit] Inbox was not open."));
            }
        } else if ("status".equals(action)) {
            InboxOpening opening = InboxGate.current();
            sender.sendMessage(new TextComponentString(opening == null
                    ? "[DevToolkit] Inbox closed."
                    : "[DevToolkit] Inbox open for " + opening.getOperatorName() + "."));
        } else {
            throw new WrongUsageException("/devtool " + getUsage());
        }
    }

    private void open(MinecraftServer server, ICommandSender sender) throws CommandException {
        EntityPlayerMP player = Senders.asServerPlayer(sender);
        if (player == null || !(sender instanceof EntityPlayerMP)) {
            // The opener is the caller of every request, so it must be a player typing it
            // themselves, not a console, a command block, or a wrapped sender.
            throw new CommandException("[DevToolkit] The inbox is opened by a player, in chat.");
        }
        if (com.mahghuuuls.agenttesttoolkit.bundle.Bundles.scheduler().getCurrentExecution() != null) {
            // A bundle runs with the player's own object as sender, so the type check above
            // cannot tell a bundle from the person. Bundle commands are only ever dispatched
            // while the scheduler advances them, so a running execution means a bundle, or join
            // automation with nobody typing, is asking. The inbox opens only when a person types.
            InboxGate.recordRefusal(RecordContext.snapshot(sender), player.getName(), "fromBundle");
            throw new CommandException("[DevToolkit] The inbox is opened by typing the command in "
                    + "chat, never from a bundle.");
        }
        InboxGate.OpenResult result = InboxGate.open(
                ToolkitConfigLoader.get().isInboxEnabled(), player.getUniqueID(), player.getName(),
                RecordContext.snapshot(sender));
        switch (result) {
            case DISABLED:
                throw new CommandException("[DevToolkit] The inbox is disabled. In "
                        + "config/devtool/devtool.cfg set B:enabled=true under inbox, then reload.");
            case OPEN_FOR_ANOTHER:
                throw new CommandException("[DevToolkit] The inbox is already open for "
                        + InboxGate.current().getOperatorName() + ".");
            case ALREADY_OPEN:
                sender.sendMessage(new TextComponentString("[DevToolkit] Inbox already open for you."));
                return;
            default:
                InboxFiles files = new InboxFiles(InboxPoller.directory());
                files.ensureDirectory();
                int leftovers = InboxPoller.refuseLeftovers(files, RecordContext.snapshot(sender));
                com.mahghuuuls.agenttesttoolkit.inbox.InboxStatus.write(files.getDirectory(),
                        InboxGate.current(), server == null ? null : server.getFolderName());
                if (leftovers > 0) {
                    sender.sendMessage(new TextComponentString("[DevToolkit] " + leftovers
                            + " request(s) written before opening were refused, not run."));
                }
                sender.sendMessage(new TextComponentString("[DevToolkit] Inbox open. Requests run as "
                        + player.getName() + " until you close it, leave, or the game restarts."));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, ACTIONS);
        }
        return Collections.emptyList();
    }
}
