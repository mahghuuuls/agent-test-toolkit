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
    /** The toolkit version each client said hello with. */
    private static final Map<UUID, String> VERSION = new ConcurrentHashMap<UUID, String>();
    /** The one close or respawn each player's client may answer: its step and request tick. */
    private static final Map<UUID, String> OUTSTANDING = new ConcurrentHashMap<UUID, String>();
    /**
     * The one reconnect each player's client may answer, kept apart so a later close cannot
     * replace it, and kept across the logout the reconnect causes (CYC5-006).
     */
    private static final Map<UUID, String> RECONNECTS = new ConcurrentHashMap<UUID, String>();
    /**
     * The player actions each client may still answer, oldest first. Kept apart from the one
     * close or respawn, since several actions can be asked in quick succession and each answer
     * belongs in the log. Bounded, so a client that never answers cannot grow it.
     */
    private static final Map<UUID, java.util.LinkedHashSet<String>> ACTIONS =
            new ConcurrentHashMap<UUID, java.util.LinkedHashSet<String>>();
    /** At most this many unanswered player actions are remembered per player; older ones are forgotten. */
    static final int MAX_OUTSTANDING_ACTIONS = 16;
    /** Players asked to reconnect who have not logged out yet: no other step is sent meanwhile. */
    private static final Set<UUID> LEAVING = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    /** When each outstanding reconnect was asked, wall clock. */
    private static final Map<UUID, Long> RECONNECT_ASKED = new ConcurrentHashMap<UUID, Long>();
    /** When a player with an outstanding reconnect logged in again, wall clock. */
    private static final Map<UUID, Long> RECONNECT_RETURNED = new ConcurrentHashMap<UUID, Long>();
    /** What each outstanding reconnect's return is checked and recorded against. */
    private static final Map<UUID, ReturnExpected> RETURNS = new ConcurrentHashMap<UUID, ReturnExpected>();
    /** Each player's connection number, from the hello it said on that connection. */
    private static final Map<UUID, Integer> CONNECTION = new ConcurrentHashMap<UUID, Integer>();
    /** Counts every hello since this process started, so each connection has a number of its own. */
    private static final java.util.concurrent.atomic.AtomicInteger CONNECTIONS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** The token a returning client must show, and what the return record names. */
    static final class ReturnExpected {
        final String token;
        final String requestId;
        final boolean logout;
        final long askedAtMillis;

        ReturnExpected(String token, String requestId, boolean logout, long askedAtMillis) {
            this.token = token;
            this.requestId = requestId;
            this.logout = logout;
            this.askedAtMillis = askedAtMillis;
        }
    }

    /** How long after that login the reconnect's report may take before the player is not busy. */
    static final long RETURN_REPORT_GRACE_MILLIS = 10000L;
    private static volatile ReportListener listener;

    private ClientCapabilities() {
    }

    public static void setReportListener(ReportListener reportListener) {
        listener = reportListener;
    }

    /** The toolkit version this player's client said hello with; null before any hello. */
    public static String helloVersion(EntityPlayerMP player) {
        return player == null ? null : VERSION.get(player.getUniqueID());
    }

    /** True when this player's client said hello; null when it has not (yet). */
    public static Boolean allowsActions(EntityPlayerMP player) {
        return player == null ? null : ALLOW.get(player.getUniqueID());
    }

    /**
     * Remembers a reconnect as it is sent: the token its client will show when it is back, the
     * inbox request that asked for it, if any, and whether it logs the player out.
     */
    public static void expectReconnect(EntityPlayerMP player, long requestTick, String token, String requestId,
                                       boolean logout) {
        expect(player.getUniqueID(), ClientActionRequest.RECONNECT, requestTick);
        RETURNS.put(player.getUniqueID(), new ReturnExpected(token, requestId, logout, System.currentTimeMillis()));
    }

    /** Remembers the request a player's client may answer next. Called as it is sent. */
    public static void expect(EntityPlayerMP player, String step, long requestTick) {
        expect(player.getUniqueID(), step, requestTick);
    }

    /**
     * Remembers a player action as it is sent. Its token tells its answer from another of the
     * same step asked in the same tick.
     */
    public static void expectAction(EntityPlayerMP player, String step, long requestTick, String token) {
        expectAction(player.getUniqueID(), step, requestTick, token);
    }

    static void expectAction(UUID player, String step, long requestTick, String token) {
        java.util.LinkedHashSet<String> waiting = ACTIONS.get(player);
        if (waiting == null) {
            waiting = new java.util.LinkedHashSet<String>();
            ACTIONS.put(player, waiting);
        }
        waiting.add(key(step, requestTick) + "#" + token);
        while (waiting.size() > MAX_OUTSTANDING_ACTIONS) {
            waiting.remove(waiting.iterator().next());
        }
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
        if (com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions.isStep(report.getStep())) {
            java.util.LinkedHashSet<String> waiting = ACTIONS.get(player);
            if (waiting == null || report.getToken().isEmpty()) {
                return false;
            }
            String expected = key(report.getStep(), report.getRequestTick()) + "#" + report.getToken();
            // A hold answers twice: when the input goes down, and when it is released.
            boolean started = "started".equals(report.getResult());
            if (started && !com.mahghuuuls.agenttesttoolkit.inbox.PlayerActionOptions.HOLD.equals(report.getStep())) {
                return false;
            }
            return started ? waiting.contains(expected) : waiting.remove(expected);
        }
        boolean reconnect = ClientActionRequest.RECONNECT.equals(report.getStep());
        Map<UUID, String> slot = reconnect ? RECONNECTS : OUTSTANDING;
        String expected = slot.get(player);
        if (expected == null || !expected.equals(key(report.getStep(), report.getRequestTick()))) {
            return false;
        }
        if (!"left".equals(report.getResult())) {
            slot.remove(player);
            if (reconnect && !"done".equals(report.getResult())) {
                // Refused or failed: no return is coming.
                RETURNS.remove(player);
            }
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

    /**
     * Whether this player's client said hello with a toolkit at least this version. A step a
     * client cannot read is never sent: it would drop it, and no report would come back.
     */
    public static boolean saidHelloAtLeast(EntityPlayerMP player, int[] minimum) {
        String version = player == null ? null : VERSION.get(player.getUniqueID());
        return version != null && ToolkitChannel.atLeast(version, minimum);
    }

    static void hello(EntityPlayerMP player, String version, boolean allowActions) {
        CONNECTION.put(player.getUniqueID(), Integer.valueOf(CONNECTIONS.incrementAndGet()));
        VERSION.put(player.getUniqueID(), version);
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
        for (Map.Entry<String, String> field : report.getFields().entrySet()) {
            record.add(field.getKey(), field.getValue());
        }
        ToolkitLog.write(record);
        if (ClientActionRequest.RECONNECT.equals(report.getStep()) && "done".equals(report.getResult())) {
            // Before the listener: a gate that waits for the return reads this one record, and
            // it must come before the inbox says it resumed.
            recordReturn(player, report);
        }
        ReportListener current = listener;
        if (current != null) {
            current.onReport(player, report);
        }
    }

    /**
     * One record when a reconnect's client is back, carrying everything a test needs to tie the
     * return to its request: the player, the request, the time away, the logout, the connection.
     * Only for the token the reconnect was sent with; a client showing another writes none.
     */
    static void recordReturn(EntityPlayerMP player, ClientActionReport report) {
        java.net.SocketAddress remote = player.connection == null || player.connection.getNetworkManager() == null
                ? null : player.connection.getNetworkManager().getRemoteAddress();
        LogRecord base = RecordContext.stamp(LogRecord.of(EventType.CLIENT_ACTION), player);
        SessionStamp.apply(base);
        LogRecord record = returnRecord(base, RETURNS.remove(player.getUniqueID()), report, player.getName(),
                player.getUniqueID(), remote == null ? "none" : remote.toString(),
                CONNECTION.get(player.getUniqueID()), System.currentTimeMillis());
        if (record != null) {
            ToolkitLog.write(record);
        }
    }

    /**
     * The return record from plain values, or null when the report is not the return of the
     * reconnect expected. {@code awayMs} is the client's own measure, from its leave to its
     * return; a client too old to report it gets the server's, from the request to the return.
     */
    static LogRecord returnRecord(LogRecord record, ReturnExpected expected, ClientActionReport report, String name,
                                  UUID uuid, String remote, Integer connection, long nowMillis) {
        if (!isReturnOf(expected, report.getToken())) {
            return null;
        }
        record.add("action", "returned");
        record.add("player", name);
        record.add("uuid", uuid.toString());
        record.add("requestId", expected.requestId);
        String away = report.getFields().get("awayMs");
        record.add("awayMs", away != null ? away : String.valueOf(nowMillis - expected.askedAtMillis));
        record.add("logout", expected.logout);
        record.add("remote", remote);
        if (connection != null) {
            record.add("connection", connection.intValue());
        }
        record.add("requestTick", report.getRequestTick());
        return record;
    }

    /** The return of an expected reconnect: the same token, which only that client was given. */
    static boolean isReturnOf(ReturnExpected expected, String token) {
        return expected != null && expected.token != null && !expected.token.isEmpty()
                && expected.token.equals(token);
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
                VERSION.remove(event.player.getUniqueID());
                // An action asked before the leave is not answered by the client that comes back.
                ACTIONS.remove(event.player.getUniqueID());
                loggedOut(event.player.getUniqueID());
            }
        }
    }

    /** For tests. */
    static void resetForTesting() {
        ALLOW.clear();
        VERSION.clear();
        OUTSTANDING.clear();
        ACTIONS.clear();
        RETURNS.clear();
        CONNECTION.clear();
        RECONNECTS.clear();
        RECONNECT_ASKED.clear();
        RECONNECT_RETURNED.clear();
        LEAVING.clear();
        listener = null;
    }
}
