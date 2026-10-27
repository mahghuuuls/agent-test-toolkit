package com.mahghuuuls.agenttesttoolkit.inbox;

import com.mahghuuuls.agenttesttoolkit.config.ToolkitConfigLoader;
import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.InboxOpening;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;

/**
 * Reads the inbox twice a second while it is open, on the server thread.
 *
 * <p>Does nothing at all while closed: the folder is not listed, no file is touched. While
 * open, every waiting request is claimed in file-name order, parsed, and either refused with a
 * record or handed to the handler the command layer registered.
 */
public final class InboxPoller {

    /** Poll interval in server ticks. */
    public static final int INTERVAL_TICKS = 10;

    private static volatile InboxRequestHandler handler;

    /** Ids already reported as duplicates during the current opening, so each is said once. */
    private static final java.util.Set<String> reportedDuplicates = new java.util.HashSet<String>();

    private int ticks;

    /** Called once at mod initialisation by the code that implements requests. */
    public static void setHandler(InboxRequestHandler value) {
        handler = value;
    }

    /** The folder the inbox reads, beside the bundles; null before configuration loads. */
    public static File directory() {
        File config = ToolkitConfigLoader.getConfigDirectory();
        return config == null ? null : new File(config, "inbox");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        if (!InboxGate.isOpen()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        pollOnce(new InboxFiles(directory()), handler, server, context(),
                ToolkitConfigLoader.get().isInboxEnabled());
    }

    /**
     * Claims and refuses every request already waiting when the inbox opens.
     *
     * <p>A request written before the opening, in this launch or an earlier one, was not asked
     * for by anyone present now. It is answered as refused rather than run, so the agent writes
     * its requests after the inbox says it is open.
     *
     * @return the number refused
     */
    public static int refuseLeftovers(InboxFiles files, RecordContext.Snapshot context) {
        synchronized (reportedDuplicates) {
            reportedDuplicates.clear();
        }
        int refused = 0;
        for (String id : files.pendingIds()) {
            InboxFiles.Claim claim = files.claim(id);
            if (claim == null) {
                continue;
            }
            InboxRequest request = InboxRequest.parse(claim.getId(), claim.getContent());
            record(context, "claimed", request, null);
            record(context, "refused", request, "placedBeforeOpen");
            InboxResult.refused(request, "placedBeforeOpen").writeTo(files.getDirectory());
            refused++;
        }
        return refused;
    }

    /**
     * One pass over the folder. Separated from the tick so it can be tested on a temporary
     * folder without a server.
     *
     * @return the number of requests claimed
     */
    public static int pollOnce(InboxFiles files, InboxRequestHandler handler, MinecraftServer server,
                               RecordContext.Snapshot context, boolean configEnabled) {
        if (!InboxGate.isOpen()) {
            return 0;
        }
        if (!configEnabled) {
            // Both halves of the gate must hold. A reload that turned the key off closes it.
            InboxGate.close("disabled", context);
            return 0;
        }
        int claimed = 0;
        for (String id : files.pendingIds()) {
            // The gate is checked per request: a request that closes the inbox, such as one
            // whose operator has gone, must stop the rest of the batch.
            if (!InboxGate.isOpen()) {
                break;
            }
            InboxFiles.Claim claim = files.claim(id);
            if (claim == null) {
                if (files.isAlreadyClaimed(id)) {
                    reportDuplicate(context, id);
                }
                continue;
            }
            claimed++;
            InboxRequest request = InboxRequest.parse(claim.getId(), claim.getContent());
            record(context, "claimed", request, null);
            if (!request.isAccepted()) {
                record(context, "refused", request, request.getRefusal());
                InboxResult.refused(request, request.getRefusal()).writeTo(files.getDirectory());
                continue;
            }
            if (handler == null) {
                // Cannot happen once the mod has initialised; said rather than swallowed.
                record(context, "refused", request, "noHandler");
                InboxResult.refused(request, "noHandler").writeTo(files.getDirectory());
                continue;
            }
            try {
                handler.handle(server, request, context);
            } catch (RuntimeException e) {
                // Contained, so another mod's exception cannot take the server tick down, and
                // the claimed request still gets its final record.
                record(context, "failed", request, e.getClass().getSimpleName());
                InboxResult.failed(request, e.getClass().getSimpleName()).writeTo(files.getDirectory());
            }
            if ("reload".equals(request.getVerb())) {
                // A reload may have turned the configuration key off. The rest of the batch waits
                // for the next poll, which checks the key before running anything.
                break;
            }
        }
        return claimed;
    }

    /** Says once per opening that a request id is already taken and will not run. */
    private static void reportDuplicate(RecordContext.Snapshot context, String id) {
        synchronized (reportedDuplicates) {
            if (!reportedDuplicates.add(id)) {
                return;
            }
        }
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.INBOX), context);
        SessionStamp.apply(record);
        record.add("action", "refused");
        record.add("id", id);
        InboxOpening opening = InboxGate.current();
        record.add("operator", opening == null ? null : opening.getOperatorName());
        record.add("reason", "duplicateId");
        ToolkitLog.write(record);
    }

    /** Writes an {@code INBOX} record about one request. */
    public static void record(RecordContext.Snapshot context, String action, InboxRequest request,
                              String reason) {
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.INBOX), context);
        SessionStamp.apply(record);
        record.add("action", action);
        record.add("id", request.getId());
        record.add("request", request.getLine());
        InboxOpening opening = InboxGate.current();
        record.add("operator", opening == null ? null : opening.getOperatorName());
        record.add("reason", reason);
        ToolkitLog.write(record);
    }

    private static RecordContext.Snapshot context() {
        World overworld = DimensionManager.getWorld(0);
        return overworld == null ? RecordContext.Snapshot.NONE : RecordContext.snapshot(overworld);
    }
}
