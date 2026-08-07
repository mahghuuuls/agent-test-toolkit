package com.mahghuuuls.agenttesttoolkit.observe;

import com.mahghuuuls.agenttesttoolkit.logging.LoggingCategory;
import com.mahghuuuls.agenttesttoolkit.state.ToolkitState;

/**
 * The check every observer applies before doing any work.
 *
 * <p>Shared rather than duplicated per observer, so the two conditions that every category
 * depends on cannot drift apart as categories are added.
 *
 * <p>Handlers stay registered permanently and are gated on a boolean, so this runs on every
 * matching game event and must stay cheap: one volatile read and a set lookup.
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
        // Server-authoritative observation only. In single player both logical sides
        // share a JVM, so without this one action would be recorded twice.
        return !isRemote;
    }

    /**
     * The position-aware form, which also applies the category's filter.
     *
     * <p>Evaluated before the record is built, so an excluded event costs a coordinate
     * comparison rather than the string work of assembling a record that is then discarded.
     *
     * <p>Every observer that knows where its event happened should use this one. The
     * position-free form above remains for events that genuinely have no location; using it
     * where a position exists would silently ignore the operator's filter, and the only symptom
     * would be a log larger than the one they asked for.
     */
    public static boolean shouldRecord(LoggingCategory category, boolean isRemote,
                                       int dimension, double x, double y, double z) {
        if (isRemote) {
            return false;
        }
        return ToolkitState.shouldRecord(category, dimension, x, y, z);
    }
}
