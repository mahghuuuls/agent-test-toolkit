package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.inbox.HideSet;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotReport;
import com.mahghuuuls.agenttesttoolkit.net.ScreenshotRequest;
import com.mahghuuuls.agenttesttoolkit.net.ToolkitChannel;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.wait.ClientReports;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

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
    /**
     * Failure key for a wait on a picture whose client cannot report it: one whose hello said a
     * version older than 1.10.0, or none. Refused at once rather than left to time out.
     */
    public static final String KEY_NO_REPORT = "devtool.screenshot.noReport";
    private static final String HIDE_PREFIX = "hide=";

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
        return "screenshot <label> [delayTicks] [player] [hide=chat|hud|toasts[,...]]";
    }

    @Override
    public boolean requiresPlayer() {
        // A named player is enough; checked below.
        return false;
    }

    /**
     * The refusal's arguments when the client's hello is older than what this {@code hide} needs,
     * by the version rule the client steps share; null when the picture may be asked. An older
     * client would take the picture with what it was asked to leave out, or drop a set it cannot
     * read. The message names both versions, in the one argument an older client's own language
     * file shows.
     *
     * @param helloVersion the version the client said hello with; null before any hello
     */
    static Object[] tooOld(String player, String helloVersion, String hide) {
        int[] needed = ClientRequestGate.hideVersion(hide);
        if (needed == null || (helloVersion != null && ToolkitChannel.atLeast(helloVersion, needed))) {
            return null;
        }
        return ClientRequestGate.versionArguments(player, helloVersion, needed, "this screenshot");
    }

    /**
     * The refusal's key when a picture asked of a client that said hello with this version cannot
     * be waited on, since that client never reports it; null when it can.
     */
    static String reportRefusal(String helloVersion) {
        return ToolkitChannel.readsNoticesAndSendsReports(helloVersion) ? null : KEY_NO_REPORT;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // hide= is the last word when given, so the positional words keep their places.
        String hide = ScreenshotRequest.HIDE_NONE;
        if (args.length > 1 && args[args.length - 1].startsWith(HIDE_PREFIX)) {
            hide = HideSet.canonical(args[args.length - 1].substring(HIDE_PREFIX.length()));
            if (hide == null || ScreenshotRequest.HIDE_NONE.equals(hide)) {
                throw new WrongUsageException("/devtool " + getUsage());
            }
            args = java.util.Arrays.copyOf(args, args.length - 1);
        }
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
        Object[] arguments = tooOld(target.getName(), ClientCapabilities.helloVersion(target), hide);
        if (arguments != null) {
            ToolkitLog.error("Screenshot hide needs a newer toolkit on the client", String.valueOf(arguments[0]));
            throw new CommandException(KEY_NO_CLIENT, arguments);
        }

        long requestTick = target.world.getTotalWorldTime();
        ScreenshotRequest request = new ScreenshotRequest(label, delay, requestTick, hide);
        ToolkitChannel.send(request, target);

        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCREENSHOT), sender);
        SessionStamp.apply(record);
        record.add("action", "requested");
        record.add("label", label);
        record.add("delayTicks", delay);
        record.add("target", target.getName());
        record.add("requestTick", requestTick);
        record.add("file", "screenshots/" + request.fileName());
        if (!ScreenshotRequest.HIDE_NONE.equals(hide)) {
            record.add("hide", hide);
        }
        ToolkitLog.write(record);
        BundleExecution run = Bundles.scheduler().getCurrentExecution();
        if (run != null) {
            // Kept for an await screenshot later in this run; a typed request keeps nothing.
            ClientReports.requestedPicture(run, target.getUniqueID(), label, requestTick);
        }
        Replies.send(sender, "[DevToolkit] Screenshot '" + label + "' requested from "
                + target.getName() + (delay > 0 ? " in " + delay + " ticks." : "."));
    }

    /**
     * Writes the server's record of a client's picture report and hands the report to the waits.
     * Installed as the channel's report receiver at mod start; called on the server thread with the
     * current player, only for a valid report from a 1.10.0 client or later.
     */
    public static final class ReportRecorder implements ToolkitChannel.ReportReceiver {
        @Override
        public void receive(EntityPlayerMP player, ScreenshotReport report) {
            LogRecord record = RecordContext.stamp(LogRecord.of(EventType.SCREENSHOT), player);
            SessionStamp.apply(record);
            record.add("side", "SERVER");
            record.add("action", report.isSaved() ? "saved" : "failed");
            record.add("player", player.getName());
            record.add("label", report.getLabel());
            record.add("requestTick", report.getRequestTick());
            record.add("file", report.getFile());
            record.add("frameClientTick", report.getFrameClientTick());
            record.add("reason", report.getReason());
            ToolkitLog.write(record);
            ClientReports.pictureReported(player.getUniqueID(), report.getLabel(), report.getRequestTick(),
                    report.isSaved(), report.getFile(), report.getFrameClientTick(), report.getReason());
        }
    }
}
