package com.mahghuuuls.agenttesttoolkit.bundle;

import com.mahghuuuls.agenttesttoolkit.logging.RecordContext;

/**
 * Supplies the side and world tick to stamp on a record, read at the moment it is needed.
 *
 * <p>Live rather than a value captured once, because an execution spans ticks. A
 * {@code BUNDLE_END} record stamped with the tick the bundle <i>started</i> would be quietly
 * wrong, and wrong in the direction that matters: correlating a bundle's end against the events
 * it caused is the main reason the boundary records exist.
 *
 * <p>An interface so the state machine stays free of Minecraft types and tests can supply a
 * fixed context.
 */
public interface ContextSource {

    /** @return the current context; never null. */
    RecordContext.Snapshot snapshot();
}
