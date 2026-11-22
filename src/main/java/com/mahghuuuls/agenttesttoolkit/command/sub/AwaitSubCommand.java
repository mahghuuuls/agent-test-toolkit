package com.mahghuuuls.agenttesttoolkit.command.sub;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.BundleRecorder;
import com.mahghuuuls.agenttesttoolkit.bundle.Bundles;
import com.mahghuuuls.agenttesttoolkit.bundle.Replies;
import com.mahghuuuls.agenttesttoolkit.bundle.WaitCondition;
import com.mahghuuuls.agenttesttoolkit.command.Senders;
import com.mahghuuuls.agenttesttoolkit.command.SubCommand;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import com.mahghuuuls.agenttesttoolkit.wait.ChunkReadiness;
import com.mahghuuuls.agenttesttoolkit.wait.ChunkPopulation;
import com.mahghuuuls.agenttesttoolkit.wait.ChunkRegion;
import com.mahghuuuls.agenttesttoolkit.wait.ScreenshotWait;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Makes a bundle wait for something, with a deadline.
 *
 * <p>Inside a bundle the command registers a condition with the execution that is dispatching
 * it, exactly as {@code devtool run} registers a nested bundle, and returns at once; the
 * scheduler polls from then on. Typed by the operator there is no execution to park, so the
 * command checks once, reports, and never blocks the server thread. The record shape is the
 * same in both cases, with the bundle fields simply absent in the second.
 *
 * <p>The first check happens here, before parking. A region that is already loaded is
 * satisfied on the same tick with nothing waited, which is what a bundle running in a
 * prepared arena sees almost every time.
 */
public final class AwaitSubCommand implements SubCommand {

    private static final List<String> CONDITIONS = Arrays.asList("chunks", "screenshot");

    /** Ten seconds. Long enough for chunks near a teleport target, short enough to notice. */
    public static final int DEFAULT_TIMEOUT_TICKS = 200;
    /** Ten minutes. A wait longer than this is a hung bundle, not a slow one. */
    public static final int MAX_TIMEOUT_TICKS = 12000;

    @Override
    public String getName() {
        return "await";
    }

    @Override
    public String getDescription() {
        return "Wait, with a timeout, until a region of chunks is loaded or a requested screenshot is saved";
    }

    @Override
    public String getUsage() {
        return "await chunks <x1> <z1> <x2> <z2> [timeoutTicks] [populated] | await screenshot <label> [timeoutTicks]";
    }

    @Override
    public boolean requiresPlayer() {
        // The console can wait too; its dimension is the overworld and cannot change.
        return false;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length >= 1 && ScreenshotWait.KIND.equals(args[0].toLowerCase(Locale.ROOT))) {
            awaitScreenshot(server, sender, args);
            return;
        }
        // populated is the last word when given: loaded is not decorated, and a test that
        // clears an area needs the generator to have finished writing into it.
        boolean populated = args.length > 5 && POPULATED.equals(args[args.length - 1].toLowerCase(Locale.ROOT));
        if (populated) {
            args = java.util.Arrays.copyOf(args, args.length - 1);
        }
        if (args.length < 5 || !"chunks".equals(args[0].toLowerCase(Locale.ROOT))) {
            throw new WrongUsageException("/devtool " + getUsage());
        }

        // Tilde-relative like every vanilla coordinate argument, so a bundle can name a region
        // around wherever the caller stands.
        double baseX = sender.getPosition().getX();
        double baseZ = sender.getPosition().getZ();
        int x1 = floor(CommandBase.parseCoordinate(baseX, args[1], false).getResult());
        int z1 = floor(CommandBase.parseCoordinate(baseZ, args[2], false).getResult());
        int x2 = floor(CommandBase.parseCoordinate(baseX, args[3], false).getResult());
        int z2 = floor(CommandBase.parseCoordinate(baseZ, args[4], false).getResult());
        int timeout = args.length > 5
                ? CommandBase.parseInt(args[5], 1, MAX_TIMEOUT_TICKS) : DEFAULT_TIMEOUT_TICKS;

        ChunkRegion region = ChunkRegion.ofBlocks(x1, z1, x2, z2);
        if (!region.isWithinCap()) {
            // Refused before any waiting, so an oversized region is a usage mistake the
            // author sees at once rather than a slow server they notice later.
            throw new CommandException("Region covers " + region.chunkCount()
                    + " chunks; the most a barrier may watch is " + ChunkRegion.MAX_CHUNKS + ".");
        }

        EntityPlayerMP player = Senders.asServerPlayer(sender);
        int dimension = sender.getEntityWorld().provider.getDimension();
        ChunkReadiness condition = new ChunkReadiness(server, region, dimension,
                player == null ? null : player.getUniqueID(), populated);

        String command = "devtool " + getName() + " " + String.join(" ", args);
        BundleExecution execution = Bundles.scheduler().getCurrentExecution();
        RecordContext.Snapshot context = RecordContext.snapshot(sender);

        WaitCondition.Poll first = condition.poll(0);
        if (first.getStatus() == WaitCondition.Status.SATISFIED) {
            BundleRecorder.recordAwait(context,
                    execution == null ? null : execution.getBundleName(),
                    execution == null ? null : execution.getHash(),
                    execution == null ? -1 : execution.getLastDispatchedIndex(),
                    command, condition, "satisfied", null, 0, timeout);
            Replies.send(sender, "[DevToolkit] All "
                    + region.chunkCount() + " chunks in dimension " + dimension
                    + (populated ? " are loaded and decorated." : " are loaded."));
            return;
        }

        if (execution == null) {
            // One check, one report. Nothing to park, and blocking here would stall the
            // very server the chunks are supposed to load on.
            BundleRecorder.recordAwait(context, null, null, -1, command, condition,
                    "pending", null, 0, timeout);
            Replies.send(sender, "[DevToolkit] Not ready: "
                    + describeShortfall(condition, region)
                    + ". Outside a bundle this command checks once and does not wait.");
            return;
        }

        execution.await(condition, timeout);
        Replies.send(sender, "[DevToolkit] Waiting up to " + timeout
                + " ticks for " + region.chunkCount() + " chunks in dimension " + dimension
                + "; " + describeShortfall(condition, region) + ".");
    }

    /**
     * {@code await screenshot <label> [timeoutTicks]}: inside a bundle, parks it until the client
     * reports the latest picture this run asked under that label. Refused at once when the run
     * asked none, and with {@code devtool.screenshot.noReport} when the picture's client is too old
     * to report it, rather than left to time out.
     */
    private void awaitScreenshot(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length < 2 || args.length > 3) {
            throw new WrongUsageException("/devtool await screenshot <label> [timeoutTicks]");
        }
        String label = args[1];
        int timeout = args.length > 2
                ? CommandBase.parseInt(args[2], 1, MAX_TIMEOUT_TICKS) : DEFAULT_TIMEOUT_TICKS;
        BundleExecution execution = Bundles.scheduler().getCurrentExecution();
        if (execution == null) {
            // A picture is waited on only by the run that asked it; typed, there is none, and a typed
            // command must not hold the server. The same key as every other client wait typed outside one.
            ToolkitLog.error("A screenshot wait needs a bundle", sender.getName());
            throw new CommandException(ClientSubCommand.KEY_WAIT_OUTSIDE_BUNDLE, sender.getName());
        }
        ScreenshotWait condition = ScreenshotWait.forLatest(execution, label);
        if (condition == null) {
            ToolkitLog.error("No screenshot to await", label);
            throw new CommandException("[DevToolkit] No screenshot labelled " + label
                    + " was requested in this bundle, so there is none to await.");
        }
        EntityPlayerMP target = server.getPlayerList().getPlayerByUUID(condition.getPlayer());
        // A player who has left is not judged by version: the logout already marked the picture as
        // never to be reported, so the wait below is cancelled on its first poll, as any wait is.
        String helloVersion = target == null ? null : ClientCapabilities.helloVersion(target);
        String refusal = target == null ? null : ScreenshotSubCommand.reportRefusal(helloVersion);
        if (refusal != null) {
            String who = target.getName();
            ToolkitLog.error("Screenshot cannot be awaited, the client does not report pictures",
                    who + " (client toolkit " + (helloVersion == null ? "none" : helloVersion) + ")");
            throw new CommandException(refusal, who + " (client toolkit " + (helloVersion == null ? "none" : helloVersion)
                    + ", reporting a picture needs 1.10.0)");
        }
        execution.await(condition, timeout);
        Replies.send(sender, "[DevToolkit] Waiting up to " + timeout + " ticks for screenshot '" + label
                + "' to be saved.");
    }

    private static String describeShortfall(ChunkReadiness condition, ChunkRegion region) {
        String loaded = condition.getLoadedCount() + " of " + region.chunkCount() + " chunks in "
                + region.bounds() + " are loaded";
        if (condition.getFirstUnloaded() != null) {
            return loaded + ", first missing " + condition.getFirstUnloaded();
        }
        ChunkPopulation.Result population = condition.getPopulation();
        return population == null ? loaded : loaded + "; " + population.getDecoratedCount() + " of "
                + population.getCheckedCount() + " decorated, first not yet " + population.getFirstNotDecorated();
    }

    private static final String POPULATED = "populated";

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args) {
        if (args.length <= 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, CONDITIONS);
        }
        return Collections.emptyList();
    }
}
