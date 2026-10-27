package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Asks a player's client to save a screenshot after a delay (REQ-187).
 *
 * <p>The target is the named player, or else the sender, which in a bundle is the player who
 * ran it and through the inbox is its operator. A client without the toolkit is never sent
 * anything: the command fails with {@code devtool.screenshot.noClient} instead.
 */
public final class ScreenshotSubCommand implements SubCommand {

    /** Failure key for a target whose client cannot receive the request. Tolerable by declaration. */
    public static final String KEY_NO_CLIENT = "devtool.screenshot.noClient";

    @Override
    public String getName() {
        return "screenshot";
    }

    @Override
    public String getDescription() {
        return "Ask a player's client to save a labelled screenshot after a delay";
    }

    @Override
    public String getUsage() {
        return "screenshot <label> [delayTicks] [player]";
    }

    @Override
    public boolean requiresPlayer() {
        // A named player is enough; checked below.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 3) {
            throw new WrongUsageException("/devtool " + getUsage());
        }
        String label = args[0];
        if (!ScreenshotRequest.isValidLabel(label)) {
            ToolkitLog.error("Screenshot label refused", label);
            throw new CommandException("[DevToolkit] A screenshot label is 1 to 48 letters, digits, '_' or '-'.");
        }
        int delay = args.length >= 2
                ? CommandBase.parseInt(args[1], 0, ScreenshotRequest.MAX_DELAY_TICKS) : 0;
        EntityPlayerMP target;
        if (args.length == 3) {
            target = CommandBase.getPlayer(server, sender, args[2]);
        } else {
            EntityPlayer self = Senders.asPlayer(sender);
            if (!(self instanceof EntityPlayerMP)) {
                throw new CommandException("[DevToolkit] screenshot needs a player: name one, or run it as a player.");
            }
            target = (EntityPlayerMP) self;
        }

        if (!ToolkitChannel.canReceive(target)) {
            ToolkitLog.error("Screenshot target has no toolkit", target.getName());
            throw new CommandException(KEY_NO_CLIENT, target.getName());
        }

        long requestTick = target.world.getTotalWorldTime();
        ScreenshotRequest request = new ScreenshotRequest(label, delay, requestTick);
        ToolkitChannel.send(request, target);

        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCREENSHOT), sender);
        SessionStamp.apply(record);
        record.add("action", "requested");
        record.add("label", label);
        record.add("delayTicks", delay);
        record.add("target", target.getName());
        record.add("requestTick", requestTick);
        record.add("file", "screenshots/" + request.fileName());
        ToolkitLog.write(record);
        sender.sendMessage(new TextComponentString("[DevToolkit] Screenshot '" + label + "' requested from "
                + target.getName() + (delay > 0 ? " in " + delay + " ticks." : ".")));
    }
}
