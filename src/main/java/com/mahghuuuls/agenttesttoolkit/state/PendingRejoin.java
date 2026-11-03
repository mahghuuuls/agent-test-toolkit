package com.mahghuuuls.agenttesttoolkit.state;

import java.util.UUID;

/**
 * A reconnect the toolkit sent a player's client on, remembered so the inbox can tell that
 * player's next logout from an ordinary leave, and recognise the client when it is back
 * (ARC-023). The token, not the UUID, proves the return: on an offline-mode server the UUID is
 * derived from the name.
 */
public final class PendingRejoin {

    private final UUID player;
    private final String token;
    private final long requestTick;
    private final long askedAtMillis;
    private final long leftAtMillis;

    /** Asked, not yet confirmed by the client: a logout now is an ordinary leave. */
    public PendingRejoin(UUID player, String token, long requestTick, long askedAtMillis) {
        this(player, token, requestTick, askedAtMillis, 0L);
    }

    private PendingRejoin(UUID player, String token, long requestTick, long askedAtMillis, long leftAtMillis) {
        this.player = player;
        this.token = token;
        this.requestTick = requestTick;
        this.askedAtMillis = askedAtMillis;
        this.leftAtMillis = leftAtMillis;
    }

    /** This reconnect, the client having said at that time that it is leaving. */
    public PendingRejoin leaving(long millis) {
        return new PendingRejoin(player, token, requestTick, askedAtMillis, millis);
    }

    /** When the reconnect was asked, wall clock. */
    public long getAskedAtMillis() {
        return askedAtMillis;
    }

    public UUID getPlayer() {
        return player;
    }

    public String getToken() {
        return token;
    }

    /** The request tick, which the client's reports carry back. */
    public long getRequestTick() {
        return requestTick;
    }

    /** When the client said it is leaving; zero while it has not. */
    public long getLeftAtMillis() {
        return leftAtMillis;
    }

    public boolean isLeaving() {
        return leftAtMillis > 0L;
    }
}
