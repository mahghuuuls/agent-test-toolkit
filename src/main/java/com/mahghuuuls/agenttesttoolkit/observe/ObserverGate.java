package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

/**
 * The check every observer applies before doing any work.
 *
 * <p>Shared rather than duplicated per observer, so the two conditions that every category
 * depends on cannot drift apart as categories are added.
 *
 * <p>ARC-006 keeps handlers registered permanently and gates them on a boolean, so this runs
 * on every matching game event and must stay cheap: one volatile read and a set lookup.
 *
 * <p>Pure by design. A Forge event cannot be simulated meaningfully, but the decision of
 * whether to record one can be checked directly, which is where the rule's real risk lives.
 */
public final class ObserverGate {

    private ObserverGate() {
    }

    /**
     * @param isRemote whether the event's world is the client side
     * @return true when this event should produce a record
     */
    public static boolean shouldRecord(LoggingCategory category, boolean isRemote) {
        if (!ToolkitState.isEnabled(category)) {
            return false;
        }
        // REQ-041: server-authoritative observation only. In single player both logical sides
        // share a JVM, so without this one action would be recorded twice.
        return !isRemote;
    }
}
