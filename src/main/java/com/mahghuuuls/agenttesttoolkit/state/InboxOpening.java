package com.mahghuuuls.agenttesttoolkit.state;

import java.util.UUID;

/**
 * The command inbox's open state: who opened it, and when.
 *
 * <p>Held in {@link ToolkitState}, which starts empty in every new process, so "closed after
 * every launch" holds by construction. It is never written to disk.
 */
public final class InboxOpening {

    private final UUID operatorUuid;
    private final String operatorName;
    private final long openedWorldTick;
    private final long openedAtMillis;

    public InboxOpening(UUID operatorUuid, String operatorName, long openedWorldTick,
                        long openedAtMillis) {
        if (operatorUuid == null) {
            throw new IllegalArgumentException("the inbox is opened by a player");
        }
        this.operatorUuid = operatorUuid;
        this.operatorName = operatorName;
        this.openedWorldTick = openedWorldTick;
        this.openedAtMillis = openedAtMillis;
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
}
