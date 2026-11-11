package com.mahghuuuls.agenttesttoolkit.state;

import java.util.UUID;

/**
 * The command inbox's open state: who opened it, when, and in which world.
 *
 * <p>Held in {@link ToolkitState}, which starts empty in every new process, so "closed after
 * every launch" holds by construction. It is never written to disk.
 *
 * <p>Since 1.4.0 an opening can be <b>suspended</b> while its operator is away on a reconnect
 * the toolkit itself started (ARC-023): {@link #getSuspendedUntilMillis} is then the wall-clock
 * deadline for the return, and zero while the inbox listens. Immutable; a change is a new value.
 */
public final class InboxOpening {

    private final UUID operatorUuid;
    private final String operatorName;
    private final long openedWorldTick;
    private final long openedAtMillis;
    private final String worldKey;
    private final long suspendedUntilMillis;
    private final long returnedAtMillis;

    public InboxOpening(UUID operatorUuid, String operatorName, long openedWorldTick,
                        long openedAtMillis) {
        this(operatorUuid, operatorName, openedWorldTick, openedAtMillis, null, 0L, 0L);
    }

    public InboxOpening(UUID operatorUuid, String operatorName, long openedWorldTick,
                        long openedAtMillis, String worldKey) {
        this(operatorUuid, operatorName, openedWorldTick, openedAtMillis, worldKey, 0L, 0L);
    }

    private InboxOpening(UUID operatorUuid, String operatorName, long openedWorldTick,
                         long openedAtMillis, String worldKey, long suspendedUntilMillis,
                         long returnedAtMillis) {
        if (operatorUuid == null) {
            throw new IllegalArgumentException("the inbox is opened by a player");
        }
        this.operatorUuid = operatorUuid;
        this.operatorName = operatorName;
        this.openedWorldTick = openedWorldTick;
        this.openedAtMillis = openedAtMillis;
        this.worldKey = worldKey;
        this.suspendedUntilMillis = suspendedUntilMillis;
        this.returnedAtMillis = returnedAtMillis;
    }

    /** This opening, suspended until the given wall-clock time. */
    public InboxOpening suspendedUntil(long deadlineMillis) {
        return new InboxOpening(operatorUuid, operatorName, openedWorldTick, openedAtMillis, worldKey,
                deadlineMillis, 0L);
    }

    /**
     * This opening under the name its operator has now: the same player can come back from a
     * reconnect with another casing of the name.
     */
    public InboxOpening renamed(String name) {
        return new InboxOpening(operatorUuid, name, openedWorldTick, openedAtMillis, worldKey,
                suspendedUntilMillis, returnedAtMillis);
    }

    /** This opening, listening again. */
    public InboxOpening resumed() {
        return new InboxOpening(operatorUuid, operatorName, openedWorldTick, openedAtMillis, worldKey, 0L, 0L);
    }

    /** This suspended opening, its operator logged in again at that time, the token not seen yet. */
    public InboxOpening returnedAt(long millis) {
        return new InboxOpening(operatorUuid, operatorName, openedWorldTick, openedAtMillis, worldKey,
                suspendedUntilMillis, millis);
    }

    public UUID getOperatorUuid() {
        return operatorUuid;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public long getOpenedWorldTick() {
        return openedWorldTick;
    }

    public long getOpenedAtMillis() {
        return openedAtMillis;
    }

    /** The world it was opened in, as {@code WorldIdentity.key()} text, or null when unknown. */
    public String getWorldKey() {
        return worldKey;
    }

    public boolean isSuspended() {
        return suspendedUntilMillis > 0L;
    }

    public long getSuspendedUntilMillis() {
        return suspendedUntilMillis;
    }

    /** When the suspended operator logged in again; zero when not yet. */
    public long getReturnedAtMillis() {
        return returnedAtMillis;
    }
}
