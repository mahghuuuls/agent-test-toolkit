package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
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

    public static boolean isOpen() {
        return ToolkitState.getInboxOpening() != null;
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
                System.currentTimeMillis()));
        write(context, "opened", name, null);
        return OpenResult.OPENED;
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
