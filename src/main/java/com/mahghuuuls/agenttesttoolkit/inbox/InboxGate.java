package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.PendingRejoin;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

import java.util.UUID;

/**
 * Whether the inbox listens, and who it listens for.
 *
 * <p>Two conditions, both required: the configuration key {@code inbox.enabled}, off by
 * default, and an operator opening it in the game since this process started. The second is
 * held only in memory. Every transition writes an {@code INBOX} record.
 */
public final class InboxGate {

    /** The configuration key named in the refusal when the inbox is disabled. */
    public static final String CONFIG_KEY = "inbox.enabled";

    /** What an open attempt did. */
    public enum OpenResult {
        OPENED,
        /** Already open for this same player; nothing changed. */
        ALREADY_OPEN,
        /** Open for another player; refused, so one person is always the caller. */
        OPEN_FOR_ANOTHER,
        /** The configuration key is false; refused. */
        DISABLED
    }

    private InboxGate() {
    }

    /** Open or suspended: an opening exists, and its status file says so. */
    public static boolean isOpen() {
        return ToolkitState.getInboxOpening() != null;
    }

    /** Open and not suspended: requests are read and run. */
    public static boolean isListening() {
        InboxOpening opening = ToolkitState.getInboxOpening();
        return opening != null && !opening.isSuspended();
    }

    /**
     * Suspends the opening while its operator is away on a reconnect the toolkit started
     * (ARC-023), until the deadline. Requests wait in the folder; nothing is read meanwhile.
     */
    public static void suspend(long deadlineMillis, RecordContext.Snapshot context) {
        InboxOpening existing = ToolkitState.getInboxOpening();
        if (existing == null || existing.isSuspended()) {
            return;
        }
        InboxOpening suspended = existing.suspendedUntil(deadlineMillis);
        ToolkitState.setInboxOpening(suspended);
        InboxStatus.write(InboxPoller.directory(), suspended, worldFolder());
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.INBOX), context);
        SessionStamp.apply(record);
        record.add("action", "suspended");
        record.add("operator", existing.getOperatorName());
        record.add("reason", "reconnect");
        record.add("resumeDeadlineMillis", deadlineMillis);
        ToolkitLog.write(record);
    }

    /** The suspended operator logged in again; their client has a moment to show its token. */
    public static void markReturned(java.util.UUID player, long nowMillis) {
        InboxOpening existing = ToolkitState.getInboxOpening();
        if (existing != null && existing.isSuspended() && existing.getOperatorUuid().equals(player)) {
            ToolkitState.setInboxOpening(existing.returnedAt(nowMillis));
        }
    }

    /**
     * Suspends the opening when this player's leave is the one their own reconnect announced
     * ({@link InboxRejoin#suspendsOnLogout}), until the configured window has passed.
     *
     * @return true when it is suspended now
     */
    public static boolean suspendIfLeaving(UUID player, long nowMillis, long windowMillis,
                                           RecordContext.Snapshot context) {
        if (!InboxRejoin.suspendsOnLogout(ToolkitState.getInboxOpening(), player, ToolkitState.getPendingRejoin(),
                nowMillis)) {
            return false;
        }
        suspend(nowMillis + windowMillis, context);
        return true;
    }

    /** The operator's client said it is leaving on that reconnect: its logout now suspends. */
    public static void onReconnectLeaving(UUID player, long requestTick, long nowMillis) {
        PendingRejoin pending = ToolkitState.getPendingRejoin();
        if (InboxRejoin.isAbout(pending, player, requestTick)) {
            ToolkitState.setPendingRejoin(pending.leaving(nowMillis));
        }
    }

    /** The operator's client refused that reconnect: no leave is coming. */
    public static void onReconnectRefused(UUID player, long requestTick) {
        if (InboxRejoin.isAbout(ToolkitState.getPendingRejoin(), player, requestTick)) {
            ToolkitState.setPendingRejoin(null);
        }
    }

    /** A returning client's report: resume on the right token, close on a wrong one. */
    public static void onReturn(java.util.UUID player, String token, RecordContext.Snapshot context) {
        InboxOpening existing = ToolkitState.getInboxOpening();
        InboxRejoin.Return decision = InboxRejoin.onReturn(existing, player, token, ToolkitState.getPendingRejoin());
        if (decision == InboxRejoin.Return.NOT_SUSPENDED) {
            return;
        }
        if (InboxRejoin.closeReason(existing, System.currentTimeMillis()) != null) {
            // Too late: the deadline passed, even if no check has closed it yet.
            checkSuspension(System.currentTimeMillis(), context);
            return;
        }
        ToolkitState.setPendingRejoin(null);
        if (decision == InboxRejoin.Return.TOKEN_MISMATCH) {
            close("tokenMismatch", context);
            return;
        }
        InboxOpening resumed = existing.resumed();
        ToolkitState.setInboxOpening(resumed);
        InboxStatus.write(InboxPoller.directory(), resumed, worldFolder());
        write(context, "resumed", existing.getOperatorName(), null);
    }

    /** Closes a suspended opening whose deadline passed, or whose operator came back without the token. */
    public static void checkSuspension(long nowMillis, RecordContext.Snapshot context) {
        String reason = InboxRejoin.closeReason(ToolkitState.getInboxOpening(), nowMillis);
        if (reason != null) {
            ToolkitState.setPendingRejoin(null);
            close(reason, context);
        }
    }

    /** @return the current opening, or null when closed. */
    public static InboxOpening current() {
        return ToolkitState.getInboxOpening();
    }

    public static OpenResult open(boolean configEnabled, UUID operator, String name,
                                  RecordContext.Snapshot context) {
        if (!configEnabled) {
            write(context, "refused", name, "disabled");
            return OpenResult.DISABLED;
        }
        InboxOpening existing = ToolkitState.getInboxOpening();
        if (existing != null) {
            if (existing.getOperatorUuid().equals(operator)) {
                return OpenResult.ALREADY_OPEN;
            }
            write(context, "refused", name, "openForAnother");
            return OpenResult.OPEN_FOR_ANOTHER;
        }
        ToolkitState.setInboxOpening(new InboxOpening(operator, name, context.getWorldTick(),
                System.currentTimeMillis(), ToolkitState.getCurrentWorldKey()));
        write(context, "opened", name, null);
        return OpenResult.OPENED;
    }

    /**
     * Opens the inbox for a local join (ARC-029), as a typed {@code devtool inbox open} would:
     * requests already waiting were placed before the opening and are refused, and the status
     * file is written. The caller has decided with {@link LocalJoin}.
     */
    public static void openAtLocalJoin(UUID operator, String name, String worldFolder,
                                       RecordContext.Snapshot context) {
        if (ToolkitState.getInboxOpening() != null) {
            return;
        }
        ToolkitState.setInboxOpening(new InboxOpening(operator, name, context.getWorldTick(),
                System.currentTimeMillis(), ToolkitState.getCurrentWorldKey()));
        write(context, "opened", name, "localJoin");
        InboxFiles files = new InboxFiles(InboxPoller.directory());
        files.ensureDirectory();
        InboxPoller.refuseLeftovers(files, context);
        InboxStatus.write(files.getDirectory(), ToolkitState.getInboxOpening(), worldFolder);
    }

    /**
     * Closes the inbox when open.
     *
     * @param reason {@code closed} when an operator closed it, {@code operatorLeft},
     *               {@code serverStopped}
     * @return true when it was open
     */
    public static boolean close(String reason, RecordContext.Snapshot context) {
        InboxOpening existing = ToolkitState.getInboxOpening();
        if (existing == null) {
            return false;
        }
        ToolkitState.setInboxOpening(null);
        InboxStatus.delete(InboxPoller.directory());
        write(context, "closed", existing.getOperatorName(), reason);
        return true;
    }

    private static String worldFolder() {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getFolderName();
    }

    /** True when this player opened the inbox now open. */
    public static boolean isOperator(UUID player) {
        InboxOpening existing = ToolkitState.getInboxOpening();
        return existing != null && existing.getOperatorUuid().equals(player);
    }

    /** Records a refused open decided by the caller, such as one attempted from a bundle. */
    public static void recordRefusal(RecordContext.Snapshot context, String name, String reason) {
        write(context, "refused", name, reason);
    }

    private static void write(RecordContext.Snapshot context, String action, String operator,
                              String reason) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.INBOX), context);
        SessionStamp.apply(record);
        record.add("action", action);
        record.add("operator", operator);
        record.add("reason", reason);
        ToolkitLog.write(record);
    }
}
