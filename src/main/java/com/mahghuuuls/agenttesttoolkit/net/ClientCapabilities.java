package com.mahghuuuls.agenttesttoolkit.net;

import com.mahghuuuls.agenttesttoolkit.logging.EventType;
import com.mahghuuuls.agenttesttoolkit.logging.LogRecord;
import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;
import com.mahghuuuls.agenttesttoolkit.logging.ToolkitLog;
import com.mahghuuuls.agenttesttoolkit.state.SessionStamp;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the server knows about each toolkit client: whether it said hello, and whether it lets a
 * server ask it for a lifecycle step (ARC-022, REQ-203). Server thread only for writes; the map
 * is concurrent so a status read from another thread is safe.
 *
 * <p>Forgotten at logout, and said again at every join, so a client that changes its setting
 * is believed from its next join. The server says {@link ServerHello} first, at login, to a
 * client new enough to read it.
 */
public final class ClientCapabilities {

    /** Told about every report, after its record; the inbox installs one (ARC-023). */
    public interface ReportListener {
        void onReport(EntityPlayerMP player, ClientActionReport report);
    }

    private static final Map<UUID, Boolean> ALLOW = new ConcurrentHashMap<UUID, Boolean>();
    /** The one close or respawn each player's client may answer: its step and request tick. */
    private static final Map<UUID, String> OUTSTANDING = new ConcurrentHashMap<UUID, String>();
    /**
     * The one reconnect each player's client may answer, kept apart so a later close cannot
     * replace it, and kept across the logout the reconnect causes (CYC5-006).
     */
    private static final Map<UUID, String> RECONNECTS = new ConcurrentHashMap<UUID, String>();
    /** Players asked to reconnect who have not logged out yet: no other step is sent meanwhile. */
    private static final Set<UUID> LEAVING = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    /** When each outstanding reconnect was asked, wall clock. */
    private static final Map<UUID, Long> RECONNECT_ASKED = new ConcurrentHashMap<UUID, Long>();
    /** When a player with an outstanding reconnect logged in again, wall clock. */
    private static final Map<UUID, Long> RECONNECT_RETURNED = new ConcurrentHashMap<UUID, Long>();
    /** How long after that login the reconnect's report may take before the player is not busy. */
    static final long RETURN_REPORT_GRACE_MILLIS = 10000L;
    private static volatile ReportListener listener;

    private ClientCapabilities() {
    }

    public static void setReportListener(ReportListener reportListener) {
        listener = reportListener;
    }

    /** True when this player's client said hello; null when it has not (yet). */
    public static Boolean allowsActions(EntityPlayerMP player) {
        return player == null ? null : ALLOW.get(player.getUniqueID());
    }

    /** Remembers the request a player's client may answer next. Called as it is sent. */
    public static void expect(EntityPlayerMP player, String step, long requestTick) {
        expect(player.getUniqueID(), step, requestTick);
    }

    static void expect(UUID player, String step, long requestTick) {
        if (ClientActionRequest.RECONNECT.equals(step)) {
            RECONNECTS.put(player, key(step, requestTick));
            RECONNECT_ASKED.put(player, Long.valueOf(System.currentTimeMillis()));
            LEAVING.add(player);
        } else {
            OUTSTANDING.put(player, key(step, requestTick));
        }
    }

    /**
     * True while this player's client is on a reconnect: about to leave, or back and not yet
     * reported. A step sent now would be lost in the leave, or answered in place of the
     * reconnect's own report. A reconnect that never reports, because its rejoin failed, stops
     * counting after the window a rejoin may take.
     */
    public static boolean isBusy(EntityPlayerMP player, long windowMillis) {
        return player != null && isBusy(player.getUniqueID(), System.currentTimeMillis(), windowMillis);
    }

    static boolean isBusy(UUID player, long nowMillis, long windowMillis) {
        Long asked = RECONNECT_ASKED.get(player);
        if (asked == null || nowMillis - asked.longValue() > windowMillis) {
            // Also a client that never answered at all: it does not block forever.
            return false;
        }
        Long returned = RECONNECT_RETURNED.get(player);
        if (returned != null && nowMillis - returned.longValue() > RETURN_REPORT_GRACE_MILLIS) {
            // Back without the reconnect's report: it failed, or the person came back by hand.
            return false;
        }
        return LEAVING.contains(player) || RECONNECTS.containsKey(player);
    }

    private static String key(String step, long requestTick) {
        return step + "@" + requestTick;
    }

    /**
     * Whether a report answers the request this player's client was sent. Anything else, such as
     * a report nobody asked for, is dropped without a record, so a client cannot fill the log.
     * A final result settles the request; {@code left}, the half of a reconnect sent before
     * leaving, does not.
     */
    static boolean accept(UUID player, ClientActionReport report) {
        boolean reconnect = ClientActionRequest.RECONNECT.equals(report.getStep());
        Map<UUID, String> slot = reconnect ? RECONNECTS : OUTSTANDING;
        String expected = slot.get(player);
        if (expected == null || !expected.equals(key(report.getStep(), report.getRequestTick()))) {
            return false;
        }
        if (!"left".equals(report.getResult())) {
            slot.remove(player);
            if (reconnect) {
                // Refused, or back: no leave is coming any more.
                LEAVING.remove(player);
                RECONNECT_ASKED.remove(player);
                RECONNECT_RETURNED.remove(player);
            }
        }
        return true;
    }

    /** The leave happened; an outstanding reconnect now waits for the return. */
    static void loggedOut(UUID player) {
        LEAVING.remove(player);
        RECONNECT_RETURNED.remove(player);
    }

    /** Back after a leave: an outstanding reconnect's report should follow at once. */
    static void loggedIn(UUID player, long nowMillis) {
        if (RECONNECTS.containsKey(player) && !LEAVING.contains(player)) {
            RECONNECT_RETURNED.put(player, Long.valueOf(nowMillis));
        }
    }

    static void hello(EntityPlayerMP player, String version, boolean allowActions) {
        ALLOW.put(player.getUniqueID(), Boolean.valueOf(allowActions));
    }

    static void report(EntityPlayerMP player, ClientActionReport report) {
        if (!accept(player.getUniqueID(), report)) {
            return;
        }
        LogRecord record = RecordContext.stamp(LogRecord.of(EventType.CLIENT_ACTION), player);
        SessionStamp.apply(record);
        record.add("action", "reported");
        record.add("player", player.getName());
        record.add("step", report.getStep());
        record.add("result", report.getResult());
        record.add("reason", report.getReason());
        record.add("screen", report.getScreen());
        record.add("showing", report.getShowing());
        record.add("requestTick", report.getRequestTick());
        ToolkitLog.write(record);
        ReportListener current = listener;
        if (current != null) {
            current.onReport(player, report);
        }
    }

    /**
     * Says {@link ServerHello} at login and forgets a player's answer when they leave. A
     * reconnect's outstanding request is kept: the client answers it when it is back
     * (CYC5-006). Registered on the event bus at mod start.
     */
    public static final class LogoutListener {
        @SubscribeEvent
        public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player != null) {
                loggedIn(event.player.getUniqueID(), System.currentTimeMillis());
            }
            if (event.player instanceof EntityPlayerMP && ToolkitChannel.clientSpeaksActions((EntityPlayerMP) event.player)) {
                ToolkitChannel.send(new ServerHello(com.mahghuuuls.agenttesttoolkit.Tags.VERSION),
                        (EntityPlayerMP) event.player);
            }
        }

        @SubscribeEvent
        public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player != null) {
                ALLOW.remove(event.player.getUniqueID());
                loggedOut(event.player.getUniqueID());
            }
        }
    }

    /** For tests. */
    static void resetForTesting() {
        ALLOW.clear();
        OUTSTANDING.clear();
        RECONNECTS.clear();
        RECONNECT_ASKED.clear();
        RECONNECT_RETURNED.clear();
        LEAVING.clear();
        listener = null;
    }
}
