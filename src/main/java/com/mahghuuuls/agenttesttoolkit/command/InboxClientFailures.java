package com.mahghuuuls.agenttesttoolkit.command;

import com.mahghuuuls.agenttesttoolkit.bundle.QuietScope;
import com.mahghuuuls.agenttesttoolkit.net.ClientActionReport;
import com.mahghuuuls.agenttesttoolkit.net.ClientCapabilities;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The late failure of a client step a single inbox request sent.
 *
 * <p>Such a request is answered at once and quietly, while its client reports later; a refusal
 * or failure reported then would otherwise reach no chat at all. So the step is marked as it is
 * sent, and when its final report says refused or failed, the request's one failure line is
 * said. A {@code done} report says nothing, and a step a bundle sent is never marked: the bundle's
 * own rules show its failures. A mark goes at the step's final report and at the player's leave,
 * and at most {@link #MAX_MARKS} are kept per player, for answers that never come.
 */
public final class InboxClientFailures implements ClientCapabilities.ReportListener {

    static final int MAX_MARKS = 16;

    /** Each player's marked steps, by step, request tick and token. */
    private static final Map<UUID, LinkedHashSet<String>> MARKED = new ConcurrentHashMap<UUID, LinkedHashSet<String>>();

    /**
     * Marks a step just sent, when a single inbox request is being worked on rather than a bundle.
     *
     * @param token the token the step's reports carry: empty for the lifecycle steps, whose reports
     *              carry none
     */
    public static void markIfRequest(UUID player, String step, long requestTick, String token) {
        if (player == null || !QuietScope.isRequest()) {
            return;
        }
        LinkedHashSet<String> marks = MARKED.get(player);
        if (marks == null) {
            marks = new LinkedHashSet<String>();
            MARKED.put(player, marks);
        }
        synchronized (marks) {
            marks.add(key(step, requestTick, token));
            while (marks.size() > MAX_MARKS) {
                marks.remove(marks.iterator().next());
            }
        }
    }

    /**
     * For an accepted report: the failure line to say, or null. Any final report forgets its
     * step's mark; {@code started} and {@code left} are not final and keep it.
     */
    static String lineFor(UUID player, ClientActionReport report) {
        String result = report.getResult();
        if ("started".equals(result) || "left".equals(result)) {
            return null;
        }
        LinkedHashSet<String> marks = MARKED.get(player);
        if (marks == null) {
            return null;
        }
        boolean marked;
        synchronized (marks) {
            marked = marks.remove(key(report.getStep(), report.getRequestTick(), report.getToken()));
        }
        if (!marked || !("refused".equals(result) || "failed".equals(result))) {
            return null;
        }
        return InboxDispatch.failureLine("client " + report.getStep(),
                report.getReason().isEmpty() ? result : report.getReason());
    }

    /** Forgets a player's marks. */
    static void forget(UUID player) {
        if (player != null) {
            MARKED.remove(player);
        }
    }

    /** For tests. */
    static void resetForTesting() {
        MARKED.clear();
    }

    private static String key(String step, long requestTick, String token) {
        return step + "@" + requestTick + "#" + (token == null ? "" : token);
    }

    @Override
    public void onReport(EntityPlayerMP player, ClientActionReport report) {
        String line = lineFor(player.getUniqueID(), report);
        if (line != null) {
            FailureLine.announce(player, line);
        }
    }

    /** A step asked before the leave is not answered by the client that comes back. */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null && event.player.world != null && !event.player.world.isRemote) {
            forget(event.player.getUniqueID());
        }
    }
}
