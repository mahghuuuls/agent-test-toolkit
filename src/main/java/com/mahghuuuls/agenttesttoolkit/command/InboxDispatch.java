package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.BundleExecution;
import com.mahghuuuls.agenttesttoolkit.bundle.CommandOutcome;
import com.mahghuuuls.agenttesttoolkit.bundle.RunOrigin;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxGate;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxPoller;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxRequest;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxRequestHandler;
import com.mahghuuuls.agenttesttoolkit.inbox.InboxResult;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;

/**
 * Runs an accepted inbox request as the player who opened the inbox, and answers it.
 *
 * <p>The request arrives already reduced to a {@code devtool} command line by the allow-list;
 * this class never sees the agent's raw text. It runs that line through the same
 * {@link CommandRunner} chat uses, with the opener's own player object as sender, so
 * permissions are exactly that player's, and with the run channel set to {@code inbox} for the
 * span of the request, so a bundle it starts says where it came from.
 *
 * <p>Every request gets one result file. A {@code run} is answered when its bundle ends,
 * through a completion listener the run takes as it is submitted; any other verb is answered
 * in the same tick.
 */
public final class InboxDispatch implements InboxRequestHandler {

    @Override
    public void handle(MinecraftServer server, final InboxRequest request,
                       RecordContext.Snapshot pollContext) {
        final File directory = InboxPoller.directory();
        InboxOpening opening = InboxGate.current();
        final EntityPlayerMP player = opening == null || server == null || server.getPlayerList() == null
                ? null : server.getPlayerList().getPlayerByUUID(opening.getOperatorUuid());
        if (player == null) {
            // The opener is gone without a logout event reaching us; close rather than run
            // anything as nobody.
            InboxPoller.record(pollContext, "refused", request, "operatorLeft");
            InboxResult.refused(request, "operatorLeft").writeTo(directory);
            InboxGate.close("operatorLeft", pollContext);
            return;
        }
        final String line = ownedCommandLine(server, request.getCommandLine());
        if (line == null) {
            // Another mod has taken both the name and the alias. Running the line would hand
            // the agent's request to that mod's command, which the allow-list never vetted.
            InboxPoller.record(pollContext, "failed", request, "rootShadowed");
            InboxResult.failed(request, "rootShadowed").writeTo(directory);
            return;
        }

        final boolean isRun = "run".equals(request.getVerb());
        if (isRun) {
            RunCompletion.offer(new BundleExecution.CompletionListener() {
                @Override
                public void completed(BundleExecution execution, boolean discarded) {
                    answerRun(request, execution, discarded, directory);
                }
            });
        }

        final CommandRunner runner = new CommandRunner(server);
        CommandOutcome outcome;
        boolean started = false;
        try {
            outcome = RunChannel.during(RunOrigin.INBOX, request.getId(), new RunChannel.Action<CommandOutcome>() {
                @Override
                public CommandOutcome run() {
                    return runner.run(player, line);
                }
            });
        } catch (CommandException e) {
            outcome = CommandOutcome.failure(e.getMessage());
        } finally {
            // In a finally so an exception that escapes before the run is submitted, such as
            // another mod's command event handler throwing, still withdraws the offer.
            started = settleOffer(isRun);
        }

        try {
            RecordContext.Snapshot context = RecordContext.snapshot(player.world);
            boolean dispatched = started || outcome.succeeded();
            InboxPoller.record(context, dispatched ? "dispatched" : "failed", request,
                    outcome.succeeded() ? null : outcome.getDetail());
        } catch (RuntimeException e) {
            // A started run is answered by its listener; an escape here must not let the
            // poller write a second answer.
            ToolkitLog.error("Inbox record could not be written", request.getId() + ": " + e);
        }

        if (started) {
            // Answered once, by the completion listener, when the bundle ends.
            return;
        }
        if (!outcome.succeeded()) {
            InboxResult.failed(request, InboxResult.REASON_COMMAND_FAILED)
                    .with("detail", outcome.getDetail()).writeTo(directory);
        } else if (isRun) {
            // The command reported success but no run started, as when another mod cancelled
            // the command event. Said plainly rather than left unanswered.
            InboxResult.failed(request, InboxResult.REASON_RUN_NOT_STARTED)
                    .with("detail", outcome.getDetail()).writeTo(directory);
        } else {
            InboxResult.done(request).writeTo(directory);
        }
    }

    /**
     * Whether a {@code run} started, withdrawing its offer when it did not. Taken means
     * submitted: RunSubCommand takes the offer only after the run is in the scheduler, so a
     * taken offer is a run that will end and be answered by its listener. An offer still
     * pending is withdrawn so it cannot attach to an unrelated run.
     */
    static boolean settleOffer(boolean isRun) {
        boolean started = isRun && !RunCompletion.isPending();
        if (isRun && !started) {
            RunCompletion.take();
        }
        return started;
    }

    /** Writes the result of a {@code run} from the bundle's own counters. */
    static void answerRun(InboxRequest request, BundleExecution execution, boolean discarded,
                          File directory) {
        InboxResult.run(request, execution.getBundleName(), execution.getHash(),
                execution.getExecuted(), execution.getFailed(), execution.getTolerated(),
                execution.getTotal(), execution.isStoppedEarly(), execution.getDurationTicks(),
                execution.isSenderLost(), discarded, execution.getFirstFailure())
                .with("resultFile", execution.getResultFile())
                .writeTo(directory);
    }

    /**
     * The request line, addressed to a root name that is this toolkit's own command: the name
     * when it is ours, else the alias when that is ours, else null.
     */
    static String ownedCommandLine(MinecraftServer server, String devtoolLine) {
        java.util.Map<String, net.minecraft.command.ICommand> commands =
                server.getCommandManager().getCommands();
        if (commands.get(DevToolCommand.NAME) instanceof DevToolCommand) {
            return devtoolLine;
        }
        if (commands.get(DevToolCommand.ALIAS) instanceof DevToolCommand) {
            return DevToolCommand.ALIAS + devtoolLine.substring(DevToolCommand.NAME.length());
        }
        return null;
    }
}
